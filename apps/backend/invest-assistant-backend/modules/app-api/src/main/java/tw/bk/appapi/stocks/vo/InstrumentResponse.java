package tw.bk.appapi.stocks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.InstrumentEntity;

/**
 * 商品搜尋回應（用於自動補全）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentResponse {

    /**
     * 商品唯一識別碼（如 US:XNAS:AAPL）
     */
    private String symbolKey;

    /**
     * Ticker（如 AAPL, 2330）
     */
    private String ticker;

    /**
     * 顯示名稱（優先中文，否則英文）
     */
    private String displayName;

    /**
     * 市場代碼（US, TW）
     */
    private String market;

    /**
     * 交易所代碼（NASDAQ, TWSE）
     */
    private String exchange;

    /**
     * 幣別（USD, TWD）
     */
    private String currency;

    /**
     * 從 Entity 轉換
     */
    public static InstrumentResponse from(InstrumentEntity entity) {
        // 組合顯示名稱（優先中文）
        String displayName = entity.getNameZh() != null && !entity.getNameZh().isBlank()
                ? entity.getNameZh() + " (" + entity.getTicker() + ")"
                : (entity.getNameEn() != null ? entity.getNameEn() + " (" + entity.getTicker() + ")"
                        : entity.getTicker());

        return InstrumentResponse.builder()
                .symbolKey(entity.getSymbolKey())
                .ticker(entity.getTicker())
                .displayName(displayName)
                .market(entity.getMarket() != null ? entity.getMarket().getCode() : null)
                .exchange(entity.getExchange() != null ? entity.getExchange().getCode() : null)
                .currency(entity.getCurrency())
                .build();
    }
}
