package tw.bk.appapi.ai.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tw.bk.appcommon.enums.UserRole;

@Component
public class CurrentUserRoleProvider {

    public UserRole getCurrentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return UserRole.USER;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority != null && "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())) {
                return UserRole.ADMIN;
            }
        }
        return UserRole.USER;
    }
}
