package tw.bk.appocr.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ocr.ai-worker")
public class AiWorkerProperties {
    private String baseUrl = "http://localhost:8001";
}
