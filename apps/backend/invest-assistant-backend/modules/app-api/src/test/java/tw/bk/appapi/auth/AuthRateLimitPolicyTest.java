package tw.bk.appapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.ratelimit.RateLimiter;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitPolicyTest {
    private static final Duration REFRESH_WINDOW = Duration.ofSeconds(60);
    private static final Duration ADMIN_WINDOW = Duration.ofMinutes(2);

    @Mock
    private RateLimiter rateLimiter;

    private AuthRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AuthRateLimitPolicy(
                rateLimiter,
                30,
                REFRESH_WINDOW,
                10,
                ADMIN_WINDOW,
                true,
                "10.0.0.5");
    }

    @Test
    void enforceRefresh_shouldUseResolvedClientIpAndConfiguredLimit() {
        MockHttpServletRequest request = trustedProxyRequest();
        when(rateLimiter.tryAcquire("auth:refresh:203.0.113.7", 30, REFRESH_WINDOW)).thenReturn(true);

        policy.enforceRefresh(request);

        verify(rateLimiter).tryAcquire("auth:refresh:203.0.113.7", 30, REFRESH_WINDOW);
    }

    @Test
    void enforceAdminLogin_shouldThrowWhenLimitIsExceeded() {
        MockHttpServletRequest request = trustedProxyRequest();
        when(rateLimiter.tryAcquire("auth:admin:login:203.0.113.7", 10, ADMIN_WINDOW)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class, () -> policy.enforceAdminLogin(request));

        assertEquals(ErrorCode.RATE_LIMITED, error.getErrorCode());
        assertEquals("Too many login attempts", error.getMessage());
    }

    @Test
    void clientIp_shouldIgnoreForwardedHeaderFromUntrustedProxy() {
        AuthRateLimitPolicy untrustedPolicy = new AuthRateLimitPolicy(
                rateLimiter,
                30,
                REFRESH_WINDOW,
                10,
                ADMIN_WINDOW,
                true,
                "10.0.0.5");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("10.0.0.9", untrustedPolicy.clientIp(request));
    }

    private MockHttpServletRequest trustedProxyRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.5");
        return request;
    }
}
