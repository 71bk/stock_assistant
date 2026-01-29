package tw.bk.appapi.ocr.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrDraftResponse {
    @JsonProperty("draft_id")
    private String draftId;

    @JsonProperty("instrument_id")
    private String instrumentId;

    @JsonProperty("raw_ticker")
    private String rawTicker;

    @JsonProperty("trade_date")
    private LocalDate tradeDate;

    @JsonProperty("settlement_date")
    private LocalDate settlementDate;

    @JsonProperty("side")
    private String side;

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

    @JsonProperty("warnings")
    private List<String> warnings;

    public static OcrDraftResponse from(StatementTradeEntity entity, List<String> warnings) {
        return OcrDraftResponse.builder()
                .draftId(entity.getId() != null ? entity.getId().toString() : null)
                .instrumentId(entity.getInstrumentId() != null ? entity.getInstrumentId().toString() : null)
                .rawTicker(entity.getRawTicker())
                .tradeDate(entity.getTradeDate())
                .settlementDate(entity.getSettlementDate())
                .side(entity.getSide())
                .quantity(entity.getQuantity() != null ? entity.getQuantity().toPlainString() : null)
                .price(entity.getPrice() != null ? entity.getPrice().toPlainString() : null)
                .currency(entity.getCurrency())
                .fee(entity.getFee() != null ? entity.getFee().toPlainString() : null)
                .tax(entity.getTax() != null ? entity.getTax().toPlainString() : null)
                .warnings(warnings)
                .build();
    }
}
