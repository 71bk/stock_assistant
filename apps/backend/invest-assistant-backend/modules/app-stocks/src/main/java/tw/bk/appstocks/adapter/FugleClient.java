package tw.bk.appstocks.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
import org.springframework.web.util.UriComponentsBuilder;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.MarketCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.util.TraceIdUtils;
import tw.bk.appstocks.adapter.dto.FugleCandlesResponse;
import tw.bk.appstocks.adapter.dto.FugleQuoteResponse;
import tw.bk.appstocks.adapter.dto.FugleTickerResponse;
import tw.bk.appstocks.config.StockMarketProperties;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.model.TickerItem;
import tw.bk.appstocks.model.TickerList;
import tw.bk.appstocks.model.TickerQuery;
import tw.bk.appstocks.port.StockMarketClient;
import tw.bk.appstocks.service.ExternalApiRateLimiter;
import tw.bk.appstocks.service.StockMetricsRecorder;

@Slf4j
@Component
@RequiredArgsConstructor
public class FugleClient implements StockMarketClient {
    private static final String VENDOR = "fugle";
    private static final String ENDPOINT_QUOTE = "quote";
    private static final String ENDPOINT_CANDLES = "candles";
    private static final String ENDPOINT_TICKERS = "tickers";
    private static final String ENDPOINT_TICKER_DETAIL = "ticker_detail";
    private static final String HEADER_API_KEY = "X-API-KEY";
    private static final String HEADER_REQUEST_ID = "X-Request-ID";

    private final StockMarketProperties properties;
    private final ObjectMapper objectMapper;
    private final ExternalApiRateLimiter rateLimiter;
    private final StockMetricsRecorder metricsRecorder;
    private final RestClient restClient = RestClient.create();

    @Override
    public Optional<Quote> getQuote(String ticker) {
        rateLimiter.acquire(VENDOR, ENDPOINT_QUOTE);
        long startedAt = System.nanoTime();
        try {
            String url = buildUrl("/intraday/quote", ticker);

            ResponseEntity<FugleQuoteResponse> entity = withApiKey(restClient.get().uri(url))
                    .retrieve()
                    .toEntity(FugleQuoteResponse.class);

            String requestId = extractRequestId(entity.getHeaders());
            int status = entity.getStatusCode().value();
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

            FugleQuoteResponse response = entity.getBody();
            if (response == null) {
                log.warn("Fugle quote response is null for ticker: {}", ticker);
                return Optional.empty();
            }

            BigDecimal lastPrice = response.getLastPrice() != null ? response.getLastPrice() : BigDecimal.ZERO;
            BigDecimal referencePrice = response.getReferencePrice() != null ? response.getReferencePrice() : BigDecimal.ZERO;
            BigDecimal change = response.getChange() != null ? response.getChange() : lastPrice.subtract(referencePrice);

            return Optional.of(Quote.builder()
                    .ticker(ticker)
                    .price(lastPrice)
                    .open(response.getOpenPrice())
                    .high(response.getHighPrice())
                    .low(response.getLowPrice())
                    .previousClose(response.getPreviousClose())
                    .volume(response.getTotal() != null ? response.getTotal().getTradeVolume() : null)
                    .change(change)
                    .changePercent(response.getChangePercent())
                    .timestamp(Instant.now())
                    .build());
        } catch (RestClientResponseException ex) {
            return handleRestClientError(ticker, ENDPOINT_QUOTE, startedAt, ex, true);
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
            log.error("Failed to fetch quote from Fugle for ticker: {}", ticker, ex);
            return Optional.empty();
        }
    }

