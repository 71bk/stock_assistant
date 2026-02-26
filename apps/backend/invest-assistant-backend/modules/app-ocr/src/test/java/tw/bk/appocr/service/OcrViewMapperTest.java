package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appocr.model.OcrDraftView;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;

class OcrViewMapperTest {

    private OcrViewMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OcrViewMapper();
    }

    @Test
    void toJobView_shouldMapEntityFields() {
        OcrJobEntity entity = new OcrJobEntity();
        entity.setId(101L);
        entity.setStatementId(202L);
        entity.setStatus(OcrJobStatus.RUNNING.name());
        entity.setProgress(55);
        entity.setErrorMessage("none");

        OcrJobView view = mapper.toJobView(entity);

        assertEquals(101L, view.id());
        assertEquals(202L, view.statementId());
        assertEquals(OcrJobStatus.RUNNING, view.status());
        assertEquals(55, view.progress());
        assertEquals("none", view.errorMessage());
    }

    @Test
    void toDraftView_shouldMapEntityFields() {
        StatementTradeEntity entity = new StatementTradeEntity();
        entity.setId(11L);
        entity.setInstrumentId(22L);
        entity.setRawTicker("2330");
        entity.setName("TSMC");
        entity.setTradeDate(LocalDate.parse("2026-01-03"));
        entity.setSettlementDate(LocalDate.parse("2026-01-05"));
        entity.setSide(TradeSide.BUY.name());
        entity.setQuantity(new BigDecimal("10.500000"));
        entity.setPrice(new BigDecimal("100.12345678"));
        entity.setCurrency("TWD");
        entity.setFee(new BigDecimal("1.000000"));
        entity.setTax(new BigDecimal("0.500000"));
        entity.setNetAmount(new BigDecimal("-1052.796296"));
        entity.setWarningsJson("[\"w1\"]");
        entity.setErrorsJson("[]");
        entity.setRowHash("row-hash");

        OcrDraftView view = mapper.toDraftView(entity);

        assertEquals(11L, view.id());
        assertEquals(22L, view.instrumentId());
        assertEquals("2330", view.rawTicker());
        assertEquals("TSMC", view.name());
        assertEquals(LocalDate.parse("2026-01-03"), view.tradeDate());
        assertEquals(LocalDate.parse("2026-01-05"), view.settlementDate());
        assertEquals(TradeSide.BUY, view.side());
        assertEquals(new BigDecimal("10.500000"), view.quantity());
        assertEquals(new BigDecimal("100.12345678"), view.price());
        assertEquals("TWD", view.currency());
        assertEquals(new BigDecimal("1.000000"), view.fee());
        assertEquals(new BigDecimal("0.500000"), view.tax());
        assertEquals(new BigDecimal("-1052.796296"), view.netAmount());
        assertEquals("[\"w1\"]", view.warningsJson());
        assertEquals("[]", view.errorsJson());
        assertEquals("row-hash", view.rowHash());
    }
}
