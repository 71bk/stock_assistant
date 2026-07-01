package tw.bk.appapi.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.auth.dto.AdminLoginRequest;
import tw.bk.appauth.service.AdminAuthService;
import tw.bk.appauth.service.AuthCookieService;
import tw.bk.appauth.service.AuthService;
import tw.bk.appcommon.result.Result;

@RestController
@RequestMapping("/auth/admin")
public class AdminAuthController {
    private final AdminAuthService adminAuthService;
    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final AuthRateLimitPolicy rateLimitPolicy;

    public AdminAuthController(
            AdminAuthService adminAuthService,
            AuthService authService,
            AuthCookieService cookieService,
            AuthRateLimitPolicy rateLimitPolicy) {
        this.adminAuthService = adminAuthService;
        this.authService = authService;
        this.cookieService = cookieService;
        this.rateLimitPolicy = rateLimitPolicy;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<Void>> login(
            @Valid @RequestBody AdminLoginRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        rateLimitPolicy.enforceAdminLogin(request);
        String ip = rateLimitPolicy.clientIp(request);
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
}
