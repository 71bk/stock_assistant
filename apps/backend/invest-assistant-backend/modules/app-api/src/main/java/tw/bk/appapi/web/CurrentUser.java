package tw.bk.appapi.web;

import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.security.CurrentUserProvider;

/**
 * 取得當前登入使用者的共用工具。
 *
 * <p>各 controller 原本各自重複「取 userId，沒有就丟未授權」的私有方法，
 * 統一收斂到這裡。刻意以委派 {@link CurrentUserProvider#getUserId()} 的靜態方法實作，
 * 而非 interface default method，讓既有以 Mockito mock {@code CurrentUserProvider}
 * 並 stub {@code getUserId()} 的單元測試維持有效。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 取得當前登入使用者 ID，未登入則拋出 {@link ErrorCode#AUTH_UNAUTHORIZED}。 */
    public static Long require(CurrentUserProvider provider) {
        return provider.getUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized"));
    }
}
