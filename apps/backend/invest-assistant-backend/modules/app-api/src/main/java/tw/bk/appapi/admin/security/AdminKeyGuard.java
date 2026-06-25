package tw.bk.appapi.admin.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.security.CurrentUserProvider;

/**
 * Centralises admin-key authorisation for the admin endpoints.
 *
 * <p>The key is resolved from either the {@code X-Admin-Key} header (kept for backwards compatibility
 * and tooling such as Swagger/curl) or the HttpOnly {@code admin_key} cookie set via
 * {@code POST /admin/session/key}. When no server-side key is configured the guard falls back to
 * requiring an authenticated user, matching the previous behaviour.
 */
@Component
public class AdminKeyGuard {
    private static final String ADMIN_HEADER = "X-Admin-Key";

    private final AdminProperties adminProperties;
    private final CurrentUserProvider currentUserProvider;

    public AdminKeyGuard(AdminProperties adminProperties, CurrentUserProvider currentUserProvider) {
        this.adminProperties = adminProperties;
        this.currentUserProvider = currentUserProvider;
    }

    /** Throws if the request is not authorised to perform sensitive admin operations. */
    public void require(HttpServletRequest request) {
        String expected = adminProperties.getApiKey();
        if (expected == null || expected.isBlank()) {
            if (currentUserProvider.getUserId().isEmpty()) {
                throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
            }
            return;
        }
        if (!matches(resolveKey(request))) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Admin key invalid");
        }
    }

    /** Whether the backend requires an admin key at all (i.e. one is configured). */
    public boolean isConfigured() {
        String expected = adminProperties.getApiKey();
        return expected != null && !expected.isBlank();
    }

    /** Whether the supplied raw key matches the configured key (constant-time). */
    public boolean matches(String provided) {
        String expected = adminProperties.getApiKey();
        if (expected == null || expected.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    /** Whether the request currently carries a valid admin key (header or cookie). */
    public boolean hasValidKey(HttpServletRequest request) {
        return matches(resolveKey(request));
    }

    private String resolveKey(HttpServletRequest request) {
        String header = request.getHeader(ADMIN_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (adminProperties.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
