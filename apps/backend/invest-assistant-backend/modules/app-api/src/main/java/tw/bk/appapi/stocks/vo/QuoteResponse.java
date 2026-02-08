package tw.bk.appapi.stocks.vo;

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

    /** 商品 ID（數字 ID） */
    private String instrumentId;

    /** 商品識別碼 */
    private String symbolKey;

    /** 現價 */
    private String price;

    /** 開盤價 */
    private String open;

    /** 最高價 */
    private String high;

    /** 最低價 */
    private String low;

    /** 昨收價 */
    private String previousClose;

    /** 成交量 */
    private Long volume;

    /** 漲跌金額 */
    private String change;

    /** 漲跌幅 % */
    private String changePercent;

    /** 報價時間 (UTC ISO 8601) */
    private String timestamp;

    /**
     * 從 Quote 模型轉換
     */
    public static QuoteResponse from(String symbolKey, Quote quote) {
        return from(null, symbolKey, quote);
    }

    /**
     * 從 Quote 模型轉換（含 instrumentId）
     */
    public static QuoteResponse from(String instrumentId, String symbolKey, Quote quote) {
        return QuoteResponse.builder()
                .instrumentId(instrumentId)
                .symbolKey(symbolKey)
                .price(quote.getPrice() != null ? quote.getPrice().toPlainString() : null)
                .open(quote.getOpen() != null ? quote.getOpen().toPlainString() : null)
                .high(quote.getHigh() != null ? quote.getHigh().toPlainString() : null)
                .low(quote.getLow() != null ? quote.getLow().toPlainString() : null)
                .previousClose(quote.getPreviousClose() != null ? quote.getPreviousClose().toPlainString() : null)
                .volume(quote.getVolume())
                .change(quote.getChange() != null ? quote.getChange().toPlainString() : null)
                .changePercent(quote.getChangePercent() != null ? quote.getChangePercent().toPlainString() : null)
                .timestamp(quote.getTimestamp() != null ? quote.getTimestamp().toString() : null)
                .build();
    }
}
