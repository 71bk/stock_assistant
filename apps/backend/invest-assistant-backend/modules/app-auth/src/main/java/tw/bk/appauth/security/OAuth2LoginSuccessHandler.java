package tw.bk.appauth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tw.bk.appauth.config.AuthProperties;
import tw.bk.appauth.service.AuthCookieService;
import tw.bk.appauth.service.AuthService;
import tw.bk.appauth.service.UserService;
import tw.bk.apppersistence.entity.UserEntity;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final UserService userService;
    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final AuthProperties properties;

    public OAuth2LoginSuccessHandler(UserService userService, AuthService authService,
                                     AuthCookieService cookieService, AuthProperties properties) {
        this.userService = userService;
        this.authService = authService;
        this.cookieService = cookieService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String sub = getAttribute(oauth2User, "sub");
        String email = getAttribute(oauth2User, "email");
        String name = getAttribute(oauth2User, "name");

        if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid OAuth2 profile");
            return;
        }

        UserEntity user = userService.upsertGoogleUser(sub, email, name);
        AuthService.AuthTokens tokens = authService.issueTokens(user);

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createAccessCookie(tokens.accessToken(), authService.accessTokenTtl()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createRefreshCookie(tokens.refreshToken(), authService.refreshTokenTtl()).toString());

        response.sendRedirect(properties.getLoginSuccessRedirect());
    }

    private String getAttribute(OAuth2User user, String key) {
        Object value = user.getAttributes().get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
