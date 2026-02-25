package tw.bk.appapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SimpleRateLimiterTest {

    @Test
    void tryAcquire_shouldEnforceLimitWithinWindow() {
        AtomicLong now = new AtomicLong(1_000L);
        SimpleRateLimiter limiter = new SimpleRateLimiter(now::get, 1);

        assertTrue(limiter.tryAcquire("auth:refresh:127.0.0.1", 2, Duration.ofSeconds(1)));
        assertTrue(limiter.tryAcquire("auth:refresh:127.0.0.1", 2, Duration.ofSeconds(1)));
        assertFalse(limiter.tryAcquire("auth:refresh:127.0.0.1", 2, Duration.ofSeconds(1)));

        now.addAndGet(1_000L);
        assertTrue(limiter.tryAcquire("auth:refresh:127.0.0.1", 2, Duration.ofSeconds(1)));
    }

    @Test
    void tryAcquire_shouldCleanupExpiredWindows() {
        AtomicLong now = new AtomicLong(1_000L);
        SimpleRateLimiter limiter = new SimpleRateLimiter(now::get, 1);

        assertTrue(limiter.tryAcquire("auth:refresh:10.0.0.1", 1, Duration.ofMillis(10)));
        assertEquals(1, limiter.windowCount());

        now.addAndGet(11L);
        assertTrue(limiter.tryAcquire("auth:refresh:10.0.0.2", 1, Duration.ofMillis(10)));

        assertEquals(1, limiter.windowCount());
    }

    @Test
    void tryAcquire_shouldHandleNullAndBlankKeySafely() {
        AtomicLong now = new AtomicLong(1_000L);
        SimpleRateLimiter limiter = new SimpleRateLimiter(now::get, 1);

        assertTrue(limiter.tryAcquire(null, 2, Duration.ofSeconds(1)));
        assertTrue(limiter.tryAcquire("   ", 2, Duration.ofSeconds(1)));
        assertFalse(limiter.tryAcquire(null, 2, Duration.ofSeconds(1)));
        assertEquals(1, limiter.windowCount());
    }
}
