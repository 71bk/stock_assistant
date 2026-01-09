package tw.bk.appapi.portfolio.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.UserPositionEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponse {

    private String portfolioId;
    private String instrumentId;
    private String totalQuantity;
    private String avgCostNative;
    private String currency;

    public static PositionResponse from(UserPositionEntity entity) {
        return PositionResponse.builder()
                .portfolioId(String.valueOf(entity.getPortfolioId()))
                .instrumentId(String.valueOf(entity.getInstrumentId()))
                .totalQuantity(entity.getTotalQuantity() != null ? entity.getTotalQuantity().toPlainString() : null)
                .avgCostNative(entity.getAvgCostNative() != null ? entity.getAvgCostNative().toPlainString() : null)
                .currency(entity.getCurrency())
                .build();
    }
}
