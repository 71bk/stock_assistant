package tw.bk.appapi.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory fixed-window rate limiter.
 */
@Component
public class SimpleRateLimiter {
    private static final class Window {
        private long resetAt;
        private int count;

        private Window(long resetAt) {
            this.resetAt = resetAt;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        long windowMs = window == null ? 0L : window.toMillis();
        if (windowMs <= 0L) {
            return true;
        }

        long now = System.currentTimeMillis();
        Window entry = windows.computeIfAbsent(key, k -> new Window(now + windowMs));
        synchronized (entry) {
            if (now >= entry.resetAt) {
                entry.resetAt = now + windowMs;
                entry.count = 0;
            }
            if (entry.count >= limit) {
                return false;
            }
            entry.count += 1;
            return true;
        }
    }
}
