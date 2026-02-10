package tw.bk.appapi.admin.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class ValuationSnapshotRequest {
    private Long userId;
    private Long portfolioId;
    private LocalDate asOfDate;
}
