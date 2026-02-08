package tw.bk.appcommon.enums;

import java.util.Locale;

/**
 * 檔案儲存供應商列舉
 */
public enum FileProvider {
    /** 本地 */
    LOCAL,
    S3,
    /** MinIO */
    MINIO;

    public static FileProvider from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FileProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
