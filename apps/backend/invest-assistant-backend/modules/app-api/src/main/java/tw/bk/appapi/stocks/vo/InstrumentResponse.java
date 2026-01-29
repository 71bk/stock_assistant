package tw.bk.appapi.stocks.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
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
     * 商品 ID（數字 ID）
     */
    @JsonProperty("instrument_id")
    private String id;

    /**
     * 商品唯一識別碼（如 US:XNAS:AAPL）
     */
    @JsonProperty("symbol_key")
    private String symbolKey;

    /**
     * Ticker（如 AAPL, 2330）
     */
    @JsonProperty("ticker")
    private String ticker;

    /**
     * 中文名稱
     */
    @JsonProperty("name_zh")
    private String nameZh;

    /**
     * 英文名稱
     */
    @JsonProperty("name_en")
    private String nameEn;

    /**
     * 市場代碼（US, TW）
     */
    @JsonProperty("market")
    private String market;

    /**
     * 交易所代碼（NASDAQ, TWSE）
     */
    @JsonProperty("exchange")
    private String exchange;

    /**
     * 幣別（USD, TWD）
     */
    @JsonProperty("currency")
    private String currency;

    @JsonProperty("asset_type")
    private String assetType;

    /**
     * 從 Entity 轉換
     */
    public static InstrumentResponse from(InstrumentEntity entity) {
        return InstrumentResponse.builder()
                .id(entity.getId() != null ? entity.getId().toString() : null)
                .symbolKey(entity.getSymbolKey())
                .ticker(entity.getTicker())
                .nameZh(entity.getNameZh())
                .nameEn(entity.getNameEn())
                .market(entity.getMarket() != null ? entity.getMarket().getCode() : null)
                .exchange(entity.getExchange() != null ? entity.getExchange().getCode() : null)
                .currency(entity.getCurrency())
                .assetType(entity.getAssetType())
                .build();
    }
}
