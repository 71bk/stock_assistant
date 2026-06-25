package tw.bk.appapi.admin.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {
    private String apiKey;

    /** Name of the HttpOnly cookie that carries the admin key after it is set via /admin/session/key. */
    private String cookieName = "admin_key";

    /** Path the admin key cookie is scoped to. Includes the server context-path so it is only sent to admin endpoints. */
    private String cookiePath = "/api/admin";

    /** Lifetime of the admin key cookie. Kept short so the elevated secret does not linger. */
    private Duration cookieTtl = Duration.ofHours(8);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public Duration getCookieTtl() {
        return cookieTtl;
    }

    public void setCookieTtl(Duration cookieTtl) {
        this.cookieTtl = cookieTtl;
    }
}
