package tw.bk.appapi.ocr.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrConfirmResponse {
    private int importedCount;
    private List<DraftError> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DraftError {
        private String draftId;
        private String reason;
    }
}
