package tw.bk.appocr.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.StockTradeRepository;

@Component
@Order(310)
@RequiredArgsConstructor
public class PortfolioDuplicateValidator implements OcrDraftValidator {
    private static final String DUPLICATE_WARNING = "DUPLICATE_PORTFOLIO_TRADE";

    private final StockTradeRepository stockTradeRepository;

    @Override
    public void validate(OcrDraftContext context, OcrDraftValidationResult result) {
        StatementEntity statement = context.statement();
        StatementTradeEntity draft = context.draft();
        if (statement == null || draft == null) {
            return;
        }
        if (draft.getInstrumentId() == null
                || draft.getTradeDate() == null
                || draft.getSide() == null
                || draft.getQuantity() == null
                || draft.getPrice() == null) {
            return;
        }

        boolean exists = stockTradeRepository
                .existsByPortfolioIdAndInstrumentIdAndTradeDateAndSideAndQuantityAndPrice(
                        statement.getPortfolioId(),
                        draft.getInstrumentId(),
                        draft.getTradeDate(),
                        draft.getSide(),
                        draft.getQuantity(),
                        draft.getPrice());
        if (exists) {
            result.addWarning(DUPLICATE_WARNING);
        }
    }
}
