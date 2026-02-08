package tw.bk.appapi.ocr.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.TradeSide;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOcrDraftRequest {
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
}
