package tw.bk.appapi.security;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tw.bk.appcommon.ratelimit.RateLimiter;

/**
 * Simple in-memory fixed-window rate limiter.
 */
@Slf4j
@Component
public class SimpleRateLimiter implements RateLimiter {
    private static final int DEFAULT_CLEANUP_INTERVAL = 256;
    private static final String DEFAULT_KEY = "__default__";
    private static final String DEFAULT_PROVIDER = "memory";
    private static final String REDIS_PROVIDER = "redis";
    private static final String DEFAULT_REDIS_KEY_PREFIX = "rate-limit:";
    private static final String REDIS_WINDOW_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if current > tonumber(ARGV[2]) then
              return 0
            end
            return 1
            """;
    private static final DefaultRedisScript<Long> REDIS_FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>(
            REDIS_WINDOW_SCRIPT,
            Long.class);

    private static final class Window {
        private long resetAt;
        private int count;

        private Window(long resetAt) {
            this.resetAt = resetAt;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier nowSupplier;
    private final int cleanupInterval;
    private final AtomicInteger acquireCounter = new AtomicInteger();
    private final AtomicBoolean redisFallbackLogged = new AtomicBoolean(false);

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Value("${app.rate-limit.provider:memory}")
    private String provider = DEFAULT_PROVIDER;

    @Value("${app.rate-limit.redis.key-prefix:rate-limit:}")
    private String redisKeyPrefix = DEFAULT_REDIS_KEY_PREFIX;

    @Value("${app.rate-limit.redis.fail-open:true}")
    private boolean redisFailOpen = true;

    public SimpleRateLimiter() {
        this(System::currentTimeMillis, DEFAULT_CLEANUP_INTERVAL);
    }

    SimpleRateLimiter(LongSupplier nowSupplier, int cleanupInterval) {
        this.nowSupplier = nowSupplier == null ? System::currentTimeMillis : nowSupplier;
        this.cleanupInterval = cleanupInterval <= 0 ? DEFAULT_CLEANUP_INTERVAL : cleanupInterval;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        long windowMs = window == null ? 0L : window.toMillis();
        if (windowMs <= 0L) {
            return true;
        }

        String normalizedKey = normalizeKey(key);
        if (isRedisProviderEnabled()) {
            Boolean redisAllowed = tryAcquireFromRedis(normalizedKey, limit, windowMs);
            if (redisAllowed != null) {
                return redisAllowed;
            }
            if (!redisFailOpen) {
                return false;
            }
        }
        return tryAcquireInMemory(normalizedKey, limit, windowMs);
    }

    int windowCount() {
        return windows.size();
    }

    private boolean tryAcquireInMemory(String normalizedKey, int limit, long windowMs) {
        long now = nowSupplier.getAsLong();
        Window entry = windows.computeIfAbsent(normalizedKey, k -> new Window(now + windowMs));
        boolean allowed;
        synchronized (entry) {
            if (now >= entry.resetAt) {
                entry.resetAt = now + windowMs;
                entry.count = 0;
            }
            if (entry.count >= limit) {
                allowed = false;
            } else {
                entry.count += 1;
                allowed = true;
            }
        }
        maybeCleanup(now);
        return allowed;
    }

    private boolean isRedisProviderEnabled() {
        String normalizedProvider = provider == null ? DEFAULT_PROVIDER : provider.trim().toLowerCase(Locale.ROOT);
        return REDIS_PROVIDER.equals(normalizedProvider);
    }

    private Boolean tryAcquireFromRedis(String normalizedKey, int limit, long windowMs) {
        if (redisTemplate == null) {
            logRedisFallbackOnce("Redis provider selected but StringRedisTemplate is unavailable");
            return null;
        }
        try {
            String redisKey = buildRedisKey(normalizedKey);
            Long result = redisTemplate.execute(
                    REDIS_FIXED_WINDOW_SCRIPT,
                    List.of(redisKey),
                    String.valueOf(windowMs),
                    String.valueOf(limit));
            if (result == null) {
                logRedisFallbackOnce("Redis rate limiter returned null result");
                return null;
            }
            return result == 1L;
        } catch (Exception ex) {
            logRedisFallbackOnce("Redis rate limiter failed, fallback to in-memory: " + ex.getMessage());
            return null;
        }
    }

    private String buildRedisKey(String normalizedKey) {
        String prefix = redisKeyPrefix == null || redisKeyPrefix.isBlank()
                ? DEFAULT_REDIS_KEY_PREFIX
                : redisKeyPrefix.trim();
        return prefix + normalizedKey;
    }

    private void logRedisFallbackOnce(String message) {
        if (redisFallbackLogged.compareAndSet(false, true)) {
            log.warn(message);
        }
    }

    private void maybeCleanup(long now) {
        if (acquireCounter.incrementAndGet() % cleanupInterval != 0) {
            return;
        }
        windows.forEach((key, entry) -> {
            if (now < entry.resetAt) {
                return;
            }
            synchronized (entry) {
                if (now >= entry.resetAt) {
                    windows.remove(key, entry);
                }
            }
        });
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return DEFAULT_KEY;
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? DEFAULT_KEY : trimmed;
    }
}
