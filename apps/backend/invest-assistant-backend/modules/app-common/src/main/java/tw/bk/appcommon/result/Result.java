package tw.bk.appcommon.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

import lombok.Getter;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.util.TraceIdUtils;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Result<T> {
    private final boolean success;
    private final T data;
    private final ApiError error;
    private final String traceId;

    private Result(boolean success, T data, ApiError error, String traceId) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.traceId = traceId;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, data, null, TraceIdUtils.getOrCreateTraceId());
    }

    public static Result<Void> ok() {
        return new Result<>(true, null, null, TraceIdUtils.getOrCreateTraceId());
    }

    public static <T> Result<T> error(ErrorCode code) {
        return error(code, code.getDefaultMessage(), null);
    }

    public static <T> Result<T> error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    public static <T> Result<T> error(ErrorCode code, String message, Map<String, Object> details) {
        Map<String, Object> safe = details == null ? null : Map.copyOf(details);
        return new Result<>(false, null, new ApiError(code.getCode(), message, safe),
                TraceIdUtils.getOrCreateTraceId());
    }

    public static final class ApiError {
        private final String code;
        private final String message;
        private final Map<String, Object> details;

        public ApiError(String code, String message, Map<String, Object> details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }
        public String getCode() { return code; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return details; }
    }
}
