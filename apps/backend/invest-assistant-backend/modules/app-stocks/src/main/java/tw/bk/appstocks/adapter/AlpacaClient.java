package tw.bk.appstocks.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.MarketCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.util.TraceIdUtils;
import tw.bk.appstocks.adapter.dto.AlpacaBarsResponse;
import tw.bk.appstocks.adapter.dto.AlpacaQuotesResponse;
import tw.bk.appstocks.config.StockMarketProperties;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.port.StockMarketClient;
import tw.bk.appstocks.service.ExternalApiRateLimiter;
import tw.bk.appstocks.service.StockMetricsRecorder;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlpacaClient implements StockMarketClient {
    private static final String VENDOR = "alpaca";
    private static final String ENDPOINT_QUOTE = "quote";
    private static final String ENDPOINT_CANDLES = "candles";
    private static final String HEADER_KEY_ID = "APCA-API-KEY-ID";
    private static final String HEADER_SECRET_KEY = "APCA-API-SECRET-KEY";
    private static final String HEADER_REQUEST_ID = "X-Request-ID";

    private final StockMarketProperties properties;
    private final ExternalApiRateLimiter rateLimiter;
    private final StockMetricsRecorder metricsRecorder;
    private final RestClient restClient = RestClient.create();

    @Override
    public Optional<Quote> getQuote(String ticker) {
        rateLimiter.acquire(VENDOR, ENDPOINT_QUOTE);
        long startedAt = System.nanoTime();
        try {
            String url = String.format("%s/v2/stocks/quotes/latest?symbols=%s&feed=iex",
                    properties.getAlpaca().getBaseUrl(),
                    ticker);

            ResponseEntity<AlpacaQuotesResponse> response = restClient.get()
                    .uri(url)
                    .header(HEADER_KEY_ID, properties.getAlpaca().getKeyId().trim())
                    .header(HEADER_SECRET_KEY, properties.getAlpaca().getSecretKey().trim())
                    .retrieve()
                    .toEntity(AlpacaQuotesResponse.class);

            String requestId = extractRequestId(response.getHeaders());
            int status = response.getStatusCode().value();
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_QUOTE, status, latencyMs, true);
            log.info(
                    "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={}",
                    VENDOR,
                    ENDPOINT_QUOTE,
                    ticker,
                    status,
                    latencyMs,
                    requestId,
                    TraceIdUtils.getTraceId());

            AlpacaQuotesResponse body = response.getBody();
            if (body == null || body.getQuotes() == null || !body.getQuotes().containsKey(ticker)) {
                log.warn("Alpaca quote not found for ticker: {}", ticker);
                return Optional.empty();
            }

            AlpacaQuotesResponse.QuoteData quoteData = body.getQuotes().get(ticker);
            BigDecimal bidPrice = quoteData.getBp() != null ? quoteData.getBp() : BigDecimal.ZERO;
            BigDecimal askPrice = quoteData.getAp() != null ? quoteData.getAp() : BigDecimal.ZERO;
            BigDecimal midPrice = bidPrice.add(askPrice).divide(BigDecimal.valueOf(2));

            return Optional.of(Quote.builder()
                    .ticker(ticker)
                    .price(midPrice)
                    .open(null)
                    .high(null)
                    .low(null)
                    .previousClose(null)
                    .volume(null)
                    .change(null)
                    .changePercent(null)
                    .timestamp(parseTimestamp(quoteData.getT()))
                    .build());
        } catch (RestClientResponseException ex) {
            long latencyMs = elapsedMillis(startedAt);
            int status = ex.getStatusCode().value();
            String requestId = extractRequestId(ex.getResponseHeaders());
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_QUOTE, status, latencyMs, false);
            log.warn(
                    "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={} error={}",
                    VENDOR,
                    ENDPOINT_QUOTE,
                    ticker,
                    status,
                    latencyMs,
                    requestId,
                    TraceIdUtils.getTraceId(),
                    ex.getMessage());
            if (status == 429) {
                throw rateLimited("Alpaca", ticker, ex.getResponseHeaders());
            }
            log.error("Failed to fetch quote from Alpaca for ticker: {}", ticker, ex);
            return Optional.empty();
        } catch (Exception ex) {
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_QUOTE, null, latencyMs, false);
            log.warn(
                    "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={} error={}",
                    VENDOR,
                    ENDPOINT_QUOTE,
                    ticker,
                    "NA",
                    latencyMs,
                    "NA",
                    TraceIdUtils.getTraceId(),
                    ex.getMessage());
            log.error("Failed to fetch quote from Alpaca for ticker: {}", ticker, ex);
            return Optional.empty();
        }
    }

    @Override
    public List<Candle> getCandles(String ticker, String interval, LocalDate from, LocalDate to) {
        rateLimiter.acquire(VENDOR, ENDPOINT_CANDLES);
        long startedAt = System.nanoTime();
        try {
            String timeframe = convertInterval(interval);
            String url = buildCandlesUrl(ticker, timeframe, from, to);

            ResponseEntity<AlpacaBarsResponse> response = restClient.get()
                    .uri(url)
                    .header(HEADER_KEY_ID, properties.getAlpaca().getKeyId())
                    .header(HEADER_SECRET_KEY, properties.getAlpaca().getSecretKey())
                    .retrieve()
                    .toEntity(AlpacaBarsResponse.class);

            String requestId = extractRequestId(response.getHeaders());
            int status = response.getStatusCode().value();
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_CANDLES, status, latencyMs, true);
            log.info(
                    "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={} interval={}",
                    VENDOR,
                    ENDPOINT_CANDLES,
                    ticker,
                    status,
                    latencyMs,
                    requestId,
                    TraceIdUtils.getTraceId(),
                    interval);

            AlpacaBarsResponse body = response.getBody();
            if (body == null || body.getBars() == null || !body.getBars().containsKey(ticker)) {
                log.warn("Alpaca bars not found for ticker: {}", ticker);
                return List.of();
            }

            List<AlpacaBarsResponse.BarData> bars = body.getBars().get(ticker);
            if (bars == null || bars.isEmpty()) {
                return List.of();
            }

            List<Candle> candles = new ArrayList<>();
            for (AlpacaBarsResponse.BarData bar : bars) {
                Candle candle = Candle.builder()
                        .ticker(ticker)
                        .timestamp(LocalDateTime.ofInstant(parseTimestamp(bar.getT()), ZoneOffset.UTC))
                        .open(bar.getO() != null ? bar.getO() : BigDecimal.ZERO)
                        .high(bar.getH() != null ? bar.getH() : BigDecimal.ZERO)
                        .low(bar.getL() != null ? bar.getL() : BigDecimal.ZERO)
                        .close(bar.getC() != null ? bar.getC() : BigDecimal.ZERO)
                        .volume(bar.getV() != null ? bar.getV() : 0L)
                        .build();
                candles.add(candle);
            }

            candles.sort(Comparator.comparing(Candle::getTimestamp));
            return candles;
        } catch (RestClientResponseException ex) {
            long latencyMs = elapsedMillis(startedAt);
            int status = ex.getStatusCode().value();
            String requestId = extractRequestId(ex.getResponseHeaders());
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_CANDLES, status, latencyMs, false);
            log.warn(
                    "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={} interval={} error={}",
                    VENDOR,
                    ENDPOINT_CANDLES,
                    ticker,
                    status,
                    latencyMs,
                    requestId,
                    TraceIdUtils.getTraceId(),
                    interval,
                    ex.getMessage());
            if (status == 429) {
                throw rateLimited("Alpaca", ticker, ex.getResponseHeaders());
            }
            log.error("Failed to fetch candles from Alpaca for ticker: {}", ticker, ex);
            return List.of();
        } catch (Exception ex) {
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_CANDLES, null, latencyMs, false);
            log.warn(
                    "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={} interval={} error={}",
                    VENDOR,
                    ENDPOINT_CANDLES,
                    ticker,
                    "NA",
                    latencyMs,
                    "NA",
                    TraceIdUtils.getTraceId(),
                    interval,
                    ex.getMessage());
            log.error("Failed to fetch candles from Alpaca for ticker: {}", ticker, ex);
            return List.of();
        }
    }

    @Override
    public MarketCode getSupportedMarket() {
        return MarketCode.US;
    }

    private String buildCandlesUrl(String ticker, String timeframe, LocalDate from, LocalDate to) {
        StringBuilder url = new StringBuilder();
        url.append(properties.getAlpaca().getBaseUrl())
                .append("/v2/stocks/bars?symbols=").append(ticker)
                .append("&timeframe=").append(timeframe)
                .append("&feed=iex");

        if (from != null) {
            url.append("&start=").append(from);
        }
        if (to != null) {
            url.append("&end=").append(to);
        }

        return url.toString();
    }

    private String convertInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return "1Day";
        }

        String normalized = interval.trim().toLowerCase();
        return switch (normalized) {
            case "1m" -> "1Min";
            case "5m" -> "5Min";
            case "15m" -> "15Min";
            case "30m" -> "30Min";
            case "1h", "60m" -> "1Hour";
            case "1d" -> "1Day";
            case "1w" -> "1Week";
            case "1mo" -> "1Month";
            default -> "1Day";
        };
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(timestamp);
    }

    private String extractRequestId(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        return headers.getFirst(HEADER_REQUEST_ID);
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private BusinessException rateLimited(String vendor, String ticker, HttpHeaders headers) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("vendor", vendor);
        details.put("ticker", ticker);
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null && !retryAfter.isBlank()) {
                details.put("retryAfter", retryAfter);
            }
            String requestId = headers.getFirst(HEADER_REQUEST_ID);
            if (requestId != null && !requestId.isBlank()) {
                details.put("requestId", requestId);
            }
        }
        return new BusinessException(ErrorCode.RATE_LIMITED, "External API rate limited", details);
    }
}
