package tw.bk.appapi.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory fixed-window rate limiter.
 */
@Component
public class SimpleRateLimiter {
    private static final int DEFAULT_CLEANUP_INTERVAL = 256;
    private static final String DEFAULT_KEY = "__default__";

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

    public SimpleRateLimiter() {
        this(System::currentTimeMillis, DEFAULT_CLEANUP_INTERVAL);
    }

    SimpleRateLimiter(LongSupplier nowSupplier, int cleanupInterval) {
        this.nowSupplier = nowSupplier == null ? System::currentTimeMillis : nowSupplier;
        this.cleanupInterval = cleanupInterval <= 0 ? DEFAULT_CLEANUP_INTERVAL : cleanupInterval;
    }

    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        long windowMs = window == null ? 0L : window.toMillis();
        if (windowMs <= 0L) {
            return true;
        }

        long now = nowSupplier.getAsLong();
        String normalizedKey = normalizeKey(key);
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

    int windowCount() {
        return windows.size();
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
