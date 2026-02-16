package tw.bk.appportfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioChatContext(
        Long portfolioId,
        String portfolioName,
        String baseCurrency,
        long holdingsCount,
        BigDecimal totalValue,
        BigDecimal cashValue,
        BigDecimal positionsValue,
        LocalDate asOfDate,
        boolean snapshotBacked) {
}
