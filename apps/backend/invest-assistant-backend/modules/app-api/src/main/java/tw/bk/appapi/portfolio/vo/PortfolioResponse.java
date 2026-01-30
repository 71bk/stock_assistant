package tw.bk.appapi.portfolio.vo;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.PortfolioEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponse {

    private String id;
    private String name;
    private String baseCurrency;

    // Summary statistics
    private BigDecimal totalMarketValue;
    private BigDecimal totalCost;
    private BigDecimal totalPnl;
    private BigDecimal totalPnlPercent;

    /**
     * Create response from entity without summary (for list view).
     */
    public static PortfolioResponse from(PortfolioEntity entity) {
        return PortfolioResponse.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .baseCurrency(entity.getBaseCurrency())
                .build();
    }

    /**
     * Create response with summary statistics (for detail view).
     */
    public static PortfolioResponse fromWithSummary(
            PortfolioEntity entity,
            BigDecimal totalMarketValue,
            BigDecimal totalCost,
            BigDecimal totalPnl,
            BigDecimal totalPnlPercent) {
        return PortfolioResponse.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .baseCurrency(entity.getBaseCurrency())
                .totalMarketValue(totalMarketValue)
                .totalCost(totalCost)
                .totalPnl(totalPnl)
                .totalPnlPercent(totalPnlPercent)
                .build();
    }
}
