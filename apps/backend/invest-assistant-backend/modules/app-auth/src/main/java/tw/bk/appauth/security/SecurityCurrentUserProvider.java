package tw.bk.appauth.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tw.bk.appcommon.security.CurrentUserProvider;

@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {
    @Override
    public Optional<Long> getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthUserPrincipal user) {
            return Optional.ofNullable(user.getUserId());
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthUserPrincipal user) {
            return Optional.ofNullable(user.getEmail());
        }
        return Optional.empty();
    }
}
