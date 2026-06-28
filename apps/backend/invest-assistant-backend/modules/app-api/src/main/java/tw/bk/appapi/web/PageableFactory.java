package tw.bk.appapi.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 共用分頁參數正規化工具。
 *
 * <p>API 對外的 page 為 1-based，Spring Data 為 0-based；此工具統一處理
 * page/size 邊界（page &gt;= 1、1 &lt;= size &lt;= maxSize），並回傳與實際查詢一致的
 * {@code page}/{@code size}，避免各 controller 重複實作。
 */
public final class PageableFactory {

    private PageableFactory() {
    }

    /** 正規化後的分頁資訊，{@code page} 為對外的 1-based 值。 */
    public record Paged(Pageable pageable, int page, int size) {
    }

    /**
     * 以指定排序建立分頁。
     *
     * @param page    對外 1-based 頁碼
     * @param size    每頁筆數
     * @param maxSize 每頁筆數上限
     * @param sort    排序條件（可為 {@link Sort#unsorted()}）
     */
    public static Paged of(int page, int size, int maxSize, Sort sort) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), maxSize);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, sort == null ? Sort.unsorted() : sort);
        return new Paged(pageable, safePage, safeSize);
    }
}
