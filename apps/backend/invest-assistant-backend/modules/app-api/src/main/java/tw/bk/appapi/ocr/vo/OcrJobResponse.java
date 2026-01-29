package tw.bk.appapi.ocr.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.OcrJobEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrJobResponse {
    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("statement_id")
    private String statementId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("progress")
    private Integer progress;

    @JsonProperty("error_message")
    private String errorMessage;

    public static OcrJobResponse from(OcrJobEntity entity) {
        return OcrJobResponse.builder()
                .jobId(entity.getId() != null ? entity.getId().toString() : null)
                .statementId(entity.getStatementId() != null ? entity.getStatementId().toString() : null)
                .status(entity.getStatus())
                .progress(entity.getProgress())
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}
