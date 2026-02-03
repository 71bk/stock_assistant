package tw.bk.appapi.ocr.dto;

import java.util.List;
import lombok.Data;

/**
 * Request DTO for confirming OCR drafts.
 */
@Data
public class ConfirmOcrRequest {
    /**
     * List of draft IDs to import.
     * If null or empty, all drafts will be imported (backward compatible).
     */
    private List<String> draftIds;
}
