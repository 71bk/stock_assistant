package tw.bk.appapi.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import tw.bk.appportfolio.model.PortfolioPositionsRebuildResult;

@Data
@Builder
public class PositionsRebuildResponse {
    private Long portfolioId;
    private Long userId;
    private int targetInstrumentCount;
    private int rebuiltInstrumentCount;
    private int failedInstrumentCount;
    private List<Long> failedInstrumentIds;

    public static PositionsRebuildResponse from(PortfolioPositionsRebuildResult result) {
        return PositionsRebuildResponse.builder()
                .portfolioId(result.portfolioId())
                .userId(result.userId())
                .targetInstrumentCount(result.targetInstrumentCount())
                .rebuiltInstrumentCount(result.rebuiltInstrumentCount())
                .failedInstrumentCount(result.failedInstrumentCount())
                .failedInstrumentIds(result.failedInstrumentIds())
                .build();
    }
}
