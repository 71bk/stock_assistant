package tw.bk.appportfolio.model;

import java.math.BigDecimal;

/**
 * Portfolio summary statistics.
 */
public record PortfolioSummary(
        BigDecimal totalMarketValue,
        BigDecimal totalCost,
        BigDecimal totalPnl,
        BigDecimal totalPnlPercent) {
    public static PortfolioSummary empty() {
        return new PortfolioSummary(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
