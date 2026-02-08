package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * AI 報告類型列舉
 */
public enum AiReportType {
    /** 個股分析 */
    INSTRUMENT,
    PORTFOLIO,
    /** 一般報告 */
    GENERAL;

    public static AiReportType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AiReportType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
