package tw.bk.appapi.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.auth.vo.MeResponse;
import tw.bk.appauth.config.AuthProperties;
import tw.bk.appauth.service.AuthCookieService;
import tw.bk.appauth.service.AuthService;
import tw.bk.appauth.service.UserService;
import tw.bk.appauth.service.UserSettingsService;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appapi.security.SimpleRateLimiter;
import tw.bk.apppersistence.entity.UserSettingsEntity;
import tw.bk.apppersistence.entity.UserEntity;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final CurrentUserProvider currentUserProvider;
    private final AuthProperties authProperties;
    private final SimpleRateLimiter rateLimiter;

    @Value("${app.auth.refresh.rate-limit:30}")
    private int refreshRateLimit;

    @Value("${app.auth.refresh.rate-window:60s}")
    private Duration refreshRateWindow;

    public AuthController(AuthService authService,
            AuthCookieService cookieService,
            UserService userService,
            UserSettingsService userSettingsService,
            CurrentUserProvider currentUserProvider,
            AuthProperties authProperties,
            SimpleRateLimiter rateLimiter) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.userService = userService;
        this.userSettingsService = userSettingsService;
        this.currentUserProvider = currentUserProvider;
        this.authProperties = authProperties;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/google/login")
    public ResponseEntity<Void> googleLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/api/oauth2/authorization/google"))
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Result<Void>> refresh(HttpServletRequest request, HttpServletResponse response) {
        enforceRefreshRateLimit(request);
        String refreshToken = readCookie(request, authProperties.getRefreshCookieName());
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Refresh token missing");
        }

        AuthService.AuthTokens tokens = authService.refreshTokens(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createAccessCookie(tokens.accessToken(), authService.accessTokenTtl()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createRefreshCookie(tokens.refreshToken(), authService.refreshTokenTtl()).toString());
        return ResponseEntity.ok(Result.ok());
    }

    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, authProperties.getRefreshCookieName());
        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearAccessCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearRefreshCookie().toString());
        return ResponseEntity.ok(Result.ok());
    }

    @GetMapping("/me")
    public Result<MeResponse> me() {
        Long userId = currentUserProvider.getUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized"));
        Optional<UserEntity> userOpt = userService.findById(userId);
        UserEntity user = userOpt.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        UserSettingsEntity settings = userSettingsService.getOrCreate(userId);
        MeResponse response = MeResponse.from(user, settings.getBaseCurrency(), settings.getDisplayTimezone());
        return Result.ok(response);
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void enforceRefreshRateLimit(HttpServletRequest request) {
        String key = "auth:refresh:" + getClientKey(request);
        if (!rateLimiter.tryAcquire(key, refreshRateLimit, refreshRateWindow)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Too many refresh attempts");
        }
    }

    private String getClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
