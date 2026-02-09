package tw.bk.appstocks.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockMetricsRecorder {
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public void recordCacheHit(String type) {
        increment("stock.cache.requests", "type", type, "result", "hit");
    }

    public void recordCacheMiss(String type) {
        increment("stock.cache.requests", "type", type, "result", "miss");
    }

    public void recordSingleFlightJoined(String type) {
        increment("stock.singleflight.joined", "type", type);
    }

    public void recordRateLimitBlocked(String vendor, String endpoint) {
        increment("stock.external.ratelimit.blocked", "vendor", vendor, "endpoint", endpoint);
    }

    public void recordExternalCall(
            String vendor,
            String endpoint,
            Integer status,
            long latencyMs,
            boolean success) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }

        String statusTag = status == null ? "NA" : String.valueOf(status);
        String successTag = success ? "true" : "false";

        Counter.builder("stock.external.calls")
                .tag("vendor", vendor)
                .tag("endpoint", endpoint)
                .tag("status", statusTag)
                .tag("success", successTag)
                .register(registry)
                .increment();

        Timer.builder("stock.external.latency")
                .tag("vendor", vendor)
                .tag("endpoint", endpoint)
                .tag("status", statusTag)
                .tag("success", successTag)
                .register(registry)
                .record(Math.max(latencyMs, 0L), TimeUnit.MILLISECONDS);
    }

    private void increment(String meterName, String... tags) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Counter.builder(meterName)
                .tags(tags)
                .register(registry)
                .increment();
    }
}
