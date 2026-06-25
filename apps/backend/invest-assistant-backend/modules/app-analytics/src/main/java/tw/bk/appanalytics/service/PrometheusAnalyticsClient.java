package tw.bk.appanalytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import tw.bk.appanalytics.config.AnalyticsProperties;
import tw.bk.appanalytics.model.AnalyticsModels.AiModelUsage;
import tw.bk.appanalytics.model.AnalyticsModels.AiTokenTrendPoint;
import tw.bk.appanalytics.model.AnalyticsModels.AiUsage;
import tw.bk.appanalytics.model.AnalyticsModels.AiUsageBreakdown;
import tw.bk.appanalytics.model.AnalyticsModels.ApiEndpointMetric;
import tw.bk.appanalytics.model.AnalyticsModels.ApiTraffic;
import tw.bk.appanalytics.model.AnalyticsModels.ApiTrafficPoint;

@Component
public class PrometheusAnalyticsClient {
    private static final Logger log = LoggerFactory.getLogger(PrometheusAnalyticsClient.class);
    private static final String REQUEST_FILTER =
            "uri!~\"/actuator.*|/analytics/events|/admin/analytics.*\"";

    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PrometheusAnalyticsClient(AnalyticsProperties properties, ObjectMapper objectMapper) {
        String configured = properties.getPrometheus().getBaseUrl();
        this.baseUrl = configured == null ? "" : configured.trim().replaceAll("/+$", "");
        this.objectMapper = objectMapper;
        // Built once and reused; null when Prometheus is not configured (never used in that case).
        this.restClient = this.baseUrl.isBlank() ? null : buildRestClient(this.baseUrl);
    }

