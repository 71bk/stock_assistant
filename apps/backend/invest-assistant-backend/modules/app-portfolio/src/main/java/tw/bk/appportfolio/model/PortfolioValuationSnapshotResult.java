package tw.bk.appportfolio.model;

import java.time.LocalDate;
import java.util.List;

public record PortfolioValuationSnapshotResult(
        LocalDate asOfDate,
        int total,
        int succeeded,
        int failed,
        List<Long> failedPortfolioIds) {
    public PortfolioValuationSnapshotResult {
        failedPortfolioIds = failedPortfolioIds == null ? List.of() : List.copyOf(failedPortfolioIds);
    }
}
