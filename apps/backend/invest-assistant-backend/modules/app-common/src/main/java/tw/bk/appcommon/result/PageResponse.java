package tw.bk.appcommon.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.util.TraceIdUtils;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PageResponse<T> {
    private final boolean success;
    private final PageData<T> data;
    private final Result.ApiError error;
    private final String traceId;

    private PageResponse(boolean success, PageData<T> data, Result.ApiError error, String traceId) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.traceId = traceId;
    }

    public static <T> PageResponse<T> ok(List<T> items, int page, int size, long total) {
        PageData<T> data = new PageData<>(items, page, size, total);
        return new PageResponse<>(true, data, null, TraceIdUtils.getOrCreateTraceId());
    }

    public static <T> PageResponse<T> error(ErrorCode code) {
        return error(code, code.getDefaultMessage(), null);
    }

    public static <T> PageResponse<T> error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    public static <T> PageResponse<T> error(ErrorCode code, String message, Map<String, Object> details) {
        Map<String, Object> safe = details == null ? null : Map.copyOf(details);
        Result.ApiError apiError = new Result.ApiError(code.getCode(), message, safe);
        return new PageResponse<>(false, null, apiError, TraceIdUtils.getOrCreateTraceId());
    }

    @Getter
    public static final class PageData<T> {
        private final List<T> items;
        private final int page;
        private final int size;
        private final long total;

        public PageData(List<T> items, int page, int size, long total) {
            this.items = items;
            this.page = page;
            this.size = size;
            this.total = total;
        }
    }
}
