package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * 對話訊息狀態列舉
 */
public enum ConversationMessageStatus {
    /** 處理中 */
    PENDING,
    COMPLETED,
    /** 失敗 */
    FAILED;

    public static ConversationMessageStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ConversationMessageStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
