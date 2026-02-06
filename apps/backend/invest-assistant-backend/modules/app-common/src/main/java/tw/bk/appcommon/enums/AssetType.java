package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * 資產類型列舉
 */
public enum AssetType {
    /** 股票 */
    STOCK,
    ETF,
    /** 權證 */
    WARRANT;

    public static AssetType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AssetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
