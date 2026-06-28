package tw.bk.appapi.ocr.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appocr.model.ConfirmResult;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrConfirmResponse {
    private int importedCount;
    private List<DraftError> errors;

    /** 由 service 層的 {@link ConfirmResult} 轉成對外 API 回應（ID 以字串表示）。 */
    public static OcrConfirmResponse from(ConfirmResult result) {
        List<DraftError> errors = result.getErrors() != null
                ? result.getErrors().stream().map(DraftError::from).toList()
                : List.of();
        return OcrConfirmResponse.builder()
                .importedCount(result.getImportedCount())
                .errors(errors)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DraftError {
        private String draftId;
        private String reason;

        public static DraftError from(ConfirmResult.DraftError error) {
            return DraftError.builder()
                    .draftId(String.valueOf(error.getDraftId()))
                    .reason(error.getReason())
                    .build();
        }
    }
}
