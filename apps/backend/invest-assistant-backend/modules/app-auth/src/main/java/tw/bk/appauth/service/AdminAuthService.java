package tw.bk.appauth.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.UserRole;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.AdminLoginAuditEntity;
import tw.bk.apppersistence.entity.UserEntity;
import tw.bk.apppersistence.repository.AdminLoginAuditRepository;
import tw.bk.apppersistence.repository.UserRepository;

@Service
public class AdminAuthService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final BCryptPasswordEncoder LEGACY_BCRYPT_ENCODER = new BCryptPasswordEncoder();

    private static final String FAIL_KEY_PREFIX = "auth:admin:login:fail:";
    private static final String LOCK_KEY_PREFIX = "auth:admin:login:lock:";

    private final UserRepository userRepository;
    private final AdminLoginAuditRepository auditRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final String dummyEncodedPassword;

    @Value("${app.auth.admin-login.max-failures:5}")
    private int maxFailures;

    @Value("${app.auth.admin-login.fail-window:15m}")
    private Duration failWindow;

    @Value("${app.auth.admin-login.lock-duration:15m}")
    private Duration lockDuration;

    public AdminAuthService(
            UserRepository userRepository,
            AdminLoginAuditRepository auditRepository,
            AuthService authService,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        // Reduce timing side-channel differences for unknown users.
        this.dummyEncodedPassword = this.passwordEncoder.encode("admin-login-dummy-password");
    }

    public AuthService.AuthTokens login(String email, String password, String ip, String userAgent) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "email is required");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "password is required");
        }

        Optional<UserEntity> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(password, dummyEncodedPassword);
            saveAudit(normalizedEmail, null, false, ip, userAgent, "USER_NOT_FOUND");
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid credentials");
        }

        UserEntity user = userOpt.get();
        if (user.getRole() != UserRole.ADMIN) {
            passwordEncoder.matches(password, dummyEncodedPassword);
            saveAudit(normalizedEmail, user.getId(), false, ip, userAgent, "NOT_ADMIN");
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid credentials");
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(safe(user.getStatus()))) {
            passwordEncoder.matches(password, dummyEncodedPassword);
            saveAudit(normalizedEmail, user.getId(), false, ip, userAgent, "INACTIVE");
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid credentials");
        }
        if (isLocked(normalizedEmail)) {
            saveAudit(normalizedEmail, user.getId(), false, ip, userAgent, "LOCKED");
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Too many login attempts");
        }

        String storedHash = user.getPasswordHash();
        if (storedHash == null || storedHash.isBlank()) {
            passwordEncoder.matches(password, dummyEncodedPassword);
            saveAudit(normalizedEmail, user.getId(), false, ip, userAgent, "NO_PASSWORD_SET");
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid credentials");
        }

        boolean ok = verifyAndUpgradePassword(user, password, storedHash);
        if (!ok) {
            int failures = recordFailure(normalizedEmail);
            String reason = failures >= maxFailures && maxFailures > 0 ? "BAD_PASSWORD_LOCKED" : "BAD_PASSWORD";
            saveAudit(normalizedEmail, user.getId(), false, ip, userAgent, reason);
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid credentials");
        }

        clearFailures(normalizedEmail);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        AuthService.AuthTokens tokens = authService.issueTokens(user);
        saveAudit(normalizedEmail, user.getId(), true, ip, userAgent, null);
        return tokens;
    }

    private boolean verifyAndUpgradePassword(UserEntity user, String rawPassword, String storedHash) {
        boolean matched;
        try {
            matched = passwordEncoder.matches(rawPassword, storedHash);
        } catch (Exception ex) {
            matched = false;
        }
        if (matched) {
            return true;
        }

        // Backward compatibility: allow legacy bcrypt hashes and upgrade to Argon2id on success.
        if (isLegacyBcrypt(storedHash) && LEGACY_BCRYPT_ENCODER.matches(rawPassword, storedHash)) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private boolean isLegacyBcrypt(String hash) {
        if (hash == null) {
            return false;
        }
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$");
    }

    private int recordFailure(String normalizedEmail) {
        if (maxFailures <= 0) {
            return 0;
        }
        String key = FAIL_KEY_PREFIX + normalizedEmail;
        Long current = redisTemplate.opsForValue().increment(key);
        long count = current == null ? 0L : current;

        // Set TTL only on first increment to keep a fixed failure window.
        if (count == 1L && failWindow != null && !failWindow.isZero() && !failWindow.isNegative()) {
            redisTemplate.expire(key, failWindow);
        }

        if (count >= maxFailures) {
            lock(normalizedEmail);
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    private void lock(String normalizedEmail) {
        if (lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(LOCK_KEY_PREFIX + normalizedEmail, "1", lockDuration);
            redisTemplate.delete(FAIL_KEY_PREFIX + normalizedEmail);
        } catch (Exception ex) {
            log.warn("Failed to lock admin account: email={}, reason={}", normalizedEmail, ex.getMessage());
        }
    }

    private boolean isLocked(String normalizedEmail) {
        try {
            return redisTemplate.opsForValue().get(LOCK_KEY_PREFIX + normalizedEmail) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private void clearFailures(String normalizedEmail) {
        try {
            redisTemplate.delete(LOCK_KEY_PREFIX + normalizedEmail);
            redisTemplate.delete(FAIL_KEY_PREFIX + normalizedEmail);
        } catch (Exception ex) {
            log.debug("Failed to clear admin login counters: email={}, reason={}", normalizedEmail, ex.getMessage());
        }
    }

    private void saveAudit(String email, Long userId, boolean success, String ip, String userAgent, String reason) {
        try {
            AdminLoginAuditEntity audit = new AdminLoginAuditEntity();
            audit.setEmail(email);
            audit.setUserId(userId);
            audit.setSuccess(success);
            audit.setIp(safe(ip));
            audit.setUserAgent(safe(userAgent));
            audit.setReason(safe(reason));
            auditRepository.save(audit);
        } catch (Exception ex) {
            log.warn("Failed to persist admin login audit: email={}, reason={}", email, ex.getMessage());
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? null : value.trim();
    }
}
