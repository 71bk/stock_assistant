package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * 交易方向列舉
 */
public enum TradeSide {
    /** 買入 */
    BUY,
    SELL;

    public static TradeSide from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TradeSide.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

