package tw.bk.appai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tw.bk.appai.config.GroqProperties;
import tw.bk.appai.metrics.AiMetricsRecorder;

class GroqChatClientTest {

    @Test
    void usageOnlyStreamEvent_shouldRecordTokensWithoutEmittingText() throws Exception {
        GroqProperties properties = new GroqProperties();
        properties.setBaseUrl("http://localhost");
        properties.setModel("openai/gpt-oss-120b");
        ObjectMapper objectMapper = new ObjectMapper();
        AiMetricsRecorder recorder = org.mockito.Mockito.mock(AiMetricsRecorder.class);
        GroqChatClient client = new GroqChatClient(
                properties,
                objectMapper,
                WebClient.builder(),
                recorder);

        String event = """
                {
                  "choices": [],
                  "usage": {
                    "prompt_tokens": 120,
                    "completion_tokens": 30,
                    "prompt_tokens_details": {"cached_tokens": 40}
                  }
                }
                """;

        assertEquals(0, client.extractDeltas(event, "chat").count().block());
        verify(recorder).recordTokens(
                "groq", "openai/gpt-oss-120b", "chat", "input", 120);
        verify(recorder).recordTokens(
                "groq", "openai/gpt-oss-120b", "chat", "output", 30);
        verify(recorder).recordTokens(
                "groq", "openai/gpt-oss-120b", "chat", "cached", 40);
    }
}
