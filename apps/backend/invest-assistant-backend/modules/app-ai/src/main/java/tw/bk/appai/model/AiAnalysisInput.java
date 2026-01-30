package tw.bk.appai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisInput {
    private Long userId;
    private Long portfolioId;
    private Long instrumentId;
    private String reportType;
    private String prompt;
}
