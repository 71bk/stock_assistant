package tw.bk.appstocks.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Fugle Ticker Detail API Response DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FugleTickerResponse {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("name")
    private String name;

    @JsonProperty("nameEn")
    private String nameEn;

    @JsonProperty("market")
    private String market;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("industry")
    private String industry;

    @JsonProperty("tradingCurrency")
    private String tradingCurrency;

    @JsonProperty("securityType")
    private String securityType;
}