    private static RestClient buildRestClient(String baseUrl) {
        // SimpleClientHttpRequestFactory + bounded timeouts so a slow/hung Prometheus
        // cannot stall the admin analytics request (it falls through to "unavailable").
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        // PromQL contains {, }, ", | which the default template encoder mistakes for URI
        // template variables. Disable builder encoding and pre-encode query values ourselves.
        DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory(baseUrl);
        uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        return RestClient.builder()
                .uriBuilderFactory(uriFactory)
                .requestFactory(factory)
                .build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public ApiTraffic queryTraffic(OffsetDateTime from, OffsetDateTime to) {
        if (baseUrl.isBlank()) {
            return ApiTraffic.unavailable("Prometheus 尚未設定");
        }

        try {
            long rangeSeconds = Math.max(60, Duration.between(from, to).toSeconds());
            String range = rangeSeconds + "s";
            // Instant queries are evaluated AT the range end (to), looking back `range`,
            // so the scalar cards cover the selected window instead of "now".
            long requestCount = Math.round(queryScalar(
                    "sum(increase(http_server_requests_seconds_count{" + REQUEST_FILTER + "}[" + range + "]))", to));
            long clientErrors = Math.round(queryScalar(
                    "sum(increase(http_server_requests_seconds_count{" + REQUEST_FILTER
                            + ",status=~\"4..\"}[" + range + "]))", to));
            long serverErrors = Math.round(queryScalar(
                    "sum(increase(http_server_requests_seconds_count{" + REQUEST_FILTER
                            + ",status=~\"5..\"}[" + range + "]))", to));
            double p95Seconds = queryScalar(
                    "histogram_quantile(0.95, sum by (le) (increase(http_server_requests_seconds_bucket{"
                            + REQUEST_FILTER + "}[" + range + "])))", to);
            double errorRate = requestCount == 0
                    ? 0
                    : (clientErrors + serverErrors) * 100.0 / requestCount;

            long stepSeconds = selectStepSeconds(rangeSeconds);
            List<ApiTrafficPoint> trend = queryRange(
                    "sum(increase(http_server_requests_seconds_count{" + REQUEST_FILTER
                            + "}[" + stepSeconds + "s]))",
                    from,
                    to,
                    stepSeconds);
            List<ApiEndpointMetric> endpoints = queryTopEndpoints(range, to);

            return new ApiTraffic(
                    true,
                    requestCount,
                    clientErrors,
                    serverErrors,
                    round(errorRate),
                    round(p95Seconds * 1000.0),
                    endpoints,
                    trend,
                    null);
        } catch (Exception ex) {
            log.warn("Prometheus analytics query failed: {}", ex.getMessage());
            return ApiTraffic.unavailable("Prometheus 暫時無法查詢");
        }
    }

    public AiUsage queryAiUsage(OffsetDateTime from, OffsetDateTime to) {
        if (baseUrl.isBlank()) {
            return AiUsage.unavailable("Prometheus 尚未設定");
        }

        try {
            long rangeSeconds = Math.max(60, Duration.between(from, to).toSeconds());
            String range = rangeSeconds + "s";
            Map<String, Long> tokenTotals = querySingleLabelTotals(
                    "sum by (type) (increase(ai_tokens_total[" + range + "]))",
                    "type",
                    to);
            long calls = Math.round(queryScalar(
                    "sum(increase(ai_calls_total[" + range + "]))",
                    to));
            long failedCalls = Math.round(queryScalar(
                    "sum(increase(ai_calls_total{success=\"false\"}[" + range + "]))",
                    to));
            double p95Seconds = queryScalar(
                    "histogram_quantile(0.95, sum by (le) "
                            + "(increase(ai_call_duration_seconds_bucket[" + range + "])))",
                    to);
            long ocrFallbacks = Math.round(queryScalar(
                    "sum(increase(ocr_fallback_total[" + range + "]))",
                    to));
            long ocrRetries = Math.round(queryScalar(
                    "sum(increase(ocr_retry_total[" + range + "]))",
                    to));
            long embeddingRetries = Math.round(queryScalar(
                    "sum(increase(embedding_retry_total[" + range + "]))",
                    to));
            long embeddingBatchFallbacks = Math.round(queryScalar(
                    "sum(increase(embedding_batch_fallback_total[" + range + "]))",
                    to));

            List<AiUsageBreakdown> byProvider = queryUsageBreakdown("provider", range, to);
            List<AiUsageBreakdown> byOperation = queryUsageBreakdown("operation", range, to);
            List<AiModelUsage> models = queryModelUsage(range, to);
            long stepSeconds = selectStepSeconds(rangeSeconds);
            List<AiTokenTrendPoint> trend = queryAiTokenTrend(
                    from,
                    to,
                    stepSeconds);
            double failureRate = calls == 0 ? 0 : failedCalls * 100.0 / calls;

            return new AiUsage(
                    true,
                    tokenTotals.getOrDefault("input", 0L),
                    tokenTotals.getOrDefault("output", 0L),
                    tokenTotals.getOrDefault("cached", 0L),
                    calls,
                    failedCalls,
                    round(failureRate),
                    round(p95Seconds * 1000.0),
                    ocrFallbacks,
                    ocrRetries,
                    embeddingRetries,
                    embeddingBatchFallbacks,
                    byProvider,
                    byOperation,
                    models,
                    trend,
                    null);
        } catch (Exception ex) {
            log.warn("Prometheus AI usage query failed: {}", ex.getMessage());
            return AiUsage.unavailable("AI 用量暫時無法查詢");
        }
    }

    private Map<String, Long> querySingleLabelTotals(
            String query,
            String label,
            OffsetDateTime evalTime) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (JsonNode item : queryVector(query, evalTime)) {
            String key = item.path("metric").path(label).asText("unknown");
            values.put(key, Math.round(readInstantValue(item)));
        }
        return values;
    }

    private List<AiUsageBreakdown> queryUsageBreakdown(
            String groupLabel,
            String range,
            OffsetDateTime evalTime) {
        Map<String, UsageAccumulator> values = new LinkedHashMap<>();
        String tokenQuery = "sum by (" + groupLabel + ", type) "
                + "(increase(ai_tokens_total[" + range + "]))";
        for (JsonNode item : queryVector(tokenQuery, evalTime)) {
            JsonNode metric = item.path("metric");
            String key = metric.path(groupLabel).asText("unknown");
            String type = metric.path("type").asText("unknown");
            values.computeIfAbsent(key, ignored -> new UsageAccumulator())
                    .addTokens(type, Math.round(readInstantValue(item)));
        }

        String callQuery = "sum by (" + groupLabel + ", success) "
                + "(increase(ai_calls_total[" + range + "]))";
        for (JsonNode item : queryVector(callQuery, evalTime)) {
            JsonNode metric = item.path("metric");
            String key = metric.path(groupLabel).asText("unknown");
            boolean success = Boolean.parseBoolean(metric.path("success").asText("false"));
            values.computeIfAbsent(key, ignored -> new UsageAccumulator())
                    .addCalls(success, Math.round(readInstantValue(item)));
        }

        return values.entrySet().stream()
                .map(entry -> entry.getValue().toBreakdown(entry.getKey()))
                .sorted(Comparator.comparingLong(AiUsageBreakdown::totalTokens).reversed()
                        .thenComparing(AiUsageBreakdown::key))
                .toList();
    }

