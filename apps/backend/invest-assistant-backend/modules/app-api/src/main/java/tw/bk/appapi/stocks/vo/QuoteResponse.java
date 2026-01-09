package tw.bk.appapi.stocks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appstocks.model.Quote;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 股票報價回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {

    /**
     * 商品識別碼
     */
    private String symbolKey;

    /**
     * Ticker
     */
    private String ticker;

    /**
     * 現價
     */
    private BigDecimal price;

    /**
     * 開盤價
     */
    private BigDecimal open;

    /**
     * 最高價
     */
    private BigDecimal high;

    /**
     * 最低價
     */
    private BigDecimal low;

    /**
     * 昨收價
     */
    private BigDecimal previousClose;

    /**
     * 成交量
     */
    private Long volume;

    /**
     * 漲跌金額
     */
    private BigDecimal change;

    /**
     * 漲跌幅 %
     */
    private BigDecimal changePercent;

    /**
     * 報價時間
     */
    private Instant timestamp;

    /**
     * 從 Quote 模型轉換
     */
    public static QuoteResponse from(String symbolKey, Quote quote) {
        return QuoteResponse.builder()
                .symbolKey(symbolKey)
                .ticker(quote.getTicker())
                .price(quote.getPrice())
                .open(quote.getOpen())
                .high(quote.getHigh())
                .low(quote.getLow())
                .previousClose(quote.getPreviousClose())
                .volume(quote.getVolume())
                .change(quote.getChange())
                .changePercent(quote.getChangePercent())
                .timestamp(quote.getTimestamp())
                .build();
    }
}
