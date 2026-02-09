package tw.bk.appstocks.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appstocks.config.StockMarketProperties;

@Service
@RequiredArgsConstructor
public class ExternalApiRateLimiter {
    private static final class Window {
        private long resetAt;
        private int count;

        private Window(long resetAt) {
            this.resetAt = resetAt;
        }
    }

    private final StockMarketProperties properties;
    private final StockMetricsRecorder metricsRecorder;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void acquire(String vendor, String endpoint) {
        StockMarketProperties.RateLimit config = properties.getRateLimit();
        if (config == null || !config.isEnabled()) {
            return;
        }

        long windowMs = config.getWindowMs();
        if (windowMs <= 0L) {
            return;
        }

        int limit = resolveLimit(vendor, config);
        if (limit <= 0) {
            return;
        }

        String normalizedVendor = normalize(vendor);
        String normalizedEndpoint = normalize(endpoint);
        String key = normalizedVendor + ":" + normalizedEndpoint;
        if (tryAcquire(key, limit, windowMs)) {
            return;
        }

        metricsRecorder.recordRateLimitBlocked(normalizedVendor, normalizedEndpoint);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("vendor", normalizedVendor);
        details.put("endpoint", normalizedEndpoint);
        details.put("limit", limit);
        details.put("windowMs", windowMs);
        throw new BusinessException(
                ErrorCode.RATE_LIMITED,
                "External API rate limit exceeded",
                details);
    }

    private int resolveLimit(String vendor, StockMarketProperties.RateLimit config) {
        String normalized = normalize(vendor);
        return switch (normalized) {
            case "alpaca" -> config.getAlpacaLimit();
            case "fugle" -> config.getFugleLimit();
            default -> 0;
        };
    }

    private boolean tryAcquire(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now + windowMs));
        synchronized (window) {
            if (now >= window.resetAt) {
                window.resetAt = now + windowMs;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count += 1;
            return true;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
