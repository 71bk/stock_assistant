package tw.bk.appapi.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioSnapshotResponse {
    private int total;
    private int ingested;
    private int skipped;
    private int failed;
}
