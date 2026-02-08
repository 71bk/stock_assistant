package tw.bk.appstocks.adapter.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Alpaca Bars API Response DTO
 */
@Data
public class AlpacaBarsResponse {
    private Map<String, List<BarData>> bars;
    private String nextPageToken;

    @Data
    public static class BarData {
        private String t; // timestamp (RFC3339)
        private BigDecimal o; // open
        private BigDecimal h; // high
        private BigDecimal l; // low
        private BigDecimal c; // close
        private Long v; // volume
        private Long n; // trade count
        private BigDecimal vw; // volume weighted average price
    }
}
