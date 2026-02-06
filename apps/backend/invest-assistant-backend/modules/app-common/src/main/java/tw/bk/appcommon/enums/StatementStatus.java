package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * OCR Statement 對帳單狀態
 */
public enum StatementStatus {
    /** 草稿 */
    DRAFT,
    REVIEWED,
    /** 已確認 */
    CONFIRMED,
    FAILED,
    /** 已被取代 */
    SUPERSEDED;

    public static StatementStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return StatementStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
