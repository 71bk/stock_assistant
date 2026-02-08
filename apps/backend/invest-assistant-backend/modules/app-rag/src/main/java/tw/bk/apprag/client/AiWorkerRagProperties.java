package tw.bk.apprag.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag.ai-worker")
public class AiWorkerRagProperties {
    private String baseUrl = "http://localhost:8001";
}
