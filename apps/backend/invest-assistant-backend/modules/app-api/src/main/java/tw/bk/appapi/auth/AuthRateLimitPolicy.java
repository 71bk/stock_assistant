package tw.bk.appapi.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tw.bk.appapi.security.ClientIpResolver;
import tw.bk.appapi.security.RateLimitGuard;
import tw.bk.appcommon.ratelimit.RateLimiter;

/** Rate-limit keys and thresholds for authentication endpoints. */
@Component
public class AuthRateLimitPolicy {
    private final RateLimiter rateLimiter;
    private final int refreshLimit;
    private final Duration refreshWindow;
    private final int adminLoginLimit;
    private final Duration adminLoginWindow;
    private final boolean trustedProxyEnabled;
    private final String trustedProxyIpList;

    public AuthRateLimitPolicy(
            RateLimiter rateLimiter,
            @Value("${app.auth.refresh.rate-limit:30}") int refreshLimit,
            @Value("${app.auth.refresh.rate-window:60s}") Duration refreshWindow,
            @Value("${app.auth.admin-login.rate-limit:10}") int adminLoginLimit,
            @Value("${app.auth.admin-login.rate-window:60s}") Duration adminLoginWindow,
            @Value("${app.security.trusted-proxy.enabled:false}") boolean trustedProxyEnabled,
            @Value("${app.security.trusted-proxy.ip-list:}") String trustedProxyIpList) {
        this.rateLimiter = rateLimiter;
        this.refreshLimit = refreshLimit;
        this.refreshWindow = refreshWindow;
        this.adminLoginLimit = adminLoginLimit;
        this.adminLoginWindow = adminLoginWindow;
        this.trustedProxyEnabled = trustedProxyEnabled;
        this.trustedProxyIpList = trustedProxyIpList;
    }

    public void enforceRefresh(HttpServletRequest request) {
        RateLimitGuard.require(
                rateLimiter,
                "auth:refresh:" + clientIp(request),
                refreshLimit,
                refreshWindow,
                "Too many refresh attempts");
    }

    public void enforceAdminLogin(HttpServletRequest request) {
        RateLimitGuard.require(
                rateLimiter,
                "auth:admin:login:" + clientIp(request),
                adminLoginLimit,
                adminLoginWindow,
                "Too many login attempts");
    }

    public String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, trustedProxyEnabled, trustedProxyIpList);
    }
}
