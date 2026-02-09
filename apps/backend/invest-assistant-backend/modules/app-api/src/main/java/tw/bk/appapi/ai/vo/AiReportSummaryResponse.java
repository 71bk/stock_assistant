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
public class AiReportSummaryResponse {
    private String reportId;

    private AiReportType reportType;

    private String portfolioId;

    private String instrumentId;

    private OffsetDateTime createdAt;

    public static AiReportSummaryResponse from(AiReportView entity) {
        return AiReportSummaryResponse.builder()
                .reportId(entity.id() != null ? entity.id().toString() : null)
                .reportType(entity.reportType())
                .portfolioId(entity.portfolioId() != null ? entity.portfolioId().toString() : null)
                .instrumentId(entity.instrumentId() != null ? entity.instrumentId().toString() : null)
                .createdAt(entity.createdAt())
                .build();
    }
}
