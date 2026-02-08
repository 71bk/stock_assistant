package tw.bk.appapi.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisRequest {
    private String portfolioId;

    private String instrumentId;

    private String reportType;

    private String prompt;
}
