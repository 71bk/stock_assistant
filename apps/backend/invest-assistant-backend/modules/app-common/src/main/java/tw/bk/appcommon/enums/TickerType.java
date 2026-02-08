package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * Ticker 標的類型列舉
 */
public enum TickerType {
    /** 股票/ETF */
    EQUITY,
    INDEX,
    /** 權證 */
    WARRANT,
    ODDLOT;

    public static TickerType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TickerType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
