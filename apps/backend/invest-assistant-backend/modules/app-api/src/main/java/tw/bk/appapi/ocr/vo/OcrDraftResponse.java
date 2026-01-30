package tw.bk.appapi.ocr.vo;

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
    private String draftId;
    private String instrumentId;
    private String rawTicker;
    private String name;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
    private String side;
    private String quantity;
    private String price;
    private String currency;
    private String fee;
    private String tax;
    private List<String> warnings;
    private List<String> errors;
    private String rowHash;

    public static OcrDraftResponse from(StatementTradeEntity entity, List<String> warnings, List<String> errors) {
        return OcrDraftResponse.builder()
                .draftId(entity.getId() != null ? entity.getId().toString() : null)
                .instrumentId(entity.getInstrumentId() != null ? entity.getInstrumentId().toString() : null)
                .rawTicker(entity.getRawTicker())
                .name(entity.getName())
                .tradeDate(entity.getTradeDate())
                .settlementDate(entity.getSettlementDate())
                .side(entity.getSide())
                .quantity(entity.getQuantity() != null ? entity.getQuantity().toPlainString() : null)
                .price(entity.getPrice() != null ? entity.getPrice().toPlainString() : null)
                .currency(entity.getCurrency())
                .fee(entity.getFee() != null ? entity.getFee().toPlainString() : null)
                .tax(entity.getTax() != null ? entity.getTax().toPlainString() : null)
                .warnings(warnings)
                .errors(errors)
                .rowHash(entity.getRowHash())
                .build();
    }
}
