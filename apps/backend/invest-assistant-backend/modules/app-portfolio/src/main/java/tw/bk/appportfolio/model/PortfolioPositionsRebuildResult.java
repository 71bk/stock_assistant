package tw.bk.appportfolio.model;

import java.util.List;

public record PortfolioPositionsRebuildResult(
        Long portfolioId,
        Long userId,
        int targetInstrumentCount,
        int rebuiltInstrumentCount,
        int failedInstrumentCount,
        List<Long> failedInstrumentIds) {
}
