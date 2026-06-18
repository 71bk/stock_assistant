package tw.bk.appstocks.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tw.bk.appstocks.model.Candle;

@Component
public class CandleAggregator {

    private static final WeekFields ISO_WEEK = WeekFields.ISO;

    public boolean isAggregateInterval(String interval) {
        String normalized = normalizeInterval(interval);
        return "1w".equals(normalized) || "1mo".equals(normalized);
    }

    public List<Candle> aggregate(String interval, List<Candle> dailyCandles) {
        if (dailyCandles == null || dailyCandles.isEmpty()) {
            return List.of();
        }

        String normalized = normalizeInterval(interval);
        if ("1w".equals(normalized)) {
            return aggregateBy(dailyCandles, this::weeklyKey);
        }
        if ("1mo".equals(normalized)) {
            return aggregateBy(dailyCandles, this::monthlyKey);
        }
        return sortedValidCandles(dailyCandles);
    }

    private List<Candle> aggregateBy(List<Candle> candles, Function<Candle, Object> keyFunction) {
        Map<Object, Accumulator> groups = new LinkedHashMap<>();
        for (Candle candle : sortedValidCandles(candles)) {
            Object key = keyFunction.apply(candle);
            groups.computeIfAbsent(key, ignored -> new Accumulator(candle)).add(candle);
        }

        return groups.values().stream()
                .map(Accumulator::toCandle)
                .toList();
    }

    private List<Candle> sortedValidCandles(List<Candle> candles) {
        return candles.stream()
                .filter(candle -> candle != null && candle.getTimestamp() != null)
                .sorted(Comparator.comparing(Candle::getTimestamp))
                .toList();
    }

    private WeeklyKey weeklyKey(Candle candle) {
        LocalDate date = candle.getTimestamp().toLocalDate();
        int weekBasedYear = date.get(ISO_WEEK.weekBasedYear());
        int weekOfYear = date.get(ISO_WEEK.weekOfWeekBasedYear());
        return new WeeklyKey(weekBasedYear, weekOfYear);
    }

    private YearMonth monthlyKey(Candle candle) {
        return YearMonth.from(candle.getTimestamp());
    }

    private String normalizeInterval(String interval) {
        if (interval == null) {
            return "1d";
        }
        return interval.trim().toLowerCase(Locale.ROOT);
    }

    private record WeeklyKey(int year, int week) {
    }

    private static final class Accumulator {
        private final Candle first;
        private Candle last;
        private BigDecimal high;
        private BigDecimal low;
        private long volume;

        private Accumulator(Candle first) {
            this.first = first;
            this.high = first.getHigh();
            this.low = first.getLow();
        }

        private void add(Candle candle) {
            last = candle;
            high = max(high, candle.getHigh());
            low = min(low, candle.getLow());
            if (candle.getVolume() != null) {
                volume += candle.getVolume();
            }
        }

        private Candle toCandle() {
            return Candle.builder()
                    .ticker(first.getTicker())
                    .timestamp(first.getTimestamp())
                    .open(first.getOpen())
                    .high(high)
                    .low(low)
                    .close(last == null ? first.getClose() : last.getClose())
                    .volume(volume)
                    .build();
        }

        private BigDecimal max(BigDecimal current, BigDecimal candidate) {
            if (current == null) {
                return candidate;
            }
            if (candidate == null) {
                return current;
            }
            return current.compareTo(candidate) >= 0 ? current : candidate;
        }

        private BigDecimal min(BigDecimal current, BigDecimal candidate) {
            if (current == null) {
                return candidate;
            }
            if (candidate == null) {
                return current;
            }
            return current.compareTo(candidate) <= 0 ? current : candidate;
        }
    }
}
