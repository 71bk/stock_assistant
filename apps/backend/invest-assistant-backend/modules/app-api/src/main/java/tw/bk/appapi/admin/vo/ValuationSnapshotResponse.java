package tw.bk.appapi.admin.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;

@Data
@Builder
public class ValuationSnapshotResponse {
    private LocalDate asOfDate;
    private int total;
    private int succeeded;
    private int failed;
    private List<Long> failedPortfolioIds;

    public static ValuationSnapshotResponse from(PortfolioValuationSnapshotResult result) {
        return ValuationSnapshotResponse.builder()
                .asOfDate(result.asOfDate())
                .total(result.total())
                .succeeded(result.succeeded())
                .failed(result.failed())
                .failedPortfolioIds(result.failedPortfolioIds())
                .build();
    }
}
