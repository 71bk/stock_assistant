package tw.bk.appapi.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appauth.config.AuthProperties;
import tw.bk.appauth.service.AuthCookieService;
import tw.bk.appauth.service.AuthService;
import tw.bk.appauth.service.UserService;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.apppersistence.entity.UserEntity;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;
    private final AuthProperties authProperties;

    public AuthController(AuthService authService,
                          AuthCookieService cookieService,
                          UserService userService,
                          CurrentUserProvider currentUserProvider,
                          AuthProperties authProperties) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
        this.authProperties = authProperties;
    }

    @GetMapping("/auth/google/login")
    public ResponseEntity<Void> googleLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/google"))
                .build();
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<Result<Void>> refresh(HttpServletRequest request, HttpServletResponse response) {
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

    @PostMapping("/auth/logout")
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
        MeResponse response = new MeResponse(String.valueOf(user.getId()), user.getEmail(), user.getDisplayName());
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

    public record MeResponse(String id, String email, String displayName) {
    }
}
