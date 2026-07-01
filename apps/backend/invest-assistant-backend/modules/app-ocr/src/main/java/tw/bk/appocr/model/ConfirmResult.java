package tw.bk.appocr.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Result of OCR confirm operation.
 */
@Data
@Builder
public class ConfirmResult {
    private int importedCount;
    private List<OcrDraftError<Long>> errors;
}
