package tw.bk.appapi.ocr.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appocr.model.OcrJobView;

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

    public static OcrJobResponse from(OcrJobView entity) {
        return OcrJobResponse.builder()
                .jobId(entity.id() != null ? entity.id().toString() : null)
                .statementId(entity.statementId() != null ? entity.statementId().toString() : null)
                .status(entity.status())
                .progress(entity.progress())
                .errorMessage(entity.errorMessage())
                .build();
    }
}
