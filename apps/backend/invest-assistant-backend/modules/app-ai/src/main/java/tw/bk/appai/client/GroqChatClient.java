package tw.bk.appai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import tw.bk.appai.config.GroqProperties;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

@Component
@Slf4j
public class GroqChatClient {
    private final GroqProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public GroqChatClient(GroqProperties properties, ObjectMapper objectMapper, WebClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        int timeoutSeconds = properties.getTimeoutSeconds() != null ? properties.getTimeoutSeconds() : 60;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));
        this.webClient = builder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Flux<String> streamChat(List<Map<String, String>> messages, Long userId) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Groq API key missing");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", messages);
        payload.put("stream", true);

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
                .flatMap(this::extractDeltas)
                .filter(text -> text != null && !text.isBlank());
    }

    private Flux<String> extractDeltas(String data) {
        if (data == null || data.isBlank() || "[DONE]".equals(data)) {
            return Flux.empty();
        }

        String delta = parseDeltaContent(data);
        if (delta == null || delta.isBlank()) {
            return Flux.empty();
        }
        return Flux.just(delta);
    }

    private String parseDeltaContent(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
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
        } catch (Exception ex) {
            log.warn("Failed to parse Groq delta: {}", ex.getMessage());
            return null;
        }
    }
}
