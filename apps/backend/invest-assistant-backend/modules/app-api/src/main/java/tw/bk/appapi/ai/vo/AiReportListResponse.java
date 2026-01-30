package tw.bk.appapi.ai.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReportListResponse {
    private List<AiReportSummaryResponse> items;
    private int page;
    private int size;
    private long total;
}
