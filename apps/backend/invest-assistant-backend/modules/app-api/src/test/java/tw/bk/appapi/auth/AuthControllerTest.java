package tw.bk.appapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import tw.bk.appapi.security.SimpleRateLimiter;
import tw.bk.appauth.config.AuthProperties;
import tw.bk.appauth.service.AdminAuthService;
import tw.bk.appauth.service.AuthCookieService;
import tw.bk.appauth.service.AuthService;
import tw.bk.appauth.service.UserService;
import tw.bk.appauth.service.UserSettingsService;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;

class AuthControllerTest {

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                mock(AuthService.class),
                mock(AuthCookieService.class),
                mock(AdminAuthService.class),
                mock(UserService.class),
                mock(UserSettingsService.class),
                mock(CurrentUserProvider.class),
                new AuthProperties(),
                mock(SimpleRateLimiter.class));
    }

    @Test
    void csrf_shouldReturnDisabledWhenTokenMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Result<Map<String, Object>> result = controller.csrf(request);

        assertTrue(result.isSuccess());
        assertEquals(Boolean.FALSE, result.getData().get("enabled"));
        assertEquals(1, result.getData().size());
    }

    @Test
    void csrf_shouldReturnEnabledPayloadWhenTokenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "token-123");
        request.setAttribute(CsrfToken.class.getName(), token);

        Result<Map<String, Object>> result = controller.csrf(request);

        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getData().get("enabled"));
        assertEquals("X-XSRF-TOKEN", result.getData().get("headerName"));
        assertEquals("_csrf", result.getData().get("parameterName"));
        assertEquals("token-123", result.getData().get("token"));
    }
}