    private List<AiModelUsage> queryModelUsage(String range, OffsetDateTime evalTime) {
        Map<ModelKey, UsageAccumulator> values = new LinkedHashMap<>();
        String tokenQuery = "sum by (provider, model, operation, type) "
                + "(increase(ai_tokens_total[" + range + "]))";
        for (JsonNode item : queryVector(tokenQuery, evalTime)) {
            JsonNode metric = item.path("metric");
            ModelKey key = ModelKey.from(metric);
            values.computeIfAbsent(key, ignored -> new UsageAccumulator())
                    .addTokens(
                            metric.path("type").asText("unknown"),
                            Math.round(readInstantValue(item)));
        }

        String callQuery = "sum by (provider, model, operation, success) "
                + "(increase(ai_calls_total[" + range + "]))";
        for (JsonNode item : queryVector(callQuery, evalTime)) {
            JsonNode metric = item.path("metric");
            ModelKey key = ModelKey.from(metric);
            values.computeIfAbsent(key, ignored -> new UsageAccumulator())
                    .addCalls(
                            Boolean.parseBoolean(metric.path("success").asText("false")),
                            Math.round(readInstantValue(item)));
        }

        return values.entrySet().stream()
                .map(entry -> entry.getValue().toModelUsage(entry.getKey()))
                .sorted(Comparator.comparingLong(AiModelUsage::totalTokens).reversed()
                        .thenComparing(AiModelUsage::provider)
                        .thenComparing(AiModelUsage::model)
                        .thenComparing(AiModelUsage::operation))
                .toList();
    }

    private List<AiTokenTrendPoint> queryAiTokenTrend(
            OffsetDateTime from,
            OffsetDateTime to,
            long stepSeconds) {
        String query = "sum by (type) (increase(ai_tokens_total[" + stepSeconds + "s]))";
        String uri = "/api/v1/query_range?query=" + encode(query)
                + "&start=" + from.toInstant().getEpochSecond()
                + "&end=" + to.toInstant().getEpochSecond()
                + "&step=" + stepSeconds;
        JsonNode response = getJson(uri);
        ensureSuccessful(response);

        Map<Long, TokenTrendAccumulator> points = new TreeMap<>();
        JsonNode result = response.path("data").path("result");
        if (!result.isArray()) {
            return List.of();
        }
        for (JsonNode series : result) {
            String type = series.path("metric").path("type").asText("unknown");
            JsonNode values = series.path("values");
            if (!values.isArray()) {
                continue;
            }
            for (JsonNode value : values) {
                if (!value.isArray() || value.size() < 2) {
                    continue;
                }
                long epochSeconds = value.get(0).asLong();
                points.computeIfAbsent(epochSeconds, ignored -> new TokenTrendAccumulator())
                        .add(type, parseNumber(value.get(1).asText()));
            }
        }
        return points.entrySet().stream()
                .map(entry -> entry.getValue().toPoint(entry.getKey()))
                .toList();
    }

    private List<ApiEndpointMetric> queryTopEndpoints(String range, OffsetDateTime evalTime) {
        String query = "topk(10, sum by (method, uri) "
                + "(increase(http_server_requests_seconds_count{" + REQUEST_FILTER + "}[" + range + "])))";
        JsonNode result = queryInstant(query, evalTime).path("data").path("result");
        List<ApiEndpointMetric> endpoints = new ArrayList<>();
        if (!result.isArray()) {
            return endpoints;
        }
        for (JsonNode item : result) {
            JsonNode metric = item.path("metric");
            endpoints.add(new ApiEndpointMetric(
                    metric.path("method").asText(""),
                    metric.path("uri").asText("UNKNOWN"),
                    Math.round(readInstantValue(item))));
        }
        return endpoints;
    }

    private double queryScalar(String query, OffsetDateTime evalTime) {
        JsonNode result = queryVector(query, evalTime);
        if (!result.isArray() || result.isEmpty()) {
            return 0;
        }
        return readInstantValue(result.get(0));
    }

    private JsonNode queryVector(String query, OffsetDateTime evalTime) {
        return queryInstant(query, evalTime).path("data").path("result");
    }