    @Override
    public List<Candle> getCandles(String ticker, String interval, LocalDate from, LocalDate to) {
        rateLimiter.acquire(VENDOR, ENDPOINT_CANDLES);
        long startedAt = System.nanoTime();
        try {
            String path = shouldUseHistorical(interval, from, to)
                    ? "/historical/candles"
                    : "/intraday/candles";
            String url = buildUrl(path, ticker);

            ResponseEntity<FugleCandlesResponse> entity = withApiKey(restClient.get().uri(url))
                    .retrieve()
                    .toEntity(FugleCandlesResponse.class);

            String requestId = extractRequestId(entity.getHeaders());
            int status = entity.getStatusCode().value();
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

            FugleCandlesResponse response = entity.getBody();
            if (response == null || response.getData() == null) {
                log.warn("Fugle returned empty candles for ticker: {}", ticker);
                return List.of();
            }

            List<Candle> candles = new ArrayList<>();
            for (FugleCandlesResponse.CandleData candleData : response.getData()) {
                LocalDateTime timestamp = parseDate(candleData.getDate());
                if (timestamp == null || !isWithinRange(timestamp, from, to)) {
                    continue;
                }

                Candle candle = Candle.builder()
                        .ticker(ticker)
                        .timestamp(timestamp)
                        .open(candleData.getOpen() != null ? candleData.getOpen() : BigDecimal.ZERO)
                        .high(candleData.getHigh() != null ? candleData.getHigh() : BigDecimal.ZERO)
                        .low(candleData.getLow() != null ? candleData.getLow() : BigDecimal.ZERO)
                        .close(candleData.getClose() != null ? candleData.getClose() : BigDecimal.ZERO)
                        .volume(candleData.getVolume() != null ? candleData.getVolume() : 0L)
                        .build();
                candles.add(candle);
            }

            candles.sort(Comparator.comparing(Candle::getTimestamp));
            return candles;
        } catch (RestClientResponseException ex) {
            return handleRestClientErrorList(ticker, ENDPOINT_CANDLES, interval, startedAt, ex);
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
            log.error("Failed to fetch candles from Fugle for ticker: {}", ticker, ex);
            return List.of();
        }
    }

    @Override
    public Optional<TickerList> getTickers(TickerQuery query) {
        rateLimiter.acquire(VENDOR, ENDPOINT_TICKERS);
        long startedAt = System.nanoTime();
        try {
            String baseUrl = buildUrl("/intraday/tickers");
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);

            if (query != null) {
                addQueryParam(builder, "type", query.getType());
                addQueryParam(builder, "exchange", query.getExchange());
                addQueryParam(builder, "market", query.getMarket());
                addQueryParam(builder, "industry", query.getIndustry());
                addQueryParam(builder, "isNormal", toFlag(query.getIsNormal()));
                addQueryParam(builder, "isAttention", toFlag(query.getIsAttention()));
                addQueryParam(builder, "isDisposition", toFlag(query.getIsDisposition()));
                addQueryParam(builder, "isHalted", toFlag(query.getIsHalted()));
            }

            String url = builder.build(true).toUriString();
            ResponseEntity<String> entity = withApiKey(restClient.get().uri(url))
                    .retrieve()
                    .toEntity(String.class);

            String requestId = extractRequestId(entity.getHeaders());
            int status = entity.getStatusCode().value();
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_TICKERS, status, latencyMs, true);
            log.info(
                    "external_api_call vendor={} endpoint={} status={} latency_ms={} request_id={} trace_id={}",
                    VENDOR,
                    ENDPOINT_TICKERS,
                    status,
                    latencyMs,
                    requestId,
                    TraceIdUtils.getTraceId());

