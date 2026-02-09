package tw.bk.appportfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioValuationView(
        LocalDate asOfDate,
        BigDecimal totalValue,
        BigDecimal cashValue,
        BigDecimal positionsValue,
        String baseCurrency) {
}
