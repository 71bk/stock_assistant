package tw.bk.appauth.bootstrap;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tw.bk.appcommon.enums.UserRole;
import tw.bk.apppersistence.entity.UserEntity;
import tw.bk.apppersistence.repository.UserRepository;

@Component
public class AdminBootstrapper implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${app.admin.bootstrap.email:}")
    private String email;

    @Value("${app.admin.bootstrap.password:}")
    private String password;

    @Value("${app.admin.bootstrap.display-name:Admin}")
    private String displayName;

    @Value("${app.admin.bootstrap.update-password:false}")
    private boolean updatePassword;

    public AdminBootstrapper(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            log.warn("Admin bootstrap enabled but email is empty; skipping");
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("Admin bootstrap enabled but password is empty; skipping");
            return;
        }

        UserEntity user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(UserEntity::new);
        boolean created = user.getId() == null;

        user.setEmail(normalizedEmail);
        user.setRole(UserRole.ADMIN);
        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus(STATUS_ACTIVE);
        }
        if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
            user.setDisplayName(displayName);
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank() || updatePassword) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        userRepository.save(user);
        if (created) {
            log.info("Bootstrapped admin user: email={}", normalizedEmail);
        } else {
            log.info("Ensured admin user: email={}, updatedPassword={}", normalizedEmail, updatePassword);
        }
    }

    private String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
