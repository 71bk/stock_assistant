package tw.bk.appapi.admin.dto;

import lombok.Data;

@Data
public class PortfolioSnapshotRequest {
    private Long userId;
    private Long portfolioId;
}
