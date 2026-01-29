package tw.bk.appstocks.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.model.MarketCode;
import tw.bk.appstocks.config.StockMarketProperties;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.port.StockMarketClient;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 股票報價服務
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQuoteService {

    private final InstrumentRepository instrumentRepository;
    private final List<StockMarketClient> stockMarketClients;
    private final StockCacheService cacheService;
    private final StockMarketProperties properties;
    private Map<MarketCode, StockMarketClient> clientMap = Collections.emptyMap();

    @PostConstruct
    void initClientMap() {
        clientMap = stockMarketClients.stream()
                .collect(Collectors.toUnmodifiableMap(
                        StockMarketClient::getSupportedMarket,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException("重複的市場 client: " + a.getSupportedMarket().getCode());
                        }));
    }

    /**
     * 取得股票即時報價
     * 
     * @param symbolKey 商品唯一識別碼（如 US:XNAS:AAPL）
     * @return 報價資料
     */
    public Quote getQuote(String symbolKey) {
        // 1. 檢查快取
        String cacheKey = "quote:" + symbolKey;
        Optional<Quote> cached = cacheService.get(cacheKey, Quote.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. 查詢商品資訊
        InstrumentEntity instrument = instrumentRepository.findBySymbolKeyWithRelations(symbolKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在: " + symbolKey));

        // 3. 根據市場選擇對應的 client
        String marketCodeValue = instrument.getMarket() != null ? instrument.getMarket().getCode() : null;
        MarketCode marketCode = MarketCode.requireSupported(
                marketCodeValue,
                ErrorCode.INTERNAL_ERROR,
                "不支援的市場: " + marketCodeValue);
        StockMarketClient client = getClientByMarket(marketCode);

        // 4. 呼叫第三方 API
        Quote quote = client.getQuote(instrument.getTicker())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "無法取得股票報價: " + symbolKey));

        // 5. 寫入快取
        cacheService.set(cacheKey, quote, properties.getCache().getQuoteTtl());

        return quote;
    }

    /**
     * 取得 K 線資料（歷史 OHLC）
     * 
     * @param symbolKey 商品唯一識別碼（如 US:XNAS:AAPL）
     * @param interval  時間間隔（1m/5m/15m/30m/1h/1d等）
     * @param from      起始日期
     * @param to        結束日期
     * @return K線資料列表
     */
    public List<Candle> getCandles(String symbolKey, String interval, LocalDate from, LocalDate to) {
        // 1. 檢查快取
        String cacheKey = String.format("candles:%s:%s:%s:%s",
                symbolKey, interval, from, to);
        Optional<List<Candle>> cached = cacheService.getList(cacheKey, Candle.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. 查詢商品資訊
        InstrumentEntity instrument = instrumentRepository.findBySymbolKeyWithRelations(symbolKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在: " + symbolKey));

        // 3. 根據市場選擇對應的 client
        String marketCodeValue = instrument.getMarket() != null ? instrument.getMarket().getCode() : null;
        MarketCode marketCode = MarketCode.requireSupported(
                marketCodeValue,
                ErrorCode.INTERNAL_ERROR,
                "不支援的市場: " + marketCodeValue);
        StockMarketClient client = getClientByMarket(marketCode);

        // 4. 呼叫第三方 API
        List<Candle> candles = client.getCandles(instrument.getTicker(), interval, from, to);

        // 5. 寫入快取（K線資料快取時間較長）
        cacheService.setList(cacheKey, candles, properties.getCache().getCandlesTtl());

        return candles;
    }

    /**
     * 根據市場代碼取得對應的 Client
     */
    private StockMarketClient getClientByMarket(MarketCode marketCode) {
        StockMarketClient client = clientMap.get(marketCode);
        if (client == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "不支援的市場: " + marketCode.getCode());
        }

        return client;
    }
}
