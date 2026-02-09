package tw.bk.appapi.portfolio.vo;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appportfolio.model.PortfolioView;

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
    public static PortfolioResponse from(PortfolioView entity) {
        return PortfolioResponse.builder()
                .id(String.valueOf(entity.id()))
                .name(entity.name())
                .baseCurrency(entity.baseCurrency())
                .build();
    }

    /**
     * Create response with summary statistics (for detail view).
     */
    public static PortfolioResponse fromWithSummary(
            PortfolioView entity,
            BigDecimal totalMarketValue,
            BigDecimal totalCost,
            BigDecimal totalPnl,
            BigDecimal totalPnlPercent) {
        return PortfolioResponse.builder()
                .id(String.valueOf(entity.id()))
                .name(entity.name())
                .baseCurrency(entity.baseCurrency())
                .totalMarketValue(totalMarketValue)
                .totalCost(totalCost)
                .totalPnl(totalPnl)
                .totalPnlPercent(totalPnlPercent)
                .build();
    }
}
