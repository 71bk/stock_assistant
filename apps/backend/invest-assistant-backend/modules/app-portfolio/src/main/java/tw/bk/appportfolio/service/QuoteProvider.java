package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 報價查詢介面，用於解耦 Portfolio 與 Stocks 模組
 */
@FunctionalInterface
public interface QuoteProvider {
    /**
     * 根據 symbolKey 查詢現價
     * 
     * @param symbolKey 商品識別碼（如 TW:XTAI:2330）
     * @return 現價，查詢失敗時回傳 empty
     */
    Optional<BigDecimal> getCurrentPrice(String symbolKey);
}
