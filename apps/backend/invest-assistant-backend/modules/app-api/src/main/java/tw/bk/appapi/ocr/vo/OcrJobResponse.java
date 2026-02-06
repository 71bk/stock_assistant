package tw.bk.appapi.ocr.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.apppersistence.entity.OcrJobEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrJobResponse {
    private String jobId;
    private String statementId;
    private OcrJobStatus status;
    private Integer progress;
    private String errorMessage;

    public static OcrJobResponse from(OcrJobEntity entity) {
        return OcrJobResponse.builder()
                .jobId(entity.getId() != null ? entity.getId().toString() : null)
                .statementId(entity.getStatementId() != null ? entity.getStatementId().toString() : null)
                .status(entity.getStatusEnum())
                .progress(entity.getProgress())
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}
