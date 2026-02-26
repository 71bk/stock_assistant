package tw.bk.appocr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Service
public class OcrTradeCommandFactory {
    private static final int AMOUNT_SCALE = 6;
    private static final String SOURCE_OCR = TradeSource.OCR.name();

    public TradeCommand toTradeCommand(StatementTradeEntity draft) {
        if (draft.getInstrumentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft missing instrumentId");
        }
        if (draft.getTradeDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft missing tradeDate");
        }
        if (isSettlementBeforeTrade(draft.getTradeDate(), draft.getSettlementDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Settlement date cannot be before trade date");
        }
        if (draft.getSide() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft missing side");
        }
        if (draft.getQuantity() == null || draft.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft quantity must be > 0");
        }
        if (draft.getPrice() == null || draft.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft price must be > 0");
        }
        String currency = draft.getCurrency();
        if (currency == null || currency.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft missing currency");
        }

        TradeSide side;
        try {
            side = TradeSide.valueOf(draft.getSide().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        return new TradeCommand(
                draft.getInstrumentId(),
                draft.getTradeDate(),
                draft.getSettlementDate(),
                side,
                draft.getQuantity(),
                draft.getPrice(),
                currency,
                normalizeAmount(draft.getFee()),
                normalizeAmount(draft.getTax()),
                null,
                SOURCE_OCR);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Amount cannot be negative");
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private boolean isSettlementBeforeTrade(LocalDate tradeDate, LocalDate settlementDate) {
        return tradeDate != null && settlementDate != null && settlementDate.isBefore(tradeDate);
    }
}
