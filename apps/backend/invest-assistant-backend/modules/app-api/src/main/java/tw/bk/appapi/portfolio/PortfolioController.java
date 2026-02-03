package tw.bk.appapi.portfolio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.portfolio.dto.CreatePortfolioRequest;
import tw.bk.appapi.portfolio.dto.CreateTradeRequest;
import tw.bk.appapi.portfolio.dto.UpdateTradeRequest;
import tw.bk.appapi.portfolio.vo.PortfolioResponse;
import tw.bk.appapi.portfolio.vo.PositionResponse;
import tw.bk.appapi.portfolio.vo.TradeResponse;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.appportfolio.model.PositionWithQuote;
import tw.bk.appportfolio.service.QuoteProvider;
import tw.bk.appstocks.service.StockQuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@Tag(name = "Portfolio", description = "投資組合 / 交易 / 持倉管理")
public class PortfolioController {
    private static final Logger log = LoggerFactory.getLogger(PortfolioController.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SOURCE_MANUAL = "MANUAL";

    private final PortfolioService portfolioService;
    private final CurrentUserProvider currentUserProvider;
    private final StockQuoteService stockQuoteService;

    public PortfolioController(PortfolioService portfolioService,
            CurrentUserProvider currentUserProvider,
            StockQuoteService stockQuoteService) {
        this.portfolioService = portfolioService;
        this.currentUserProvider = currentUserProvider;
        this.stockQuoteService = stockQuoteService;
    }

    // ======================= Portfolio =======================

    @GetMapping("/portfolios")
    @Operation(summary = "取得投資組合列表")
    public Result<List<PortfolioResponse>> listPortfolios() {
        Long userId = requireUserId();
        List<PortfolioEntity> portfolios = portfolioService.listPortfolios(userId);
        List<PortfolioResponse> response = portfolios.stream()
                .map(PortfolioResponse::from)
                .toList();
        return Result.ok(response);
    }

    @PostMapping("/portfolios")
    @Operation(summary = "新增投資組合")
    public Result<PortfolioResponse> createPortfolio(@Valid @RequestBody CreatePortfolioRequest request) {
        Long userId = requireUserId();
        PortfolioEntity portfolio = portfolioService.createPortfolio(
                userId,
                request.getName(),
                request.getBaseCurrency());
        return Result.ok(PortfolioResponse.from(portfolio));
    }

    @GetMapping("/portfolios/{portfolioId}")
    @Operation(summary = "取得投資組合")
    public Result<PortfolioResponse> getPortfolio(@PathVariable String portfolioId) {
        Long userId = requireUserId();
        Long id = parseId(portfolioId);
        PortfolioEntity portfolio = portfolioService.getPortfolio(userId, id);
        QuoteProvider quoteProvider = createQuoteProvider();
        tw.bk.appportfolio.model.PortfolioSummary summary = portfolioService.getPortfolioSummary(userId, id,
                quoteProvider);
        return Result.ok(PortfolioResponse.fromWithSummary(
                portfolio,
                summary.totalMarketValue(),
                summary.totalCost(),
                summary.totalPnl(),
                summary.totalPnlPercent()));
    }

    // ======================= Positions =======================

    @GetMapping("/portfolios/{portfolioId}/positions")
    @Operation(summary = "取得持倉列表")
    public Result<List<PositionResponse>> listPositions(@PathVariable String portfolioId) {
        Long userId = requireUserId();
        QuoteProvider quoteProvider = createQuoteProvider();
        List<PositionWithQuote> positions = portfolioService.listPositionsWithQuotes(userId, parseId(portfolioId),
                quoteProvider);
        List<PositionResponse> response = positions.stream()
                .map(PositionResponse::from)
                .toList();
        return Result.ok(response);
    }

    private QuoteProvider createQuoteProvider() {
        return symbolKey -> {
            try {
                var quote = stockQuoteService.getQuote(symbolKey);
                return java.util.Optional.ofNullable(quote.getPrice());
            } catch (Exception e) {
                log.warn("無法取得報價: {}, 錯誤: {}", symbolKey, e.getMessage());
                return java.util.Optional.empty();
            }
        };
    }

    // ======================= Trades =======================

    @GetMapping("/portfolios/{portfolioId}/trades")
    @Operation(summary = "取得交易列表")
    public Result<PageResponse<TradeResponse>> listTrades(
            @PathVariable String portfolioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "tradeDate,desc") String sort) {

        Long userId = requireUserId();
        Pageable pageable = buildPageable(page, size, sort);
        Page<StockTradeEntity> trades = portfolioService.listTrades(
                userId, parseId(portfolioId), from, to, pageable);

        List<TradeResponse> items = trades.getContent().stream()
                .map(TradeResponse::from)
                .toList();
        return Result.ok(PageResponse.ok(items, page, size, trades.getTotalElements()));
    }

