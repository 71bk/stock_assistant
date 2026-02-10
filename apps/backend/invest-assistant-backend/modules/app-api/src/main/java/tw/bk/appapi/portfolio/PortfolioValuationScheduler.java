package tw.bk.appapi.portfolio;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.appportfolio.service.QuoteProvider;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.service.StockQuoteService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioValuationScheduler {
    private static final int HISTORICAL_LOOKBACK_DAYS = 14;

    private final PortfolioService portfolioService;
    private final StockQuoteService stockQuoteService;
    private final ClockProvider clockProvider;

    @Value("${app.portfolio.valuation.enabled:false}")
    private boolean enabled;

    @Value("${app.portfolio.valuation.zone:Asia/Taipei}")
    private String valuationZone;

    @Scheduled(cron = "${app.portfolio.valuation.cron:0 40 13 * * MON-FRI}",
            zone = "${app.portfolio.valuation.zone:Asia/Taipei}")
    public void runScheduled() {
        if (!enabled) {
            return;
        }

        LocalDate asOfDate = resolveAsOfDate();
        PortfolioValuationSnapshotResult result = portfolioService.snapshotValuations(
                asOfDate,
                createQuoteProvider());

        log.info(
                "Portfolio valuation snapshot finished: asOfDate={}, total={}, succeeded={}, failed={}",
                result.asOfDate(),
                result.total(),
                result.succeeded(),
                result.failed());
        if (!result.failedPortfolioIds().isEmpty()) {
            log.warn("Portfolio valuation snapshot failed portfolioIds={}", result.failedPortfolioIds());
        }
    }

    private LocalDate resolveAsOfDate() {
        try {
            return clockProvider.now().atZone(ZoneId.of(valuationZone)).toLocalDate();
        } catch (DateTimeException ex) {
            log.warn("Invalid valuation zone '{}', fallback to UTC", valuationZone);
            return clockProvider.nowUtc().toLocalDate();
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
