package tw.bk.appapi.security;

import java.time.Duration;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.ratelimit.RateLimiter;

/**
 * 固定窗口 rate limit 的小型守門：取不到額度就丟 {@link ErrorCode#RATE_LIMITED}。
 *
 * <p>收斂各處重複的「tryAcquire 失敗就拋例外」樣式。
 */
public final class RateLimitGuard {

    private RateLimitGuard() {
    }

    public static void require(
            RateLimiter rateLimiter, String key, int limit, Duration window, String message) {
        if (!rateLimiter.tryAcquire(key, limit, window)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, message);
        }
    }
}
