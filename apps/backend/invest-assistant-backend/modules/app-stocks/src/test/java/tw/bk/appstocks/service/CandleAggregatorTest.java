package tw.bk.appstocks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.bk.appstocks.model.Candle;

class CandleAggregatorTest {

    private CandleAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new CandleAggregator();
    }

    @Test
    void aggregateWeekly_shouldUseOhlcvRulesAndSortDailyCandles() {
        List<Candle> dailyCandles = List.of(
                candle("2330", "2026-01-07T00:00:00", "110", "130", "105", "125", 200),
                candle("2330", "2026-01-05T00:00:00", "100", "120", "90", "110", 100),
                candle("2330", "2026-01-09T00:00:00", "125", "128", "95", "118", 300));

        List<Candle> weekly = aggregator.aggregate("1w", dailyCandles);

        assertEquals(1, weekly.size());
        Candle candle = weekly.getFirst();
        assertEquals("2330", candle.getTicker());
        assertEquals(LocalDateTime.parse("2026-01-05T00:00:00"), candle.getTimestamp());
        assertEquals(new BigDecimal("100"), candle.getOpen());
        assertEquals(new BigDecimal("130"), candle.getHigh());
        assertEquals(new BigDecimal("90"), candle.getLow());
        assertEquals(new BigDecimal("118"), candle.getClose());
        assertEquals(600L, candle.getVolume());
    }

    @Test
    void aggregateWeekly_shouldSplitDifferentIsoWeeks() {
        List<Candle> dailyCandles = List.of(
                candle("2330", "2026-01-09T00:00:00", "100", "110", "90", "105", 100),
                candle("2330", "2026-01-12T00:00:00", "106", "120", "101", "119", 200));

        List<Candle> weekly = aggregator.aggregate("1w", dailyCandles);

        assertEquals(2, weekly.size());
        assertEquals(LocalDateTime.parse("2026-01-09T00:00:00"), weekly.get(0).getTimestamp());
        assertEquals(LocalDateTime.parse("2026-01-12T00:00:00"), weekly.get(1).getTimestamp());
    }

    @Test
    void aggregateMonthly_shouldSplitByMonth() {
        List<Candle> dailyCandles = List.of(
                candle("2330", "2026-01-30T00:00:00", "100", "115", "95", "110", 100),
                candle("2330", "2026-02-02T00:00:00", "111", "130", "109", "128", 200),
                candle("2330", "2026-02-27T00:00:00", "128", "129", "100", "105", 300));

        List<Candle> monthly = aggregator.aggregate("1mo", dailyCandles);

        assertEquals(2, monthly.size());
        assertEquals(LocalDateTime.parse("2026-01-30T00:00:00"), monthly.get(0).getTimestamp());
        assertEquals(new BigDecimal("100"), monthly.get(0).getOpen());
        assertEquals(new BigDecimal("110"), monthly.get(0).getClose());
        assertEquals(100L, monthly.get(0).getVolume());

        assertEquals(LocalDateTime.parse("2026-02-02T00:00:00"), monthly.get(1).getTimestamp());
        assertEquals(new BigDecimal("111"), monthly.get(1).getOpen());
        assertEquals(new BigDecimal("130"), monthly.get(1).getHigh());
        assertEquals(new BigDecimal("100"), monthly.get(1).getLow());
        assertEquals(new BigDecimal("105"), monthly.get(1).getClose());
        assertEquals(500L, monthly.get(1).getVolume());
    }

    @Test
    void aggregate_shouldReturnEmptyListForEmptyInput() {
        assertTrue(aggregator.aggregate("1w", List.of()).isEmpty());
    }

    @Test
    void aggregate_shouldReturnSortedDailyCandlesForUnsupportedInterval() {
        List<Candle> dailyCandles = List.of(
                candle("2330", "2026-01-06T00:00:00", "100", "110", "90", "105", 100),
                candle("2330", "2026-01-05T00:00:00", "95", "106", "92", "100", 200));

        List<Candle> result = aggregator.aggregate("1d", dailyCandles);

        assertEquals(2, result.size());
        assertEquals(LocalDateTime.parse("2026-01-05T00:00:00"), result.get(0).getTimestamp());
        assertEquals(LocalDateTime.parse("2026-01-06T00:00:00"), result.get(1).getTimestamp());
    }

    private Candle candle(
            String ticker,
            String timestamp,
            String open,
            String high,
            String low,
            String close,
            long volume) {
        return Candle.builder()
                .ticker(ticker)
                .timestamp(LocalDateTime.parse(timestamp))
                .open(new BigDecimal(open))
                .high(new BigDecimal(high))
                .low(new BigDecimal(low))
                .close(new BigDecimal(close))
                .volume(volume)
                .build();
    }
}
