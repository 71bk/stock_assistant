package tw.bk.appapi.portfolio.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appportfolio.model.PositionWithQuote;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponse {

    private String positionId;
    private String portfolioId;
    private String instrumentId;
    private String ticker;
    private String name;
    private String totalQuantity;
    private String avgCostNative;
    private String currency;

    // 市價與損益欄位
    private String currentPrice;
    private String marketValue;
    private String unrealizedPnl;
    private String unrealizedPnlPercent;

    /**
     * 從 Service 層的 PositionWithQuote 模型轉換為 API 回應 DTO
     */
    public static PositionResponse from(PositionWithQuote pos) {
        return PositionResponse.builder()
                .positionId(pos.portfolioId() + "-" + pos.instrumentId())
                .portfolioId(String.valueOf(pos.portfolioId()))
                .instrumentId(String.valueOf(pos.instrumentId()))
                .ticker(pos.ticker())
                .name(pos.name())
                .totalQuantity(pos.totalQuantity() != null ? pos.totalQuantity().toPlainString() : null)
                .avgCostNative(pos.avgCostNative() != null ? pos.avgCostNative().toPlainString() : null)
                .currency(pos.currency())
                .currentPrice(pos.currentPrice() != null ? pos.currentPrice().toPlainString() : null)
                .marketValue(pos.marketValue() != null ? pos.marketValue().toPlainString() : null)
                .unrealizedPnl(pos.unrealizedPnl() != null ? pos.unrealizedPnl().toPlainString() : null)
                .unrealizedPnlPercent(
                        pos.unrealizedPnlPercent() != null ? pos.unrealizedPnlPercent().toPlainString() : null)
                .build();
    }
}
