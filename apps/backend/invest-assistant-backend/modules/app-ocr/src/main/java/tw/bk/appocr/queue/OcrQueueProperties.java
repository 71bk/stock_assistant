package tw.bk.appocr.queue;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ocr.queue")
public class OcrQueueProperties {
    private String streamKey = "ocr:queue";
    private String group = "ocr-workers";
    private String consumer = "ocr-worker-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    private int batchSize = 10;
    private Duration block = Duration.ofSeconds(2);
    private long pollIntervalMs = 1000;
}
