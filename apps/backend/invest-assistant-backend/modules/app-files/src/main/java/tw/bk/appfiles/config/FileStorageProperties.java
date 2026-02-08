package tw.bk.appfiles.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.files")
public class FileStorageProperties {
    private String provider = "local";
    private String localPath = "./data/uploads";
    private String bucket;
    private String region = "us-east-1";
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private Integer presignExpirySeconds = 900;
}
