package tw.bk.appfiles.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.files")
public class FileStorageProperties {
    private String localPath = "./data/uploads";
}
