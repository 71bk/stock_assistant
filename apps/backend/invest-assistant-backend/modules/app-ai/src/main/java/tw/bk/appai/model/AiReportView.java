package tw.bk.appai.model;

import java.time.OffsetDateTime;
import tw.bk.appcommon.enums.AiReportType;

public record AiReportView(
        Long id,
        AiReportType reportType,
        Long portfolioId,
        Long instrumentId,
        String inputSummary,
        String outputText,
        OffsetDateTime createdAt) {
}
