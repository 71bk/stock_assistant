package tw.bk.appapi.ocr.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appocr.model.ConfirmResult;
import tw.bk.appocr.model.OcrDraftError;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrConfirmResponse {
    private int importedCount;
    private List<OcrDraftError<String>> errors;

    /** Maps the service result to the API response while preserving string IDs. */
    public static OcrConfirmResponse from(ConfirmResult result) {
        List<OcrDraftError<String>> errors = result.getErrors() != null
                ? result.getErrors().stream()
                        .map(error -> new OcrDraftError<>(String.valueOf(error.draftId()), error.reason()))
                        .toList()
                : List.of();
        return OcrConfirmResponse.builder()
                .importedCount(result.getImportedCount())
                .errors(errors)
                .build();
    }
}
