package tw.bk.appbootstrap.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler(
            @Value("${app.scheduling.pool-size:8}") int poolSize,
            @Value("${app.scheduling.thread-name-prefix:app-scheduler-}") String threadNamePrefix,
            @Value("${app.scheduling.wait-for-tasks-to-complete-on-shutdown:true}") boolean waitForTasksOnShutdown,
            @Value("${app.scheduling.await-termination-seconds:30}") int awaitTerminationSeconds) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, poolSize));
        scheduler.setThreadNamePrefix(
                threadNamePrefix == null || threadNamePrefix.isBlank()
                        ? "app-scheduler-"
                        : threadNamePrefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(waitForTasksOnShutdown);
        scheduler.setAwaitTerminationSeconds(Math.max(0, awaitTerminationSeconds));
        scheduler.setErrorHandler(ex -> log.error("Scheduled task execution failed", ex));
        return scheduler;
    }
}
