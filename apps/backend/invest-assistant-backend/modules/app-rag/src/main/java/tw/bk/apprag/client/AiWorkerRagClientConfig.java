package tw.bk.apprag.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiWorkerRagClientConfig {

    @Bean
    public WebClient aiWorkerRagWebClient(AiWorkerRagProperties properties, WebClient.Builder builder) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }
}
