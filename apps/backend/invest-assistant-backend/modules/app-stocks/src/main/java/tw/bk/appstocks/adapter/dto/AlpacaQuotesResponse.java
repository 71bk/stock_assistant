package tw.bk.appstocks.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Alpaca Quotes API Response DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlpacaQuotesResponse {
    private Map<String, QuoteData> quotes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteData {
        private String t; // timestamp (RFC3339)
        private BigDecimal bp; // bid price
        private BigDecimal ap; // ask price
        private Long bs; // bid size
        private Long as; // ask size
        private List<String> c; // conditions (array)
        private String z; // tape
        private String ax; // ask exchange
        private String bx; // bid exchange
    }
}
