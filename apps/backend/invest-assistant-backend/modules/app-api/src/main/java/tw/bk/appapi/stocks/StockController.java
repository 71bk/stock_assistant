package tw.bk.appapi.stocks;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import tw.bk.appapi.stocks.vo.*;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.model.MarketCode;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.model.TickerList;
import tw.bk.appstocks.model.TickerQuery;
import tw.bk.appstocks.service.InstrumentService;
import tw.bk.appstocks.service.StockQuoteService;
import tw.bk.appstocks.service.StockTickerService;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stock API Controller（符合 API 合約規範）
 */
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
@Tag(name = "Stocks", description = "股票行情與商品主檔")
public class StockController {

    private static final Set<String> ALLOWED_TICKER_TYPES = Set.of("EQUITY", "INDEX", "WARRANT", "ODDLOT");

    private final InstrumentService instrumentService;
    private final StockQuoteService stockQuoteService;
    private final StockTickerService stockTickerService;

    /**
     * GET /api/stocks/markets - 市場列表
     */
    @GetMapping("/markets")
    @Operation(summary = "取得市場列表", description = "取得支援的市場列表（TW/US）")
    public Result<List<MarketResponse>> getMarkets() {
        List<MarketResponse> markets = List.of(
                MarketResponse.of("US", "美股"),
                MarketResponse.of("TW", "台股"));
        return Result.ok(markets);
    }

    /**
     * GET /api/stocks/exchanges?market=TW - 交易所列表
     */
    @GetMapping("/exchanges")
    @Operation(summary = "取得交易所列表", description = "取得交易所列表，可依市場篩選")
    public Result<List<ExchangeResponse>> getExchanges(
            @RequestParam(required = false) String market) {

        List<ExchangeResponse> exchanges = List.of(
                ExchangeResponse.of("XNAS", "NASDAQ", "US"),
                ExchangeResponse.of("XNYS", "New York Stock Exchange", "US"),
                ExchangeResponse.of("XTAI", "台灣證券交易所", "TW"),
                ExchangeResponse.of("ROCO", "台灣櫃買中心", "TW"));

        if (market != null && !market.isBlank()) {
            String normalizedMarket = market.trim().toUpperCase();
            exchanges = exchanges.stream()
                    .filter(e -> normalizedMarket.equals(e.getMarket()))
                    .collect(Collectors.toList());
        }

        return Result.ok(exchanges);
    }

    /**
     * GET /api/stocks/instruments?q=AAPL&page=1&size=20 - 搜尋商品（分頁）
     */
    @GetMapping("/instruments")
    @Operation(summary = "搜尋商品", description = "根據 ticker 或名稱搜尋商品，支援分頁")
    public Result<PageResponse<InstrumentResponse>> searchInstruments(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        int validLimit = Math.min(Math.max(size, 1), 50);
        List<InstrumentEntity> entities = instrumentService.searchInstruments(q, validLimit);
        List<InstrumentResponse> items = entities.stream()
                .map(InstrumentResponse::from)
                .collect(Collectors.toList());

        PageResponse<InstrumentResponse> pageResponse = PageResponse.ok(
                items,
                page,
                size,
                items.size());

        return Result.ok(pageResponse);
    }

    /**
     * GET /api/stocks/instruments/{instrumentId} - 取得單一商品
     */
    @GetMapping("/instruments/{instrumentId}")
    @Operation(summary = "取得商品詳情", description = "根據 instrument_id 查詢商品詳細資訊")
    public Result<InstrumentResponse> getInstrument(@PathVariable String instrumentId) {
        // 支援數字 ID 或 symbol_key
        InstrumentEntity entity;

        if (instrumentId.contains(":")) {
            // 是 symbol_key
            validateSymbolKey(instrumentId);
            entity = instrumentService.findBySymbolKey(instrumentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在: " + instrumentId));
        } else {
            // 是數字 ID
            Long id = parseInstrumentId(instrumentId);
            entity = instrumentService.findByIdWithRelations(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在: " + instrumentId));
        }

        return Result.ok(InstrumentResponse.from(entity));
    }

    /**
     * GET /api/stocks/quote?symbol_key=US:XNAS:AAPL - 即時報價
     */
    @GetMapping("/quote")
    @Operation(summary = "取得即時報價", description = "查詢股票即時報價，支援 instrument_id 或 symbol_key（擇一）")
    public Result<QuoteResponse> getQuote(
            @RequestParam(name = "instrument_id", required = false) String instrumentId,
            @RequestParam(name = "symbol_key", required = false) String symbolKey) {

        // Normalize 參數（去除空白、null 視為空）
        String normalizedInstrumentId = normalizeParam(instrumentId);
        String normalizedSymbolKey = normalizeParam(symbolKey);

        // 驗證：必須提供其中之一，但不可兩者皆提供
        if (normalizedInstrumentId == null && normalizedSymbolKey == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "必須提供 instrument_id 或 symbol_key 其中之一");
        }
        if (normalizedInstrumentId != null && normalizedSymbolKey != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "不可同時提供 instrument_id 與 symbol_key，請擇一使用");
        }

        String actualSymbolKey;
        String actualInstrumentId;

        if (normalizedSymbolKey != null) {
            // 使用 symbol_key 查詢
            validateSymbolKey(normalizedSymbolKey);
            actualSymbolKey = normalizedSymbolKey;

            // 取得 instrument_id（如DB有的話）
            InstrumentEntity entity = instrumentService.findBySymbolKey(normalizedSymbolKey)
                    .orElse(null);
            actualInstrumentId = entity != null && entity.getId() != null
                    ? entity.getId().toString()
                    : null;
        } else {
            // 使用 instrument_id 查詢
            Long id = parseInstrumentId(normalizedInstrumentId);
            InstrumentEntity entity = instrumentService.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "商品不存在: " + normalizedInstrumentId));

