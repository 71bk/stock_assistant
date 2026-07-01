package tw.bk.appapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tw.bk.appapi.auth.dto.AdminLoginRequest;
import tw.bk.appauth.service.AdminAuthService;
import tw.bk.appauth.service.AuthCookieService;
import tw.bk.appauth.service.AuthService;
import tw.bk.appcommon.result.Result;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {
    @Mock
    private AdminAuthService adminAuthService;
    @Mock
    private AuthService authService;
    @Mock
    private AuthCookieService cookieService;
    @Mock
    private AuthRateLimitPolicy rateLimitPolicy;

    private AdminAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAuthController(adminAuthService, authService, cookieService, rateLimitPolicy);
    }

    @Test
    void login_shouldEnforcePolicyAndIssueCookies() {
        AdminLoginRequest body = new AdminLoginRequest();
        body.setEmail("admin@example.com");
        body.setPassword("secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "test-agent");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthService.AuthTokens tokens = new AuthService.AuthTokens("access-token", "refresh-token");
        Duration accessTtl = Duration.ofMinutes(15);
        Duration refreshTtl = Duration.ofDays(7);

        when(rateLimitPolicy.clientIp(request)).thenReturn("203.0.113.10");
        when(adminAuthService.login("admin@example.com", "secret", "203.0.113.10", "test-agent"))
                .thenReturn(tokens);
        when(authService.accessTokenTtl()).thenReturn(accessTtl);
        when(authService.refreshTokenTtl()).thenReturn(refreshTtl);
        when(cookieService.createAccessCookie("access-token", accessTtl))
                .thenReturn(ResponseCookie.from("access", "access-token").build());
        when(cookieService.createRefreshCookie("refresh-token", refreshTtl))
                .thenReturn(ResponseCookie.from("refresh", "refresh-token").build());

        ResponseEntity<Result<Void>> result = controller.login(body, request, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        Collection<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, cookies.size());
        verify(rateLimitPolicy).enforceAdminLogin(request);
        verify(adminAuthService).login("admin@example.com", "secret", "203.0.113.10", "test-agent");
    }
}
