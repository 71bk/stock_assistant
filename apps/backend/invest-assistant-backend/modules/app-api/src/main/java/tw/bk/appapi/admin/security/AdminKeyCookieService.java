package tw.bk.appapi.admin.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appauth.config.AuthProperties;

/**
 * Builds the HttpOnly cookie that carries the admin key.
 *
 * <p>Secure / SameSite / domain are reused from {@link AuthProperties} so the admin cookie behaves
 * identically to the auth cookies across environments (e.g. {@code Secure + SameSite=None} in prod).
 * Because the cookie is HttpOnly it is never readable from JavaScript, unlike the previous
 * localStorage approach.
 */
@Component
public class AdminKeyCookieService {
    private final AdminProperties adminProperties;
    private final AuthProperties authProperties;

    public AdminKeyCookieService(AdminProperties adminProperties, AuthProperties authProperties) {
        this.adminProperties = adminProperties;
        this.authProperties = authProperties;
    }

    public ResponseCookie create(String key) {
        return build(key, adminProperties.getCookieTtl());
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration ttl) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(adminProperties.getCookieName(), value)
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .path(adminProperties.getCookiePath())
                .maxAge(ttl);

        if (authProperties.getCookieDomain() != null && !authProperties.getCookieDomain().isBlank()) {
            builder.domain(authProperties.getCookieDomain());
        }

        builder.sameSite(authProperties.getCookieSameSite());
        return builder.build();
    }
}
