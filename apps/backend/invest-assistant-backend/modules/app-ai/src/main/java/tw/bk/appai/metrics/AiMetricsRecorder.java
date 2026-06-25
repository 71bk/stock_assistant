package tw.bk.appai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AiMetricsRecorder {
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public AiMetricsRecorder(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    public void recordTokens(
            String provider,
            String model,
            String operation,
            String type,
            long tokens) {
        if (tokens <= 0) {
            return;
        }
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Counter.builder("ai_tokens")
                .description("AI provider token usage")
                .tags(
                        "provider", normalize(provider),
                        "model", normalize(model),
                        "operation", normalize(operation),
                        "type", normalize(type))
                .register(registry)
                .increment(tokens);
    }

    public void recordCall(
            String provider,
            String model,
            String operation,
            boolean success,
            long durationNanos) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        String normalizedProvider = normalize(provider);
        String normalizedModel = normalize(model);
        String normalizedOperation = normalize(operation);
        Counter.builder("ai_calls")
                .description("AI provider calls")
                .tags(
                        "provider", normalizedProvider,
                        "model", normalizedModel,
                        "operation", normalizedOperation,
                        "success", Boolean.toString(success))
                .register(registry)
                .increment();
        Timer.builder("ai_call_duration")
                .description("AI provider call duration")
                .serviceLevelObjectives(
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(50),
                        Duration.ofMillis(75),
                        Duration.ofMillis(100),
                        Duration.ofMillis(250),
                        Duration.ofMillis(500),
                        Duration.ofMillis(750),
                        Duration.ofSeconds(1),
                        Duration.ofMillis(2500),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(7500),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(120),
                        Duration.ofSeconds(300))
                .tags(
                        "provider", normalizedProvider,
                        "model", normalizedModel,
                        "operation", normalizedOperation)
                .register(registry)
                .record(Duration.ofNanos(Math.max(0, durationNanos)));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
