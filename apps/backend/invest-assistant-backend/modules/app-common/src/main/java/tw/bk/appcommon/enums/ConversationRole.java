package tw.bk.appcommon.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * 對話角色列舉
 */
public enum ConversationRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String value;

    ConversationRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static ConversationRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ConversationRole role : values()) {
            if (role.value.equals(normalized)) {
                return role;
            }
        }
        return null;
    }
}
