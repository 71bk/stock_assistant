package tw.bk.appapi.portfolio.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.appportfolio.model.TradeView;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeResponse {

    private String tradeId;
    private String instrumentId;
    private String tradeDate;
    private String settlementDate;
    private TradeSide side;
    private String quantity;
    private String price;
    private String currency;
    private String grossAmount;
    private String fee;
    private String tax;
    private String netAmount;
    private TradeSource source;
    private String accountId;

    public static TradeResponse from(TradeView entity) {
        return TradeResponse.builder()
                .tradeId(String.valueOf(entity.id()))
                .instrumentId(String.valueOf(entity.instrumentId()))
                .tradeDate(entity.tradeDate() != null ? entity.tradeDate().toString() : null)
                .settlementDate(entity.settlementDate() != null ? entity.settlementDate().toString() : null)
                .side(entity.side())
                .quantity(entity.quantity() != null ? entity.quantity().toPlainString() : null)
                .price(entity.price() != null ? entity.price().toPlainString() : null)
                .currency(entity.currency())
                .grossAmount(entity.grossAmount() != null ? entity.grossAmount().toPlainString() : null)
                .fee(entity.fee() != null ? entity.fee().toPlainString() : null)
                .tax(entity.tax() != null ? entity.tax().toPlainString() : null)
                .netAmount(entity.netAmount() != null ? entity.netAmount().toPlainString() : null)
                .source(entity.source())
                .accountId(entity.accountId() != null ? String.valueOf(entity.accountId()) : null)
                .build();
    }
}
