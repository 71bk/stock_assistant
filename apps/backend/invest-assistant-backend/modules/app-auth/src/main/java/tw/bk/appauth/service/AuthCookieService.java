package tw.bk.appauth.service;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import tw.bk.appauth.config.AuthProperties;

@Component
public class AuthCookieService {
    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie createAccessCookie(String token, Duration ttl) {
        return baseCookie(properties.getAccessCookieName(), token, properties.getAccessCookiePath(), ttl);
    }

    public ResponseCookie createRefreshCookie(String token, Duration ttl) {
        return baseCookie(properties.getRefreshCookieName(), token, properties.getRefreshCookiePath(), ttl);
    }

    public ResponseCookie clearAccessCookie() {
        return baseCookie(properties.getAccessCookieName(), "", properties.getAccessCookiePath(), Duration.ZERO);
    }

    public ResponseCookie clearRefreshCookie() {
        return baseCookie(properties.getRefreshCookieName(), "", properties.getRefreshCookiePath(), Duration.ZERO);
    }

    private ResponseCookie baseCookie(String name, String value, String path, Duration ttl) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .path(path)
                .maxAge(ttl);

        if (properties.getCookieDomain() != null && !properties.getCookieDomain().isBlank()) {
            builder.domain(properties.getCookieDomain());
        }

        builder.sameSite(properties.getCookieSameSite());
        return builder.build();
    }
}
