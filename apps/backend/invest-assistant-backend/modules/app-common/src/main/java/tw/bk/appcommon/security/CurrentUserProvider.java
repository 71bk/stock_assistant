package tw.bk.appcommon.security;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<Long> getUserId();

    Optional<String> getEmail();

    default boolean isAuthenticated() {
        return getUserId().isPresent();
    }
}
