package tw.bk.appocr.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;

@Component
@Order(200)
@RequiredArgsConstructor
public class CurrencyMismatchWarningValidator implements OcrDraftValidator {
    private static final String WARNING = "CURRENCY_MISMATCH";

    private final InstrumentRepository instrumentRepository;

    @Override
    public void validate(OcrDraftContext context, OcrDraftValidationResult result) {
        StatementTradeEntity draft = context.draft();
        if (draft == null || draft.getInstrumentId() == null) {
            return;
        }
        String draftCurrency = draft.getCurrency();
        if (draftCurrency == null || draftCurrency.isBlank()) {
            return;
        }

        InstrumentEntity instrument = instrumentRepository.findById(draft.getInstrumentId()).orElse(null);
        if (instrument == null || instrument.getCurrency() == null || instrument.getCurrency().isBlank()) {
            return;
        }
        if (!draftCurrency.equalsIgnoreCase(instrument.getCurrency())) {
            result.addWarning(WARNING);
        }
    }
}