            String body = entity.getBody();
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(body);
            TickerList list = parseTickerList(root, query);
            return Optional.ofNullable(list);
        } catch (RestClientResponseException ex) {
            long latencyMs = elapsedMillis(startedAt);
            int status = ex.getStatusCode().value();
            String requestId = extractRequestId(ex.getResponseHeaders());
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_TICKERS, status, latencyMs, false);
            log.warn(
                    "external_api_call vendor={} endpoint={} status={} latency_ms={} request_id={} trace_id={} error={}",
                    VENDOR,
                    ENDPOINT_TICKERS,
                    status,
                    latencyMs,
                    requestId,
                    TraceIdUtils.getTraceId(),
                    ex.getMessage());
            if (status == 429) {
                throw rateLimited("Fugle", "tickers", ex.getResponseHeaders());
            }
            log.error("Failed to fetch tickers from Fugle", ex);
            return Optional.empty();
        } catch (IOException ex) {
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_TICKERS, null, latencyMs, false);
            log.error("Failed to parse Fugle tickers response", ex);
            return Optional.empty();
        } catch (Exception ex) {
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_TICKERS, null, latencyMs, false);
            log.error("Failed to fetch tickers from Fugle", ex);
            return Optional.empty();
        }
    }

    public Optional<FugleTickerResponse> getTickerDetail(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return Optional.empty();
        }

        String symbol = ticker.trim();
        rateLimiter.acquire(VENDOR, ENDPOINT_TICKER_DETAIL);
        long startedAt = System.nanoTime();
        try {
            String url = buildUrl("/intraday/ticker", symbol);
            ResponseEntity<FugleTickerResponse> entity = withApiKey(restClient.get().uri(url))
                    .retrieve()
                    .toEntity(FugleTickerResponse.class);

            String requestId = extractRequestId(entity.getHeaders());
            int status = entity.getStatusCode().value();
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_TICKER_DETAIL, status, latencyMs, true);
            log.info(
                    "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={}",
                    VENDOR,
                    ENDPOINT_TICKER_DETAIL,
                    symbol,
                    status,
                    latencyMs,
                    requestId,
                    TraceIdUtils.getTraceId());

            return Optional.ofNullable(entity.getBody());
        } catch (RestClientResponseException ex) {
            long latencyMs = elapsedMillis(startedAt);
            int status = ex.getStatusCode().value();
            String requestId = extractRequestId(ex.getResponseHeaders());
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_TICKER_DETAIL, status, latencyMs, false);
            log.warn(
                    "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={} error={}",
                    VENDOR,
                    ENDPOINT_TICKER_DETAIL,
                    symbol,
                    status,
                    latencyMs,
                    requestId,
                    TraceIdUtils.getTraceId(),
                    ex.getMessage());
            if (status == 429) {
                throw rateLimited("Fugle", symbol, ex.getResponseHeaders());
            }
            log.error("Failed to fetch ticker detail from Fugle: {}", symbol, ex);
            return Optional.empty();
        } catch (Exception ex) {
            long latencyMs = elapsedMillis(startedAt);
            metricsRecorder.recordExternalCall(VENDOR, ENDPOINT_TICKER_DETAIL, null, latencyMs, false);
            log.error("Failed to fetch ticker detail from Fugle: {}", symbol, ex);
            return Optional.empty();
        }
    }

    @Override
    public MarketCode getSupportedMarket() {
        return MarketCode.TW;
    }

    private Optional<Quote> handleRestClientError(
            String ticker,
            String endpoint,
            long startedAt,
            RestClientResponseException ex,
            boolean quoteFlow) {
        long latencyMs = elapsedMillis(startedAt);
        int status = ex.getStatusCode().value();
        String requestId = extractRequestId(ex.getResponseHeaders());
        metricsRecorder.recordExternalCall(VENDOR, endpoint, status, latencyMs, false);
        log.warn(
                "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={} error={}",
                VENDOR,
                endpoint,
                ticker,
                status,
                latencyMs,
                requestId,
                TraceIdUtils.getTraceId(),
                ex.getMessage());
        if (status == 429) {
            throw rateLimited("Fugle", ticker, ex.getResponseHeaders());
        }
        if (quoteFlow) {
            log.error("Failed to fetch quote from Fugle for ticker: {}", ticker, ex);
        }
        return Optional.empty();
    }

    private List<Candle> handleRestClientErrorList(
            String ticker,
            String endpoint,
            String interval,
            long startedAt,
            RestClientResponseException ex) {
        long latencyMs = elapsedMillis(startedAt);
        int status = ex.getStatusCode().value();
        String requestId = extractRequestId(ex.getResponseHeaders());
        metricsRecorder.recordExternalCall(VENDOR, endpoint, status, latencyMs, false);
        log.warn(
                "external_api_call vendor={} endpoint={} ticker={} status={} latency_ms={} request_id={} trace_id={} interval={} error={}",
                VENDOR,
                endpoint,
                ticker,
                status,
                latencyMs,
                requestId,
                TraceIdUtils.getTraceId(),
                interval,
                ex.getMessage());
        if (status == 429) {
            throw rateLimited("Fugle", ticker, ex.getResponseHeaders());
        }
        log.error("Failed to fetch candles from Fugle for ticker: {}", ticker, ex);
        return List.of();
    }

    private boolean shouldUseHistorical(String interval, LocalDate from, LocalDate to) {
        if (interval != null) {
            String normalized = interval.trim().toLowerCase();
            if (normalized.equals("1d") || normalized.equals("1w") || normalized.equals("1mo")) {
                return true;
            }
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (from != null && from.isBefore(today.minusDays(1))) {
            return true;
        }
        return to != null && to.isBefore(today);
    }

    private boolean isWithinRange(LocalDateTime timestamp, LocalDate from, LocalDate to) {
        LocalDate date = timestamp.toLocalDate();
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ex) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private String buildUrl(String path, String ticker) {
        String base = properties.getFugle().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return String.format("%s%s/%s", base, path, ticker);
    }

    private String buildUrl(String path) {
        String base = properties.getFugle().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return String.format("%s%s", base, path);
    }

    private RestClient.RequestHeadersSpec<?> withApiKey(RestClient.RequestHeadersSpec<?> spec) {
        String apiKey = properties.getFugle().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return spec;
        }
        return spec.header(HEADER_API_KEY, apiKey.trim());
    }

    private void addQueryParam(UriComponentsBuilder builder, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.queryParam(name, value.trim());
    }

    private String toFlag(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "1" : "0";
    }

    private TickerList parseTickerList(JsonNode root, TickerQuery query) {
        if (root == null || root.isNull()) {
            return null;
        }

        List<TickerItem> items = extractItems(root).stream()
                .map(this::parseTickerItem)
                .filter(item -> item.getSymbol() != null && !item.getSymbol().isBlank())
                .collect(java.util.stream.Collectors.toList());

        String market = readText(root, "market");
        if (market == null && query != null) {
            market = query.getMarket();
        }

        return TickerList.builder()
                .date(readDate(root, "date"))
                .type(readText(root, "type", query != null ? query.getType() : null))
                .exchange(readText(root, "exchange", query != null ? query.getExchange() : null))
                .market(market)
                .industry(readText(root, "industry", query != null ? query.getIndustry() : null))
                .isNormal(readBoolean(root, "isNormal", query != null ? query.getIsNormal() : null))
                .isAttention(readBoolean(root, "isAttention", query != null ? query.getIsAttention() : null))
                .isDisposition(readBoolean(root, "isDisposition", query != null ? query.getIsDisposition() : null))
                .isHalted(readBoolean(root, "isHalted", query != null ? query.getIsHalted() : null))
                .data(items)
                .build();
    }

    private List<JsonNode> extractItems(JsonNode root) {
        if (root.has("data")) {
            JsonNode dataNode = root.get("data");
            if (dataNode.isArray()) {
                return toList(dataNode);
            }
            if (dataNode.isObject() && dataNode.has("data") && dataNode.get("data").isArray()) {
                return toList(dataNode.get("data"));
            }
        }
        if (root.has("items") && root.get("items").isArray()) {
            return toList(root.get("items"));
        }
        for (JsonNode field : root) {
            if (field.isArray()) {
                return toList(field);
            }
        }
        return List.of();
    }

    private List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        arrayNode.forEach(list::add);
        return list;
    }

    private TickerItem parseTickerItem(JsonNode node) {
        String symbol = readText(node, "symbol", readText(node, "ticker", readText(node, "code", null)));
        String name = readText(node, "name",
                readText(node, "nameZh",
                        readText(node, "nameEn",
                                readText(node, "cname",
                                        readText(node, "ename", symbol)))));
        return TickerItem.builder()
                .symbol(symbol)
                .name(name)
                .build();
    }

    private String readText(JsonNode node, String field) {
        return readText(node, field, null);
    }

    private String readText(JsonNode node, String field, String fallback) {
        if (node == null || field == null) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    private Boolean readBoolean(JsonNode node, String field, Boolean fallback) {
        if (node == null || field == null) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asInt() != 0;
        }
        if (value.isTextual()) {
            String text = value.asText().trim().toLowerCase();
            if ("1".equals(text) || "true".equals(text)) {
                return true;
            }
            if ("0".equals(text) || "false".equals(text)) {
                return false;
            }
        }
        return fallback;
    }

    private LocalDate readDate(JsonNode node, String field) {
        String text = readText(node, field);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String extractRequestId(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String requestId = headers.getFirst(HEADER_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return headers.getFirst("X-Request-Id");
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
            String requestId = extractRequestId(headers);
            if (requestId != null && !requestId.isBlank()) {
                details.put("requestId", requestId);
            }
        }
        return new BusinessException(ErrorCode.RATE_LIMITED, "External API rate limited", details);
    }
}
