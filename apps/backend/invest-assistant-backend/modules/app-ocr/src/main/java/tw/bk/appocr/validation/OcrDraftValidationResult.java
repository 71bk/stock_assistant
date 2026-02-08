package tw.bk.appocr.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class OcrDraftValidationResult {
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private boolean blocked;

    public static OcrDraftValidationResult empty() {
        return new OcrDraftValidationResult();
    }

    public static OcrDraftValidationResult withWarnings(Collection<String> initialWarnings) {
        OcrDraftValidationResult result = new OcrDraftValidationResult();
        result.addWarnings(initialWarnings);
        return result;
    }

    public void addWarning(String warning) {
        if (warning == null || warning.isBlank()) {
            return;
        }
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    public void addWarnings(Collection<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        for (String warning : warnings) {
            addWarning(warning);
        }
    }

    public void addError(String error) {
        if (error == null || error.isBlank()) {
            return;
        }
        if (!errors.contains(error)) {
            errors.add(error);
        }
    }

    public void block(String reason) {
        blocked = true;
        addError(reason);
    }

    public boolean hasBlockingErrors() {
        return blocked;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }
}
