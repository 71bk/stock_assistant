package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.apppersistence.entity.StatementTradeEntity;

class OcrTradeCommandFactoryTest {

    private OcrTradeCommandFactory factory;

    @BeforeEach
    void setUp() {
        factory = new OcrTradeCommandFactory();
    }

    @Test
    void toTradeCommand_shouldBuildCommandWithNormalizedAmounts() {
        StatementTradeEntity draft = baseDraft();
        draft.setFee(new BigDecimal("1.2"));
        draft.setTax(new BigDecimal("0.9"));

        TradeCommand command = factory.toTradeCommand(draft);

        assertNotNull(command);
        assertEquals(new BigDecimal("1.200000"), command.fee());
        assertEquals(new BigDecimal("0.900000"), command.tax());
        assertEquals("OCR", command.source());
    }

    @Test
    void toTradeCommand_shouldDefaultFeeAndTaxToZero() {
        StatementTradeEntity draft = baseDraft();
        draft.setFee(null);
        draft.setTax(null);

        TradeCommand command = factory.toTradeCommand(draft);

        assertNotNull(command);
        assertEquals(new BigDecimal("0.000000"), command.fee());
        assertEquals(new BigDecimal("0.000000"), command.tax());
    }

    @Test
    void toTradeCommand_shouldReturnNullForUnknownSide() {
        StatementTradeEntity draft = baseDraft();
        draft.setSide("UNKNOWN");

        TradeCommand command = factory.toTradeCommand(draft);

        assertNull(command);
    }

    @Test
    void toTradeCommand_shouldRejectSettlementBeforeTrade() {
        StatementTradeEntity draft = baseDraft();
        draft.setSettlementDate(LocalDate.parse("2026-01-01"));
        draft.setTradeDate(LocalDate.parse("2026-01-03"));

        BusinessException ex = assertThrows(BusinessException.class, () -> factory.toTradeCommand(draft));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertEquals("Settlement date cannot be before trade date", ex.getMessage());
    }

    @Test
    void toTradeCommand_shouldRejectNegativeAmount() {
        StatementTradeEntity draft = baseDraft();
        draft.setFee(new BigDecimal("-1"));

        BusinessException ex = assertThrows(BusinessException.class, () -> factory.toTradeCommand(draft));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertEquals("Amount cannot be negative", ex.getMessage());
    }

    private StatementTradeEntity baseDraft() {
        StatementTradeEntity draft = new StatementTradeEntity();
        draft.setInstrumentId(1001L);
        draft.setTradeDate(LocalDate.parse("2026-01-03"));
        draft.setSettlementDate(LocalDate.parse("2026-01-05"));
        draft.setSide("BUY");
        draft.setQuantity(new BigDecimal("10.000000"));
        draft.setPrice(new BigDecimal("25.500000"));
        draft.setCurrency("USD");
        return draft;
    }
}