            actualSymbolKey = entity.getSymbolKey();
            actualInstrumentId = entity.getId().toString();

            // 驗證從 DB 取得的 symbolKey 格式
            validateSymbolKey(actualSymbolKey);
        }

        Quote quote = stockQuoteService.getQuote(actualSymbolKey);
        return Result.ok(QuoteResponse.from(actualInstrumentId, actualSymbolKey, quote));
    }

    /**
     * GET
     * /api/stocks/candles?symbol_key=...&interval=1d&from=2026-01-01&to=2026-01-09
     */
    @GetMapping("/candles")
    @Operation(summary = "取得K線資料", description = "查詢歷史 K 線資料（OHLC），支援 instrument_id 或 symbol_key（擇一）")
    public Result<List<CandleResponse>> getCandles(
            @RequestParam(name = "instrument_id", required = false) String instrumentId,
            @RequestParam(name = "symbol_key", required = false) String symbolKey,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        // Normalize 參數
        String normalizedInstrumentId = normalizeParam(instrumentId);
        String normalizedSymbolKey = normalizeParam(symbolKey);

        // 驗證：必須提供其中之一，但不可兩者皆提供
        if (normalizedInstrumentId == null && normalizedSymbolKey == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "必須提供 instrument_id 或 symbol_key 其中之一");
        }
        if (normalizedInstrumentId != null && normalizedSymbolKey != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "不可同時提供 instrument_id 與 symbol_key，請擇一使用");
        }

        String actualSymbolKey;

        if (normalizedSymbolKey != null) {
            // 使用 symbol_key 查詢
            validateSymbolKey(normalizedSymbolKey);
            actualSymbolKey = normalizedSymbolKey;
        } else {
            // 使用 instrument_id 查詢
            Long id = parseInstrumentId(normalizedInstrumentId);
            InstrumentEntity entity = instrumentService.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "商品不存在: " + normalizedInstrumentId));

            actualSymbolKey = entity.getSymbolKey();

            // 驗證從 DB 取得的 symbolKey 格式
            validateSymbolKey(actualSymbolKey);
        }

        List<Candle> candles = stockQuoteService.getCandles(actualSymbolKey, interval, from, to);
        return Result.ok(CandleResponse.fromList(candles));
    }

    /**
     * GET /api/stocks/tickers - 取得股票或指數列表（台股專用）
     */
    @GetMapping("/tickers")
    @Operation(summary = "取得股票或指數列表", description = "依條件查詢股票/指數清單（台股）")
    public Result<TickerListResponse> getTickers(
            @RequestParam String type,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Boolean isNormal,
            @RequestParam(required = false) Boolean isAttention,
            @RequestParam(required = false) Boolean isDisposition,
            @RequestParam(required = false) Boolean isHalted) {

        String normalizedType = normalizeTickerType(type);
        TickerQuery query = TickerQuery.builder()
                .type(normalizedType)
                .exchange(exchange)
                .market(market)
                .industry(industry)
                .isNormal(isNormal)
                .isAttention(isAttention)
                .isDisposition(isDisposition)
                .isHalted(isHalted)
                .build();

        TickerList list = stockTickerService.getTickers(MarketCode.TW, query);
        return Result.ok(TickerListResponse.from(list));
    }

    /**
     * Normalize query parameter: trim and convert blank to null
     */
    private String normalizeParam(String param) {
        if (param == null) {
            return null;
        }
        String trimmed = param.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Parse instrument_id with proper error handling
     */
    private Long parseInstrumentId(String instrumentId) {
        try {
            return Long.parseLong(instrumentId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "instrument_id 格式錯誤，必須為數字: " + instrumentId);
        }
    }

    private void validateSymbolKey(String symbolKey) {
        if (symbolKey == null || symbolKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "symbol_key 為必填");
        }
        String[] parts = symbolKey.split(":", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "symbol_key 格式錯誤，應為 MARKET:EXCHANGE:TICKER 格式: " + symbolKey);
        }
        MarketCode.requireSupported(parts[0], ErrorCode.VALIDATION_ERROR, "不支援的市場: " + parts[0]);
    }

    private String normalizeTickerType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type 為必填");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TICKER_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type 不支援: " + normalized);
        }
        return normalized;
    }
}
