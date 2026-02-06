package tw.bk.appstocks.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appstocks.adapter.TpexWarrantMarketClient;
import tw.bk.appstocks.adapter.TpexWarrantMarketClient.TpexWarrantQuote;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;

@Service
@RequiredArgsConstructor
public class WarrantQuoteService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final TpexWarrantMarketClient marketClient;

    public Quote getQuote(String ticker) {
        List<TpexWarrantQuote> quotes = marketClient.fetchDailyQuotes();
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
                .timestamp(latest.date().atStartOfDay().toInstant(ZoneOffset.UTC))
                .build();
    }

    public List<Candle> getCandles(String ticker, String interval, LocalDate from, LocalDate to) {
        String normalized = interval == null ? "1d" : interval.trim().toLowerCase(Locale.ROOT);
        List<TpexWarrantQuote> quotes;
        if ("1d".equals(normalized)) {
            quotes = marketClient.fetchDailyQuotes();
        } else if ("1mo".equals(normalized)) {
            quotes = marketClient.fetchMonthlyQuotes();
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
        return date.atStartOfDay();
    }
}
