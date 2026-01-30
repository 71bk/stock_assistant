package tw.bk.appai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tw.bk.appai.config.GroqProperties;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

/**
 * Groq LLM API 客戶端。
 * 使用 WebClient 串流讀取 Groq Chat Completions API 的回應。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GroqChatClient {
    private final GroqProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 以串流方式呼叫 Groq Chat API。
     *
     * @param messages 聊天訊息列表，包含 role 和 content
     * @param userId   使用者 ID（用於 Groq 的 user 參數追蹤）
     * @return 串流回應的 Flux，每個元素為一個 delta content
     * @throws BusinessException 若 API 金鑰未設定
     */
    public Flux<String> streamChat(List<Map<String, String>> messages, Long userId) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Groq API key missing");
        }

        // 建立請求 payload
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

        WebClient client = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        log.debug("Sending Groq stream request, model={}", properties.getModel());

        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::extractDeltas)
                .filter(text -> text != null && !text.isBlank());
    }

    /**
     * 從 SSE chunk 中提取 delta content。
     * Groq 的 SSE 格式為 "data: {json}" 或 "data: [DONE]"
     */
    private Flux<String> extractDeltas(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return Flux.empty();
        }

        String[] lines = chunk.split("\\r?\\n");
        List<String> deltas = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isBlank() || "[DONE]".equals(data)) {
                continue;
            }
            String delta = parseDeltaContent(data);
            if (delta != null && !delta.isBlank()) {
                deltas.add(delta);
            }
        }
        return Flux.fromIterable(deltas);
    }

    /**
     * 解析 JSON 格式的 SSE data，提取 choices[0].delta.content。
     */
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
