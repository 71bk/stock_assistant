package tw.bk.appocr.validation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Component
@Order(205)
public class SettlementBeforeTradeWarningValidator implements OcrDraftValidator {
    private static final String WARNING = "SETTLEMENT_BEFORE_TRADE";

    @Override
    public void validate(OcrDraftContext context, OcrDraftValidationResult result) {
        StatementTradeEntity draft = context.draft();
        if (draft == null || draft.getTradeDate() == null || draft.getSettlementDate() == null) {
            return;
        }
        if (draft.getSettlementDate().isBefore(draft.getTradeDate())) {
            result.addWarning(WARNING);
        }
    }
}
