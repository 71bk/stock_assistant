package tw.bk.appcommon.ratelimit;

import java.time.Duration;

public interface RateLimiter {
    boolean tryAcquire(String key, int limit, Duration window);
}
