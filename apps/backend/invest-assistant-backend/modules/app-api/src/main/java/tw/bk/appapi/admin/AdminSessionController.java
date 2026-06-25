package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.admin.dto.AdminKeyRequest;
import tw.bk.appapi.admin.security.AdminKeyCookieService;
import tw.bk.appapi.admin.security.AdminKeyGuard;
import tw.bk.appapi.admin.vo.AdminKeyStatusResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;

/**
 * Manages the admin key as an HttpOnly cookie so it never has to live in browser-readable storage.
 *
 * <p>All routes sit under {@code /admin/**} and therefore already require an authenticated ADMIN
 * (see SecurityConfig). Setting the cookie is an elevation step on top of the admin login.
 */
@RestController
@RequestMapping("/admin/session")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminSessionController {
    private final AdminKeyGuard adminKeyGuard;
    private final AdminKeyCookieService cookieService;

    @GetMapping("/key")
    @Operation(summary = "Report whether an admin key is required and currently active")
    public Result<AdminKeyStatusResponse> status(HttpServletRequest request) {
        boolean required = adminKeyGuard.isConfigured();
        boolean active = required && adminKeyGuard.hasValidKey(request);
        return Result.ok(new AdminKeyStatusResponse(required, active));
    }

    @PostMapping("/key")
    @Operation(summary = "Validate the admin key and store it in an HttpOnly cookie")
    public ResponseEntity<Result<AdminKeyStatusResponse>> setKey(@Valid @RequestBody AdminKeyRequest request) {
        if (!adminKeyGuard.isConfigured()) {
            // No server-side key configured: nothing to store, an authenticated admin is sufficient.
            return ResponseEntity.ok(Result.ok(new AdminKeyStatusResponse(false, false)));
        }
        if (!adminKeyGuard.matches(request.getApiKey())) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Admin key invalid");
        }
        ResponseCookie cookie = cookieService.create(request.getApiKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Result.ok(new AdminKeyStatusResponse(true, true)));
    }

    @DeleteMapping("/key")
    @Operation(summary = "Clear the admin key cookie")
    public ResponseEntity<Result<Void>> clearKey() {
        ResponseCookie cookie = cookieService.clear();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Result.ok());
    }
}
