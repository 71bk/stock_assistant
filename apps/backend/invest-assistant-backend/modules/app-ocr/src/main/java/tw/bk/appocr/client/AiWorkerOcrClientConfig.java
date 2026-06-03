package tw.bk.appocr.client;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AiWorkerOcrClientConfig {

    @Bean
    public WebClient aiWorkerOcrWebClient(AiWorkerProperties properties, WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout(properties));
        return builder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private Duration timeout(AiWorkerProperties properties) {
        int timeoutSeconds = properties.getTimeoutSeconds() != null ? properties.getTimeoutSeconds() : 120;
        return Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }
}
