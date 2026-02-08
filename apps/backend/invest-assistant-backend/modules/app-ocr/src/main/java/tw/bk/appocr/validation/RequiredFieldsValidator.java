package tw.bk.appocr.validation;

import java.math.BigDecimal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Component
@Order(10)
public class RequiredFieldsValidator implements OcrDraftValidator {
    private static final String MISSING_TRADE_DATE = "MISSING_TRADE_DATE";
    private static final String MISSING_SIDE = "MISSING_SIDE";
    private static final String INVALID_SIDE = "INVALID_SIDE";
    private static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    private static final String INVALID_PRICE = "INVALID_PRICE";
    private static final String MISSING_CURRENCY = "MISSING_CURRENCY";

    @Override
    public void validate(OcrDraftContext context, OcrDraftValidationResult result) {
        StatementTradeEntity draft = context.draft();
        if (draft == null) {
            result.addError("DRAFT_MISSING");
            return;
        }

        if (draft.getTradeDate() == null) {
            result.addError(MISSING_TRADE_DATE);
        }

        String side = draft.getSide();
        if (side == null || side.isBlank()) {
            result.addError(MISSING_SIDE);
        } else if (!"BUY".equalsIgnoreCase(side) && !"SELL".equalsIgnoreCase(side)) {
            result.addError(INVALID_SIDE);
        }

        BigDecimal quantity = draft.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            result.addError(INVALID_QUANTITY);
        }

        BigDecimal price = draft.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            result.addError(INVALID_PRICE);
        }

        String currency = draft.getCurrency();
        if (currency == null || currency.isBlank()) {
            result.addError(MISSING_CURRENCY);
        }
    }
}
