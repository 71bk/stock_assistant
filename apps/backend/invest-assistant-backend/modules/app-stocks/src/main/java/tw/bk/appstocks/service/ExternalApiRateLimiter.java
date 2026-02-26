package tw.bk.appstocks.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.ratelimit.RateLimiter;
import tw.bk.appstocks.config.StockMarketProperties;

@Service
@RequiredArgsConstructor
public class ExternalApiRateLimiter {
    private final StockMarketProperties properties;
    private final StockMetricsRecorder metricsRecorder;
    private final RateLimiter rateLimiter;

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
        String key = "stock:external:" + normalizedVendor + ":" + normalizedEndpoint;
        if (rateLimiter.tryAcquire(key, limit, Duration.ofMillis(windowMs))) {
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
