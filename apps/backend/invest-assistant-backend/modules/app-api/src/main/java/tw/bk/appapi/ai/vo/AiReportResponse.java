package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appai.model.AiReportView;
import tw.bk.appcommon.enums.AiReportType;

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

    public static AiReportResponse from(AiReportView entity) {
        return AiReportResponse.builder()
                .reportId(entity.id() != null ? entity.id().toString() : null)
                .reportType(entity.reportType())
                .portfolioId(entity.portfolioId() != null ? entity.portfolioId().toString() : null)
                .instrumentId(entity.instrumentId() != null ? entity.instrumentId().toString() : null)
                .inputSummary(entity.inputSummary())
                .outputText(entity.outputText())
                .createdAt(entity.createdAt())
                .build();
    }
}
