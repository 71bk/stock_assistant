package tw.bk.appapi.auth;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.auth.dto.AdminLoginRequest;
import tw.bk.appapi.auth.vo.MeResponse;
import tw.bk.appauth.model.UserSettingsView;
import tw.bk.appauth.model.UserView;
import tw.bk.appauth.config.AuthProperties;
import tw.bk.appauth.service.AdminAuthService;
import tw.bk.appauth.service.AuthCookieService;
import tw.bk.appauth.service.AuthService;
import tw.bk.appauth.service.UserService;
import tw.bk.appauth.service.UserSettingsService;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appapi.security.SimpleRateLimiter;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final AdminAuthService adminAuthService;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final CurrentUserProvider currentUserProvider;
    private final AuthProperties authProperties;
    private final SimpleRateLimiter rateLimiter;

    @Value("${app.auth.refresh.rate-limit:30}")
    private int refreshRateLimit;

    @Value("${app.auth.refresh.rate-window:60s}")
    private Duration refreshRateWindow;

    @Value("${app.auth.admin-login.rate-limit:10}")
    private int adminLoginRateLimit;

    @Value("${app.auth.admin-login.rate-window:60s}")
    private Duration adminLoginRateWindow;

    public AuthController(AuthService authService,
            AuthCookieService cookieService,
            AdminAuthService adminAuthService,
            UserService userService,
            UserSettingsService userSettingsService,
            CurrentUserProvider currentUserProvider,
            AuthProperties authProperties,
            SimpleRateLimiter rateLimiter) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.adminAuthService = adminAuthService;
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

    @PostMapping("/admin/login")
    public ResponseEntity<Result<Void>> adminLogin(
            @Valid @RequestBody AdminLoginRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        enforceAdminLoginRateLimit(request);
        String ip = getClientKey(request);
        String userAgent = request.getHeader("User-Agent");

        AuthService.AuthTokens tokens = adminAuthService.login(
                requestBody.getEmail(),
                requestBody.getPassword(),
                ip,
                userAgent);

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
        Optional<UserView> userOpt = userService.findById(userId);
        UserView user = userOpt.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        UserSettingsView settings = userSettingsService.getOrCreate(userId);
        MeResponse response = MeResponse.from(user, settings.baseCurrency(), settings.displayTimezone());
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

    private void enforceAdminLoginRateLimit(HttpServletRequest request) {
        String key = "auth:admin:login:" + getClientKey(request);
        if (!rateLimiter.tryAcquire(key, adminLoginRateLimit, adminLoginRateWindow)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Too many login attempts");
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
