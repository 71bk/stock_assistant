package tw.bk.appapi.ai.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiAsyncConfig {

    @Bean(name = "aiSseExecutor")
    public Executor aiSseExecutor() {
        return buildExecutor("ai-sse-");
    }

    @Bean(name = "aiSkillExecutor")
    public Executor aiSkillExecutor() {
        return buildExecutor("ai-skill-");
    }

    private Executor buildExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
