package tw.bk.appapi.web;

import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

/**
 * 共用 ID 字串解析工具。
 *
 * <p>API 對外以字串傳遞 ID（path / body / query），此工具統一把字串轉成 Long，
 * 失敗時拋出 {@link ErrorCode#VALIDATION_ERROR}，避免各 controller 重複實作。
 */
public final class IdParser {

    private IdParser() {
    }

    /** 解析必填 ID，格式錯誤拋出驗證錯誤。 */
    public static Long parseId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid ID format");
        }
    }

    /** 解析選填 ID，空白回傳 null，格式錯誤拋出驗證錯誤。 */
    public static Long parseIdOrNull(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return null;
        }
        return parseId(idStr);
    }
}
