package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * 標的狀態列舉
 */
public enum InstrumentStatus {
    /** 上市 */
    ACTIVE,
    DELISTED,
    /** 暫停交易 */
    SUSPENDED;

    public static InstrumentStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return InstrumentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
