package tw.bk.appstocks.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Fugle Quote API Response DTO
 * 實際 API 回應是扁平結構，沒有 data 包裝層
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FugleQuoteResponse {
    private String date;
    private String type;
    private String exchange;
    private String market;
    private String symbol;
    private String name;

    // 價格資訊
    private BigDecimal referencePrice;
    private BigDecimal previousClose;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal lastPrice;
    private BigDecimal avgPrice;

    // 漲跌資訊
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal amplitude;

    // 成交資訊
    private Long lastSize;
    private TotalData total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TotalData {
        private Long tradeValue;
        private Long tradeVolume;
        private Long tradeVolumeAtBid;
        private Long tradeVolumeAtAsk;
        private Long transaction;
    }
}
