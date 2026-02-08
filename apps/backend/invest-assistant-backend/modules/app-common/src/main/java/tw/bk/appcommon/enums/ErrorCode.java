package tw.bk.appcommon.enums;

/**
 * 錯誤代碼對應 HTTP 狀態碼
 */
public enum ErrorCode {
    VALIDATION_ERROR(400, "VALIDATION_ERROR", "Validation error"),
    AUTH_UNAUTHORIZED(401, "AUTH_UNAUTHORIZED", "Unauthorized"),
    AUTH_FORBIDDEN(403, "AUTH_FORBIDDEN", "Forbidden"),
    NOT_FOUND(404, "NOT_FOUND", "Resource not found"),
    CONFLICT(409, "CONFLICT", "Conflict"),
    OCR_PARSE_FAILED(422, "OCR_PARSE_FAILED", "OCR parse failed"),
    RATE_LIMITED(429, "RATE_LIMITED", "Too many requests"),
    NOT_IMPLEMENTED(501, "NOT_IMPLEMENTED", "Not implemented"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "Internal server error");

    private final int httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
