package tw.bk.appanalytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tw.bk.appanalytics.config.AnalyticsProperties;
import tw.bk.appanalytics.model.AnalyticsModels.ApiTraffic;
import tw.bk.appanalytics.model.AnalyticsModels.AiUsage;

class PrometheusAnalyticsClientTest {

    private HttpServer server;
    private final List<String> instantQueries = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void queryTraffic_returnsUnavailableWhenBaseUrlBlank() {
        PrometheusAnalyticsClient client =
                new PrometheusAnalyticsClient(new AnalyticsProperties(), new ObjectMapper());

        ApiTraffic traffic = client.queryTraffic(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                OffsetDateTime.now(ZoneOffset.UTC));

        assertFalse(traffic.available());
        assertEquals("Prometheus 尚未設定", traffic.warning());
    }

    @Test
    void queryTraffic_anchorsInstantQueriesAtRangeEnd() throws IOException {
        startStubServer();
        PrometheusAnalyticsClient client = clientForServer();

        OffsetDateTime from = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2026, 6, 8, 0, 0, 0, 0, ZoneOffset.UTC);
        long expectedTime = to.toInstant().getEpochSecond();

        ApiTraffic traffic = client.queryTraffic(from, to);

        assertTrue(traffic.available());
        assertEquals(123, traffic.requestCount());
        assertFalse(instantQueries.isEmpty());
        // The #2 fix: scalar/instant queries must be evaluated at the range end, not "now".
        assertTrue(instantQueries.stream().allMatch(q -> q.contains("time=" + expectedTime)),
                "instant queries should carry time=" + expectedTime + " but were " + instantQueries);
    }

    @Test
    void queryTraffic_returnsUnavailableWhenPrometheusErrors() throws IOException {
        startErrorServer();
        PrometheusAnalyticsClient client = clientForServer();

        ApiTraffic traffic = client.queryTraffic(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                OffsetDateTime.now(ZoneOffset.UTC));

        assertFalse(traffic.available());
    }

    @Test
    void queryAiUsage_shouldAggregateTokensCallsAndFallbacks() throws IOException {
        startAiUsageStubServer();
        PrometheusAnalyticsClient client = clientForServer();

        AiUsage usage = client.queryAiUsage(
                OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 6, 8, 0, 0, 0, 0, ZoneOffset.UTC));

        assertTrue(usage.available());
        assertEquals(100, usage.inputTokens());
        assertEquals(40, usage.outputTokens());
        assertEquals(10, usage.cachedTokens());
        assertEquals(5, usage.callCount());
        assertEquals(1, usage.failedCallCount());
        assertEquals(20.0, usage.failureRatePercent());
        assertEquals(250.0, usage.p95DurationMs());
        assertEquals(2, usage.ocrFallbackCount());
        assertEquals(1, usage.ocrRetryCount());
        assertFalse(usage.byProvider().isEmpty());
        assertFalse(usage.models().isEmpty());
        assertFalse(usage.trend().isEmpty());
    }

    private PrometheusAnalyticsClient clientForServer() {
        AnalyticsProperties props = new AnalyticsProperties();
        props.getPrometheus().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        return new PrometheusAnalyticsClient(props, new ObjectMapper());
    }

    private void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/query", exchange -> {
            instantQueries.add(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, """
                    {"status":"success","data":{"resultType":"vector",
                    "result":[{"metric":{},"value":[1717804800,"123"]}]}}
                    """);
        });
        server.createContext("/api/v1/query_range", exchange -> respond(exchange, 200, """
                {"status":"success","data":{"resultType":"matrix",
                "result":[{"metric":{},"values":[[1717804800,"5"]]}]}}
                """));
        server.start();
    }

    private void startErrorServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 500, "boom"));
        server.start();
    }

    private void startAiUsageStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/query", exchange -> {
            String query = decodedQuery(exchange);
            String result;
            if (query.startsWith("sum by (type)")) {
                result = """
                        [
                          {"metric":{"type":"input"},"value":[1,"100"]},
                          {"metric":{"type":"output"},"value":[1,"40"]},
                          {"metric":{"type":"cached"},"value":[1,"10"]}
                        ]
                        """;
            } else if (query.startsWith("sum by (provider, type)")) {
                result = """
                        [
                          {"metric":{"provider":"groq","type":"input"},"value":[1,"100"]},
                          {"metric":{"provider":"gemini","type":"output"},"value":[1,"40"]}
                        ]
                        """;
            } else if (query.startsWith("sum by (operation, type)")) {
                result = """
                        [
                          {"metric":{"operation":"chat","type":"input"},"value":[1,"100"]},
                          {"metric":{"operation":"ocr_parse","type":"output"},"value":[1,"40"]}
                        ]
                        """;
            } else if (query.startsWith("sum by (provider, model, operation, type)")) {
                result = """
                        [
                          {"metric":{"provider":"groq","model":"test","operation":"chat","type":"input"},
                           "value":[1,"100"]}
                        ]
                        """;
            } else if (query.startsWith("sum by (provider, success)")) {
                result = """
                        [{"metric":{"provider":"groq","success":"true"},"value":[1,"4"]}]
                        """;
            } else if (query.startsWith("sum by (operation, success)")) {
                result = """
                        [{"metric":{"operation":"chat","success":"true"},"value":[1,"4"]}]
                        """;
            } else if (query.startsWith("sum by (provider, model, operation, success)")) {
                result = """
                        [{"metric":{"provider":"groq","model":"test","operation":"chat","success":"true"},
                          "value":[1,"4"]}]
                        """;
            } else if (query.contains("success=\"false\"")) {
                result = scalarResult("1");
            } else if (query.contains("ai_calls_total")) {
                result = scalarResult("5");
            } else if (query.contains("histogram_quantile")) {
                result = scalarResult("0.25");
            } else if (query.contains("ocr_fallback_total")) {
                result = scalarResult("2");
            } else if (query.contains("ocr_retry_total")) {
                result = scalarResult("1");
            } else if (query.contains("embedding_retry_total")) {
                result = scalarResult("3");
            } else if (query.contains("embedding_batch_fallback_total")) {
                result = scalarResult("4");
            } else {
                result = "[]";
            }
            respond(exchange, 200, successResponse(result));
        });
        server.createContext("/api/v1/query_range", exchange -> respond(exchange, 200, """
                {"status":"success","data":{"resultType":"matrix","result":[
                  {"metric":{"type":"input"},"values":[[1,"10"]]},
                  {"metric":{"type":"output"},"values":[[1,"5"]]}
                ]}}
                """));
        server.start();
    }

    private static String decodedQuery(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        for (String part : raw.split("&")) {
            if (part.startsWith("query=")) {
                return URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static String scalarResult(String value) {
        return "[{\"metric\":{},\"value\":[1,\"" + value + "\"]}]";
    }

    private static String successResponse(String result) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":"
                + result + "}}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
