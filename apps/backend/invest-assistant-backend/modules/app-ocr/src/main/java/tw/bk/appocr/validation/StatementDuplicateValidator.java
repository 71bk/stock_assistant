package tw.bk.appocr.validation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(300)
public class StatementDuplicateValidator implements OcrDraftValidator {
    @Override
    public void validate(OcrDraftContext context, OcrDraftValidationResult result) {
        String rowHash = context.draft() != null ? context.draft().getRowHash() : null;
        if (rowHash == null || rowHash.isBlank()) {
            result.addWarning("ROW_HASH_MISSING");
            return;
        }
        if (context.seenHashes() != null && context.seenHashes().contains(rowHash)) {
            // Mark as duplicate warning instead of blocking
            result.addWarning("DUPLICATE_IN_STATEMENT");
        }
    }
}
