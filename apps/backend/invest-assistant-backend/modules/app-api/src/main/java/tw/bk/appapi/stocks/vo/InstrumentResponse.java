package tw.bk.appapi.stocks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.AssetType;
import tw.bk.appstocks.model.InstrumentView;

/**
 * 商品搜尋回應（用於自動補全）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentResponse {

    /**
     * 商品 ID（數字 ID）
     */
    private String instrumentId;

    /**
     * 商品唯一識別碼（如 US:XNAS:AAPL）
     */
    private String symbolKey;

    /**
     * Ticker（如 AAPL, 2330）
     */
    private String ticker;

    /**
     * 中文名稱
     */
    private String nameZh;

    /**
     * 英文名稱
     */
    private String nameEn;

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
     * 資產類型（STOCK, ETF）
     */
    private AssetType assetType;

    /**
     * 從 Entity 轉換
     */
    public static InstrumentResponse from(InstrumentView entity) {
        return InstrumentResponse.builder()
                .instrumentId(entity.id() != null ? entity.id().toString() : null)
                .symbolKey(entity.symbolKey())
                .ticker(entity.ticker())
                .nameZh(entity.nameZh())
                .nameEn(entity.nameEn())
                .market(entity.marketCode())
                .exchange(entity.exchangeCode())
                .currency(entity.currency())
                .assetType(entity.assetType())
                .build();
    }
}
