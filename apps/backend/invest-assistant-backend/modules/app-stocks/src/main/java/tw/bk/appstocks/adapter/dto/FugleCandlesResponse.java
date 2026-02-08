package tw.bk.appstocks.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fugle Candles API Response DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FugleCandlesResponse {
    private List<CandleData> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CandleData {
        private String date;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private Long volume;
    }
}
