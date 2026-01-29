package tw.bk.appapi.stocks.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appstocks.model.Quote;

/**
 * 股票報價回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {

    /**
     * 商品 ID（數字 ID）
     */
    @JsonProperty("instrument_id")
    private String instrumentId;

    /**
     * 商品識別碼
     */
    @JsonProperty("symbol_key")
    private String symbolKey;

    /**
     * 現價
     */
    @JsonProperty("price")
    private String price;

    /**
     * 漲跌金額
     */
    @JsonProperty("change")
    private String change;

    /**
     * 漲跌幅 %
     */
    @JsonProperty("change_pct")
    private String changePct;

    /**
     * 報價時間 (UTC ISO 8601)
     */
    @JsonProperty("ts_utc")
    private String tsUtc;

    /**
     * 從 Quote 模型轉換（合約格式）
     */
    public static QuoteResponse from(String symbolKey, Quote quote) {
        return from(null, symbolKey, quote);
    }

    /**
     * 從 Quote 模型轉換（合約格式，含 instrument_id）
     */
    public static QuoteResponse from(String instrumentId, String symbolKey, Quote quote) {
        return QuoteResponse.builder()
                .instrumentId(instrumentId)
                .symbolKey(symbolKey)
                .price(quote.getPrice() != null ? quote.getPrice().toPlainString() : null)
                .change(quote.getChange() != null ? quote.getChange().toPlainString() : null)
                .changePct(quote.getChangePercent() != null ? quote.getChangePercent().toPlainString() : null)
                .tsUtc(quote.getTimestamp() != null ? quote.getTimestamp().toString() : null)
                .build();
    }
}
