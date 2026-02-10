package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appapi.admin.dto.PositionsRebuildRequest;
import tw.bk.appapi.admin.dto.ValuationSnapshotRequest;
import tw.bk.appapi.admin.vo.PositionsRebuildResponse;
import tw.bk.appapi.admin.vo.ValuationSnapshotResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appportfolio.model.PortfolioPositionsRebuildResult;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.appportfolio.service.QuoteProvider;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.service.StockQuoteService;

@RestController
@RequestMapping("/admin/portfolios")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminPortfolioController {
    private static final String ADMIN_HEADER = "X-Admin-Key";
    private static final int HISTORICAL_LOOKBACK_DAYS = 14;

    private final PortfolioService portfolioService;
    private final AdminProperties adminProperties;
    private final CurrentUserProvider currentUserProvider;
    private final StockQuoteService stockQuoteService;

    @PostMapping("/positions-rebuild")
    @Operation(summary = "Rebuild positions for a portfolio")
    public Result<PositionsRebuildResponse> rebuildPositions(
            @RequestHeader(value = ADMIN_HEADER, required = false) String adminKey,
            @Valid @RequestBody PositionsRebuildRequest request) {
        requireAdminKey(adminKey);
        PortfolioPositionsRebuildResult result = portfolioService.rebuildPositions(
                request.getPortfolioId(),
                request.getInstrumentId());
        return Result.ok(PositionsRebuildResponse.from(result));
    }

    @PostMapping("/valuations-snapshot")
    @Operation(summary = "Snapshot portfolio valuations")
    public Result<ValuationSnapshotResponse> snapshotValuations(
            @RequestHeader(value = ADMIN_HEADER, required = false) String adminKey,
            @RequestBody(required = false) ValuationSnapshotRequest request) {
        requireAdminKey(adminKey);
        Long userId = request != null ? request.getUserId() : null;
        Long portfolioId = request != null ? request.getPortfolioId() : null;
        java.time.LocalDate asOfDate = request != null ? request.getAsOfDate() : null;

        PortfolioValuationSnapshotResult result = portfolioService.snapshotValuations(
                userId,
                portfolioId,
                asOfDate,
                createQuoteProvider());
        return Result.ok(ValuationSnapshotResponse.from(result));
    }

    private void requireAdminKey(String provided) {
        String expected = adminProperties.getApiKey();
        if (expected == null || expected.isBlank()) {
            if (currentUserProvider.getUserId().isEmpty()) {
                throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
            }
            return;
        }
        if (provided == null || provided.isBlank() || !expected.equals(provided)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Admin key invalid");
        }
    }

    private Optional<BigDecimal> resolveCurrentPrice(String symbolKey) {
        if (symbolKey == null || symbolKey.isBlank()) {
            return Optional.empty();
        }
        try {
            Quote quote = stockQuoteService.getQuote(symbolKey);
            return quote == null ? Optional.empty() : Optional.ofNullable(quote.getPrice());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> resolveHistoricalClosePrice(String symbolKey, LocalDate asOfDate) {
        if (symbolKey == null || symbolKey.isBlank() || asOfDate == null) {
            return Optional.empty();
        }
        try {
            LocalDate from = asOfDate.minusDays(HISTORICAL_LOOKBACK_DAYS);
            List<Candle> candles = stockQuoteService.getCandles(symbolKey, "1d", from, asOfDate);
            return candles.stream()
                    .filter(candle -> candle.getTimestamp() != null)
                    .filter(candle -> !candle.getTimestamp().toLocalDate().isAfter(asOfDate))
                    .filter(candle -> candle.getClose() != null)
                    .max(Comparator.comparing(Candle::getTimestamp))
                    .map(Candle::getClose);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private QuoteProvider createQuoteProvider() {
        return new QuoteProvider() {
            @Override
            public Optional<BigDecimal> getCurrentPrice(String symbolKey) {
                return resolveCurrentPrice(symbolKey);
            }

            @Override
            public Optional<BigDecimal> getPrice(String symbolKey, LocalDate asOfDate, LocalDate today) {
                if (asOfDate != null && today != null && asOfDate.isBefore(today)) {
                    return resolveHistoricalClosePrice(symbolKey, asOfDate);
                }
                return resolveCurrentPrice(symbolKey);
            }
        };
    }
}
