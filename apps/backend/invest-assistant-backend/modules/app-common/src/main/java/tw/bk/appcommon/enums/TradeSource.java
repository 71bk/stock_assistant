package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * 交易來源列舉
 */
public enum TradeSource {
    /** 手動輸入 */
    MANUAL,
    OCR,
    /** API 匯入 */
    API,
    MIGRATION;

    public static TradeSource from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TradeSource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
