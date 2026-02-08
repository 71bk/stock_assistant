package tw.bk.appocr.validation;

import java.math.BigDecimal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Component
@Order(20)
public class NegativeAmountWarningValidator implements OcrDraftValidator {
    private static final String NEGATIVE_FEE = "NEGATIVE_FEE";
    private static final String NEGATIVE_TAX = "NEGATIVE_TAX";

    @Override
    public void validate(OcrDraftContext context, OcrDraftValidationResult result) {
        StatementTradeEntity draft = context.draft();
        if (draft == null) {
            return;
        }

        if (isNegative(draft.getFee())) {
            result.addWarning(NEGATIVE_FEE);
        }
        if (isNegative(draft.getTax())) {
            result.addWarning(NEGATIVE_TAX);
        }
    }

    private boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }
}
