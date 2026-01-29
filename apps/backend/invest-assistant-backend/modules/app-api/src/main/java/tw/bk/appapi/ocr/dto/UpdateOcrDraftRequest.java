package tw.bk.appapi.ocr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appportfolio.model.TradeSide;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOcrDraftRequest {
    @JsonProperty("instrument_id")
    private String instrumentId;

    @JsonProperty("raw_ticker")
    private String rawTicker;

    @JsonProperty("name")
    private String name;

    @JsonProperty("trade_date")
    private LocalDate tradeDate;

    @JsonProperty("settlement_date")
    private LocalDate settlementDate;

    @JsonProperty("side")
    private TradeSide side;

    @JsonProperty("quantity")
    private String quantity;

    @JsonProperty("price")
    private String price;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("fee")
    private String fee;

    @JsonProperty("tax")
    private String tax;
}
