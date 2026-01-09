package tw.bk.appcommon.model;

import java.util.Locale;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

public enum MarketCode {
    TW,
    US;

    public String getCode() {
        return name();
    }

    public static MarketCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Market code is blank");
        }
        return MarketCode.valueOf(code.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isSupported(String code) {
        try {
            fromCode(code);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
    //字串轉MarketCode(正規化)
    public static MarketCode requireSupported(String code, ErrorCode errorCode, String message) {
        try {
            return fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(errorCode, message);
        }
    }

    public static MarketCode requireSupported(String code) {
        return requireSupported(code, ErrorCode.VALIDATION_ERROR, "Unsupported market: " + code);
    }
}
