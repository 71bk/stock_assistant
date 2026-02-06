package tw.bk.appapi.portfolio.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.apppersistence.entity.StockTradeEntity;

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

    public static TradeResponse from(StockTradeEntity entity) {
        return TradeResponse.builder()
                .tradeId(String.valueOf(entity.getId()))
                .instrumentId(String.valueOf(entity.getInstrumentId()))
                .tradeDate(entity.getTradeDate() != null ? entity.getTradeDate().toString() : null)
                .settlementDate(entity.getSettlementDate() != null ? entity.getSettlementDate().toString() : null)
                .side(entity.getSideEnum())
                .quantity(entity.getQuantity() != null ? entity.getQuantity().toPlainString() : null)
                .price(entity.getPrice() != null ? entity.getPrice().toPlainString() : null)
                .currency(entity.getCurrency())
                .grossAmount(entity.getGrossAmount() != null ? entity.getGrossAmount().toPlainString() : null)
                .fee(entity.getFee() != null ? entity.getFee().toPlainString() : null)
                .tax(entity.getTax() != null ? entity.getTax().toPlainString() : null)
                .netAmount(entity.getNetAmount() != null ? entity.getNetAmount().toPlainString() : null)
                .source(entity.getSourceEnum())
                .accountId(entity.getAccountId() != null ? String.valueOf(entity.getAccountId()) : null)
                .build();
    }
}
