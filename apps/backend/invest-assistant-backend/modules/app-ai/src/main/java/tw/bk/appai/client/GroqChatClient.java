package tw.bk.appai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.core.publisher.SignalType;
import tw.bk.appai.config.GroqProperties;
import tw.bk.appai.metrics.AiMetricsRecorder;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

@Component
@Slf4j
public class GroqChatClient {
    private final GroqProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final AiMetricsRecorder metricsRecorder;

    public GroqChatClient(
            GroqProperties properties,
            ObjectMapper objectMapper,
            WebClient.Builder builder,
            AiMetricsRecorder metricsRecorder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metricsRecorder = metricsRecorder;
        int timeoutSeconds = properties.getTimeoutSeconds() != null ? properties.getTimeoutSeconds() : 60;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));
        this.webClient = builder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Flux<String> streamChat(
            List<Map<String, String>> messages,
            Long userId,
            String operation) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Groq API key missing");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", messages);
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));

        if (properties.getTemperature() != null) {
            payload.put("temperature", properties.getTemperature());
        }
        if (properties.getMaxCompletionTokens() != null) {
            payload.put("max_completion_tokens", properties.getMaxCompletionTokens());
        }
        if (properties.getTopP() != null) {
            payload.put("top_p", properties.getTopP());
        }
        if (userId != null) {
            payload.put("user", String.valueOf(userId));
        }

        log.debug("Sending Groq stream request, model={}", properties.getModel());

        return Flux.defer(() -> {
            long startedAt = System.nanoTime();
            AtomicBoolean failed = new AtomicBoolean(false);
            return webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                    })
                    .map(ServerSentEvent::data)
                    .filter(data -> data != null && !data.isBlank())
                    .flatMap(data -> extractDeltas(data, operation))
                    .filter(text -> text != null && !text.isBlank())
                    .doOnError(error -> failed.set(true))
                    .doFinally(signalType -> metricsRecorder.recordCall(
                            "groq",
                            properties.getModel(),
                            operation,
                            !failed.get() && signalType == SignalType.ON_COMPLETE,
                            System.nanoTime() - startedAt));
        });
    }

    Flux<String> extractDeltas(String data, String operation) {
        if (data == null || data.isBlank() || "[DONE]".equals(data)) {
            return Flux.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            recordUsage(root.path("usage"), operation);
            String delta = parseDeltaContent(root);
            if (delta == null || delta.isBlank()) {
                return Flux.empty();
            }
            return Flux.just(delta);
        } catch (Exception ex) {
            log.warn("Failed to parse Groq stream event: {}", ex.getMessage());
            return Flux.empty();
        }
    }

    void recordUsage(JsonNode usage, String operation) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return;
        }
        metricsRecorder.recordTokens(
                "groq", properties.getModel(), operation, "input",
                usage.path("prompt_tokens").asLong(0));
        metricsRecorder.recordTokens(
                "groq", properties.getModel(), operation, "output",
                usage.path("completion_tokens").asLong(0));
        long cachedTokens = usage.path("prompt_tokens_details").path("cached_tokens").asLong(0);
        if (cachedTokens == 0) {
            cachedTokens = usage.path("cached_tokens").asLong(0);
        }
        metricsRecorder.recordTokens(
                "groq", properties.getModel(), operation, "cached", cachedTokens);
    }

    private String parseDeltaContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode delta = choices.get(0).path("delta");
        JsonNode content = delta.get("content");
        if (content == null || content.isNull()) {
            return null;
        }
        return content.asText();
    }
}