    @PostMapping("/portfolios/{portfolioId}/trades")
    @Operation(summary = "新增交易")
    public Result<TradeResponse> createTrade(
            @PathVariable String portfolioId,
            @Valid @RequestBody CreateTradeRequest request) {

        Long userId = requireUserId();
        TradeCommand command = toTradeCommand(request);
        StockTradeEntity trade = portfolioService.createTrade(userId, parseId(portfolioId), command);
        return Result.ok(TradeResponse.from(trade));
    }

    @PatchMapping("/trades/{tradeId}")
    @Operation(summary = "更新交易")
    public Result<TradeResponse> updateTrade(
            @PathVariable String tradeId,
            @Valid @RequestBody UpdateTradeRequest request) {

        Long userId = requireUserId();
        TradeCommand command = toTradeCommand(request);
        StockTradeEntity trade = portfolioService.updateTrade(userId, parseId(tradeId), command);
        return Result.ok(TradeResponse.from(trade));
    }

    @DeleteMapping("/trades/{tradeId}")
    @Operation(summary = "刪除交易")
    public Result<Void> deleteTrade(@PathVariable String tradeId) {
        Long userId = requireUserId();
        portfolioService.deleteTrade(userId, parseId(tradeId));
        return Result.ok();
    }

    // ======================= Private Helpers =======================

    private Long requireUserId() {
        return currentUserProvider.getUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized"));
    }

    private Long parseId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid ID format");
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid number format: " + value);
        }
    }

    private Pageable buildPageable(int page, int size, String sort) {
        int safePage = Math.max(1, page) - 1; // API 是 1-based，Spring 是 0-based
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Sort sortObj = Sort.unsorted();
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (parts.length == 2) {
                Sort.Direction direction = "desc".equalsIgnoreCase(parts[1])
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;
                sortObj = Sort.by(direction, parts[0].trim());
            } else if (parts.length == 1) {
                sortObj = Sort.by(Sort.Direction.ASC, parts[0].trim());
            }
        }
        return PageRequest.of(safePage, safeSize, sortObj);
    }

    private TradeCommand toTradeCommand(CreateTradeRequest request) {
        return new TradeCommand(
                parseId(request.getInstrumentId()),
                request.getTradeDate(),
                request.getSettlementDate(),
                request.getSide(),
                parseDecimal(request.getQuantity()),
                parseDecimal(request.getPrice()),
                request.getCurrency(),
                parseDecimal(request.getFee()),
                parseDecimal(request.getTax()),
                request.getAccountId() != null ? parseId(request.getAccountId()) : null,
                SOURCE_MANUAL);
    }

    private TradeCommand toTradeCommand(UpdateTradeRequest request) {
        return new TradeCommand(
                parseId(request.getInstrumentId()),
                request.getTradeDate(),
                request.getSettlementDate(),
                request.getSide(),
                parseDecimal(request.getQuantity()),
                parseDecimal(request.getPrice()),
                request.getCurrency(),
                parseDecimal(request.getFee()),
                parseDecimal(request.getTax()),
                request.getAccountId() != null ? parseId(request.getAccountId()) : null,
                SOURCE_MANUAL);
    }
}
