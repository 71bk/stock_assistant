package tw.bk.appstocks.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appstocks.adapter.TpexWarrantMarketClient;
import tw.bk.appstocks.adapter.TpexWarrantMarketClient.TpexWarrantQuote;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.config.StockMarketProperties;

@Service
@RequiredArgsConstructor
public class WarrantQuoteService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final ZoneId TAIPEI_TZ = ZoneId.of("Asia/Taipei");

    private final TpexWarrantMarketClient marketClient;
    private final StockCacheService cacheService;
    private final StockMarketProperties properties;
    private final ConcurrentHashMap<String, CompletableFuture<List<TpexWarrantQuote>>> inflight = new ConcurrentHashMap<>();

    public Quote getQuote(String ticker) {
        List<TpexWarrantQuote> quotes = fetchDailyQuotesCached();
        TpexWarrantQuote latest = quotes.stream()
                .filter(q -> matchTicker(q.code(), ticker))
                .max(Comparator.comparing(TpexWarrantQuote::date))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Warrant quote not found: " + ticker));

        BigDecimal previousClose = null;
        if (latest.close() != null && latest.change() != null) {
            previousClose = latest.close().subtract(latest.change());
        }
        BigDecimal changePercent = null;
        if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) != 0
                && latest.change() != null) {
            changePercent = latest.change()
                    .divide(previousClose, 6, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED);
        }

        return Quote.builder()
                .ticker(latest.code())
                .price(latest.close())
                .open(latest.open())
                .high(latest.high())
                .low(latest.low())
                .previousClose(previousClose)
                .volume(latest.volume())
                .change(latest.change())
                .changePercent(changePercent)
                .timestamp(latest.date().atStartOfDay(ZoneOffset.UTC).toInstant())
                .build();
    }

    public List<Candle> getCandles(String ticker, String interval, LocalDate from, LocalDate to) {
        String normalized = interval == null ? "1d" : interval.trim().toLowerCase(Locale.ROOT);
        List<TpexWarrantQuote> quotes;
        if ("1d".equals(normalized)) {
            quotes = fetchDailyQuotesCached();
        } else if ("1mo".equals(normalized)) {
            quotes = fetchMonthlyQuotesCached();
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Warrant candles only support 1d/1mo interval");
        }

        return quotes.stream()
                .filter(q -> matchTicker(q.code(), ticker))
                .filter(q -> isWithinRange(q.date(), from, to))
                .sorted(Comparator.comparing(TpexWarrantQuote::date))
                .map(q -> Candle.builder()
                        .ticker(q.code())
                        .timestamp(toTimestamp(q.date()))
                        .open(q.open())
                        .high(q.high())
                        .low(q.low())
                        .close(q.close())
                        .volume(q.volume())
                        .build())
                .toList();
    }

    private boolean matchTicker(String code, String ticker) {
        if (code == null || ticker == null) {
            return false;
        }
        return Objects.equals(code.trim(), ticker.trim());
    }

    private boolean isWithinRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private LocalDateTime toTimestamp(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toLocalDateTime();
    }

    private List<TpexWarrantQuote> fetchDailyQuotesCached() {
        LocalDate today = LocalDate.now(TAIPEI_TZ);
        String cacheKey = "warrant:daily:" + today;
        Optional<List<TpexWarrantQuote>> cached = cacheService.getList(cacheKey, TpexWarrantQuote.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        return singleFlight(cacheKey, () -> {
            Optional<List<TpexWarrantQuote>> cachedAgain = cacheService.getList(cacheKey, TpexWarrantQuote.class);
            if (cachedAgain.isPresent()) {
                return cachedAgain.get();
            }
            List<TpexWarrantQuote> quotes = marketClient.fetchDailyQuotes();
            cacheService.setList(cacheKey, quotes, properties.getCache().getQuoteTtl());
            return quotes;
        });
    }

    private List<TpexWarrantQuote> fetchMonthlyQuotesCached() {
        YearMonth month = YearMonth.now(TAIPEI_TZ);
        String cacheKey = "warrant:monthly:" + month;
        Optional<List<TpexWarrantQuote>> cached = cacheService.getList(cacheKey, TpexWarrantQuote.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        return singleFlight(cacheKey, () -> {
            Optional<List<TpexWarrantQuote>> cachedAgain = cacheService.getList(cacheKey, TpexWarrantQuote.class);
            if (cachedAgain.isPresent()) {
                return cachedAgain.get();
            }
            List<TpexWarrantQuote> quotes = marketClient.fetchMonthlyQuotes();
            cacheService.setList(cacheKey, quotes, properties.getCache().getCandlesTtl());
            return quotes;
        });
    }

    private List<TpexWarrantQuote> singleFlight(String key, Supplier<List<TpexWarrantQuote>> supplier) {
        CompletableFuture<List<TpexWarrantQuote>> future = new CompletableFuture<>();
        CompletableFuture<List<TpexWarrantQuote>> existing = inflight.putIfAbsent(key, future);
        if (existing == null) {
            try {
                List<TpexWarrantQuote> value = supplier.get();
                future.complete(value);
                return value;
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
                throw ex;
            } finally {
                inflight.remove(key, future);
            }
        }

        try {
            return existing.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }
}
