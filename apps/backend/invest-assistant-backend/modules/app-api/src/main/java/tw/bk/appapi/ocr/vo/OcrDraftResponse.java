package tw.bk.appapi.ocr.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appocr.model.OcrDraftView;

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
    private TradeSide side;
    private String quantity;
    private String price;
    private String currency;
    private String fee;
    private String tax;
    /** 客戶淨收/淨付金額（買入為負，賣出為正） */
    private String netAmount;
    private List<String> warnings;
    private List<String> errors;
    private String rowHash;

    public static OcrDraftResponse from(OcrDraftView entity, List<String> warnings, List<String> errors) {
        return OcrDraftResponse.builder()
                .draftId(entity.id() != null ? entity.id().toString() : null)
                .instrumentId(entity.instrumentId() != null ? entity.instrumentId().toString() : null)
                .rawTicker(entity.rawTicker())
                .name(entity.name())
                .tradeDate(entity.tradeDate())
                .settlementDate(entity.settlementDate())
                .side(entity.side())
                .quantity(entity.quantity() != null ? entity.quantity().toPlainString() : null)
                .price(entity.price() != null ? entity.price().toPlainString() : null)
                .currency(entity.currency())
                .fee(entity.fee() != null ? entity.fee().toPlainString() : null)
                .tax(entity.tax() != null ? entity.tax().toPlainString() : null)
                .netAmount(entity.netAmount() != null ? entity.netAmount().toPlainString() : null)
                .warnings(warnings)
                .errors(errors)
                .rowHash(entity.rowHash())
                .build();
    }
}
