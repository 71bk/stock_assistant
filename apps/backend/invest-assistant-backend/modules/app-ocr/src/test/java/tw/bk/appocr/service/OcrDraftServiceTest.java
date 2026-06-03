package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appocr.client.AiWorkerParsedTrade;
import tw.bk.appocr.validation.OcrDraftValidatorChain;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;

@ExtendWith(MockitoExtension.class)
class OcrDraftServiceTest {

    @Mock
    private StatementTradeRepository statementTradeRepository;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private OcrDraftValidatorChain draftValidatorChain;
    @Mock
    private StatementRepository statementRepository;
    @Mock
    private StockTradeRepository stockTradeRepository;

    private OcrDraftService service;
    private List<StatementTradeEntity> savedDrafts;

    @BeforeEach
    void setUp() {
        service = new OcrDraftService(
                statementTradeRepository,
                instrumentRepository,
                draftValidatorChain,
                new ObjectMapper(),
                new OcrRowHashService(),
                statementRepository,
                stockTradeRepository);
        savedDrafts = new ArrayList<>();
        when(statementTradeRepository.findRowHashesByStatementId(99L)).thenReturn(List.of());
        doAnswer(invocation -> {
            Iterable<StatementTradeEntity> entities = invocation.getArgument(0);
            entities.forEach(savedDrafts::add);
            return savedDrafts;
        }).when(statementTradeRepository).saveAll(any());
        when(draftValidatorChain.validateAll(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void saveDrafts_shouldAssignInstrumentWhenTickerHasSingleMatch() {
        InstrumentEntity instrument = instrument(1L, "ABC", "US:XNAS:ABC", "USD");
        when(instrumentRepository.findByTickerIgnoreCase("ABC")).thenReturn(List.of(instrument));

        service.saveDrafts(statement(), List.of(trade("ABC", null)));

        assertEquals(1, savedDrafts.size());
        StatementTradeEntity draft = savedDrafts.get(0);
        assertEquals(1L, draft.getInstrumentId());
        assertEquals("USD", draft.getCurrency());
        assertEquals("[]", draft.getWarningsJson());
    }

    @Test
    void saveDrafts_shouldNotAssignInstrumentWhenTickerIsAmbiguous() {
        InstrumentEntity first = instrument(1L, "ABC", "US:XNAS:ABC", "USD");
        InstrumentEntity second = instrument(2L, "ABC", "TW:XTAI:ABC", "TWD");
        when(instrumentRepository.findByTickerIgnoreCase("ABC")).thenReturn(List.of(first, second));

        service.saveDrafts(statement(), List.of(trade("ABC", "USD")));

        assertEquals(1, savedDrafts.size());
        StatementTradeEntity draft = savedDrafts.get(0);
        assertNull(draft.getInstrumentId());
        assertEquals("USD", draft.getCurrency());
        assertTrue(draft.getWarningsJson().contains("AMBIGUOUS_TICKER"));
    }

    private StatementEntity statement() {
        StatementEntity statement = new StatementEntity();
        statement.setId(99L);
        statement.setUserId(7L);
        statement.setPortfolioId(11L);
        return statement;
    }

    private AiWorkerParsedTrade trade(String ticker, String currency) {
        return new AiWorkerParsedTrade(
                ticker,
                "BUY",
                new BigDecimal("10"),
                new BigDecimal("12.34"),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 4),
                currency,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null);
    }

    private InstrumentEntity instrument(Long id, String ticker, String symbolKey, String currency) {
        InstrumentEntity instrument = new InstrumentEntity();
        instrument.setId(id);
        instrument.setTicker(ticker);
        instrument.setSymbolKey(symbolKey);
        instrument.setCurrency(currency);
        return instrument;
    }
}