    private List<ApiTrafficPoint> queryRange(
            String query,
            OffsetDateTime from,
            OffsetDateTime to,
            long stepSeconds) {
        String uri = "/api/v1/query_range?query=" + encode(query)
                + "&start=" + from.toInstant().getEpochSecond()
                + "&end=" + to.toInstant().getEpochSecond()
                + "&step=" + stepSeconds;
        JsonNode response = getJson(uri);
        ensureSuccessful(response);

        JsonNode result = response.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }

        List<ApiTrafficPoint> points = new ArrayList<>();
        JsonNode values = result.get(0).path("values");
        if (!values.isArray()) {
            return points;
        }
        for (JsonNode value : values) {
            if (!value.isArray() || value.size() < 2) {
                continue;
            }
            long epochSeconds = value.get(0).asLong();
            double count = parseNumber(value.get(1).asText());
            points.add(new ApiTrafficPoint(
                    OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC),
                    round(count)));
        }
        return points;
    }

    private JsonNode queryInstant(String query, OffsetDateTime evalTime) {
        String uri = "/api/v1/query?query=" + encode(query)
                + "&time=" + evalTime.toInstant().getEpochSecond();
        JsonNode response = getJson(uri);
        ensureSuccessful(response);
        return response;
    }

    /**
     * Read the response as a String and parse with the shared Jackson 2 ObjectMapper,
     * avoiding RestClient message-converter type mismatches on JsonNode.
     */
    private JsonNode getJson(String uri) {
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty Prometheus response");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid Prometheus response", ex);
        }
    }

    private void ensureSuccessful(JsonNode response) {
        if (response == null || !"success".equals(response.path("status").asText())) {
            throw new IllegalStateException("Prometheus returned an unsuccessful response");
        }
    }

    private double readInstantValue(JsonNode item) {
        JsonNode value = item.path("value");
        if (!value.isArray() || value.size() < 2) {
            return 0;
        }
        return parseNumber(value.get(1).asText());
    }

    private double parseNumber(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private long selectStepSeconds(long rangeSeconds) {
        if (rangeSeconds <= Duration.ofDays(2).toSeconds()) {
            return Duration.ofHours(1).toSeconds();
        }
        if (rangeSeconds <= Duration.ofDays(14).toSeconds()) {
            return Duration.ofHours(6).toSeconds();
        }
        return Duration.ofDays(1).toSeconds();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class UsageAccumulator {
        private long inputTokens;
        private long outputTokens;
        private long cachedTokens;
        private long calls;
        private long failedCalls;

        private void addTokens(String type, long value) {
            switch (type) {
                case "input" -> inputTokens += value;
                case "output" -> outputTokens += value;
                case "cached" -> cachedTokens += value;
                default -> {
                    // Ignore unknown token types so the response contract remains stable.
                }
            }
        }

        private void addCalls(boolean success, long value) {
            calls += value;
            if (!success) {
                failedCalls += value;
            }
        }

        private AiUsageBreakdown toBreakdown(String key) {
            return new AiUsageBreakdown(
                    key,
                    inputTokens,
                    outputTokens,
                    cachedTokens,
                    calls,
                    failedCalls);
        }

        private AiModelUsage toModelUsage(ModelKey key) {
            return new AiModelUsage(
                    key.provider(),
                    key.model(),
                    key.operation(),
                    inputTokens,
                    outputTokens,
                    cachedTokens,
                    calls,
                    failedCalls);
        }
    }

    private record ModelKey(String provider, String model, String operation) {
        private static ModelKey from(JsonNode metric) {
            return new ModelKey(
                    metric.path("provider").asText("unknown"),
                    metric.path("model").asText("unknown"),
                    metric.path("operation").asText("unknown"));
        }
    }

    private static final class TokenTrendAccumulator {
        private double inputTokens;
        private double outputTokens;
        private double cachedTokens;

        private void add(String type, double value) {
            switch (type) {
                case "input" -> inputTokens += value;
                case "output" -> outputTokens += value;
                case "cached" -> cachedTokens += value;
                default -> {
                    // Ignore unknown token types.
                }
            }
        }

        private AiTokenTrendPoint toPoint(long epochSeconds) {
            return new AiTokenTrendPoint(
                    OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC),
                    inputTokens,
                    outputTokens,
                    cachedTokens);
        }
    }
}
