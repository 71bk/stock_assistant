package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.AiReportType;
import tw.bk.apppersistence.entity.AiReportEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReportResponse {
    private String reportId;

    private AiReportType reportType;

    private String portfolioId;

    private String instrumentId;

    private String inputSummary;

    private String outputText;

    private OffsetDateTime createdAt;

    public static AiReportResponse from(AiReportEntity entity) {
        return AiReportResponse.builder()
                .reportId(entity.getId() != null ? entity.getId().toString() : null)
                .reportType(AiReportType.from(entity.getReportType()))
                .portfolioId(entity.getPortfolioId() != null ? entity.getPortfolioId().toString() : null)
                .instrumentId(entity.getInstrumentId() != null ? entity.getInstrumentId().toString() : null)
                .inputSummary(entity.getInputSummary())
                .outputText(entity.getOutputText())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
