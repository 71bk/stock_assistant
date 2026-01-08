package tw.bk.appauth.security;

public class AuthUserPrincipal {
    private final Long userId;
    private final String email;

    public AuthUserPrincipal(Long userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}
