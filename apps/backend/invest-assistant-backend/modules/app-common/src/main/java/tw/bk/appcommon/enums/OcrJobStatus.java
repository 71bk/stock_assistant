package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * OCR 工作狀態列舉
 */
public enum OcrJobStatus {
    /** 排隊中 */
    QUEUED,
    RUNNING,
    /** 失敗 */
    FAILED,
    DONE,
    /** 已取消 */
    CANCELLED;

    public static OcrJobStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OcrJobStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
