package tw.bk.appocr.validation;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Component
@Order(210)
public class FutureTradeDateWarningValidator implements OcrDraftValidator {
    private static final String WARNING = "TRADE_DATE_IN_FUTURE";

    @Override
    public void validate(OcrDraftContext context, OcrDraftValidationResult result) {
        StatementTradeEntity draft = context.draft();
        if (draft == null || draft.getTradeDate() == null) {
            return;
        }
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        if (draft.getTradeDate().isAfter(todayUtc)) {
            result.addWarning(WARNING);
        }
    }
}
