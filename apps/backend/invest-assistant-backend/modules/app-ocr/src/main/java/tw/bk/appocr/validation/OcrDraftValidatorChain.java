package tw.bk.appocr.validation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OcrDraftValidatorChain {
    private final List<OcrDraftValidator> validators;

    public OcrDraftValidationResult validateAll(OcrDraftContext context, OcrDraftValidationResult result) {
        if (validators == null || validators.isEmpty()) {
            return result;
        }
        for (OcrDraftValidator validator : validators) {
            validator.validate(context, result);
            if (result.hasBlockingErrors()) {
                break;
            }
        }
        return result;
    }
}
