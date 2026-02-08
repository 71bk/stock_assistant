package tw.bk.appcommon.result;

import java.util.List;
import lombok.Getter;

@Getter
public final class PageResponse<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;

    private PageResponse(List<T> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public static <T> PageResponse<T> ok(List<T> items, int page, int size, long total) {
        return new PageResponse<>(items, page, size, total);
    }
}
