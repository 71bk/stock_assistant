package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appocr.model.OcrDraftUpdate;
import tw.bk.appocr.model.OcrDraftView;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.appocr.queue.OcrDedupeService;
import tw.bk.appocr.queue.OcrQueueService;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;

@ExtendWith(MockitoExtension.class)
class OcrServiceQueryAndDelegationTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private StatementRepository statementRepository;
    @Mock
    private StatementTradeRepository statementTradeRepository;
    @Mock
    private OcrJobRepository ocrJobRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioService portfolioService;
    @Mock
    private OcrQueueService queueService;
    @Mock
    private OcrDedupeService dedupeService;
    @Mock
    private StockTradeRepository stockTradeRepository;
    @Mock
    private OcrJobProcessor jobProcessor;
    @Mock
    private OcrDraftService ocrDraftService;
    @Mock
    private OcrDedupeContentKeyResolver dedupeContentKeyResolver;
    @Mock
    private OcrTradeCommandFactory tradeCommandFactory;
    @Mock
    private OcrViewMapper viewMapper;

    private OcrService service;

    @BeforeEach
    void setUp() {
        service = new OcrService(
                fileRepository,
                statementRepository,
                statementTradeRepository,
                ocrJobRepository,
                portfolioRepository,
                portfolioService,
                queueService,
                dedupeService,
                stockTradeRepository,
                jobProcessor,
                ocrDraftService,
                dedupeContentKeyResolver,
                tradeCommandFactory,
                viewMapper);
    }

    @Test
    void processJob_shouldDelegateToProcessor() {
        service.processJob(7L, 101L);

        verify(jobProcessor).processJob(7L, 101L);
    }

    @Test
    void getJob_shouldLoadAndMapView() {
        Long userId = 7L;
        Long jobId = 102L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.RUNNING.name(), 202L);
        OcrJobView expected = new OcrJobView(jobId, 202L, OcrJobStatus.RUNNING, 33, null);

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(viewMapper.toJobView(job)).thenReturn(expected);

        OcrJobView view = service.getJob(userId, jobId);

        assertEquals(expected, view);
    }

    @Test
    void getDrafts_shouldReturnEmptyWhenJobHasNoStatement() {
        Long userId = 7L;
        Long jobId = 103L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), null);
        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        List<OcrDraftView> views = service.getDrafts(userId, jobId);

        assertNotNull(views);
        assertEquals(0, views.size());
        verify(statementRepository, never()).findByIdAndUserId(any(), any());
        verify(statementTradeRepository, never()).findByStatementIdOrderByIdAsc(any());
    }

    @Test
    void getDrafts_shouldMapDraftViewsWhenStatementExists() {
        Long userId = 7L;
        Long jobId = 104L;
        Long statementId = 204L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), statementId);
        StatementEntity statement = statement(statementId, userId, 304L);
        StatementTradeEntity draft = draft(11L, statementId);
        OcrDraftView expected = new OcrDraftView(
                11L, null, "2330", "TSMC", LocalDate.parse("2026-01-03"), LocalDate.parse("2026-01-05"),
                TradeSide.BUY, new BigDecimal("10.000000"), new BigDecimal("100.00000000"),
                "TWD", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1000.000000"), "[]", "[]", "hash-1");

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId)).thenReturn(List.of(draft));
        when(viewMapper.toDraftView(draft)).thenReturn(expected);

        List<OcrDraftView> views = service.getDrafts(userId, jobId);

        assertEquals(1, views.size());
        assertEquals(expected, views.get(0));
    }

    @Test
    void getPortfolioIdByJob_shouldReturnNullWhenJobHasNoStatement() {
        Long userId = 7L;
        Long jobId = 105L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), null);
        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        Long portfolioId = service.getPortfolioIdByJob(userId, jobId);

        assertEquals(null, portfolioId);
    }

    @Test
    void getPortfolioIdByJob_shouldReturnStatementPortfolioId() {
        Long userId = 7L;
        Long jobId = 106L;
        Long statementId = 206L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), statementId);
        StatementEntity statement = statement(statementId, userId, 306L);

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));

        Long portfolioId = service.getPortfolioIdByJob(userId, jobId);

        assertEquals(306L, portfolioId);
    }

    @Test
    void getPortfolioIdByStatementId_shouldReturnNullWhenStatementIdNull() {
        Long portfolioId = service.getPortfolioIdByStatementId(7L, null);

        assertEquals(null, portfolioId);
    }

    @Test
    void getPortfolioIdByStatementId_shouldReturnPortfolioId() {
        Long userId = 7L;
        Long statementId = 207L;
        StatementEntity statement = statement(statementId, userId, 307L);
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));

        Long portfolioId = service.getPortfolioIdByStatementId(userId, statementId);

        assertEquals(307L, portfolioId);
    }

    @Test
    void updateDraft_shouldDelegateToDraftServiceAndMapView() {
        Long userId = 7L;
        Long draftId = 108L;
        OcrDraftUpdate update = new OcrDraftUpdate(
                1001L,
                "2330",
                "TSMC",
                LocalDate.parse("2026-01-03"),
                LocalDate.parse("2026-01-05"),
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("100"),
                "TWD",
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        StatementTradeEntity entity = draft(draftId, 208L);
        OcrDraftView expected = new OcrDraftView(
                draftId, 1001L, "2330", "TSMC", LocalDate.parse("2026-01-03"), LocalDate.parse("2026-01-05"),
                TradeSide.BUY, new BigDecimal("10"), new BigDecimal("100"), "TWD",
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1000"), "[]", "[]", "hash-2");

        when(ocrDraftService.updateDraft(userId, draftId, update)).thenReturn(entity);
        when(viewMapper.toDraftView(entity)).thenReturn(expected);

        OcrDraftView view = service.updateDraft(userId, draftId, update);

        assertEquals(expected, view);
        verify(ocrDraftService).updateDraft(userId, draftId, update);
        verify(viewMapper).toDraftView(entity);
    }

    private OcrJobEntity job(Long id, Long userId, String status, Long statementId) {
        OcrJobEntity entity = new OcrJobEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setStatus(status);
        entity.setStatementId(statementId);
        entity.setProgress(33);
        return entity;
    }

    private StatementEntity statement(Long id, Long userId, Long portfolioId) {
        StatementEntity entity = new StatementEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setPortfolioId(portfolioId);
        entity.setStatus(StatementStatus.DRAFT.name());
        return entity;
    }

    private StatementTradeEntity draft(Long id, Long statementId) {
        StatementTradeEntity entity = new StatementTradeEntity();
        entity.setId(id);
        entity.setStatementId(statementId);
        entity.setRawTicker("2330");
        entity.setName("TSMC");
        entity.setTradeDate(LocalDate.parse("2026-01-03"));
        entity.setSettlementDate(LocalDate.parse("2026-01-05"));
        entity.setSide(TradeSide.BUY.name());
        entity.setQuantity(new BigDecimal("10.000000"));
        entity.setPrice(new BigDecimal("100.00000000"));
        entity.setCurrency("TWD");
        entity.setFee(BigDecimal.ZERO);
        entity.setTax(BigDecimal.ZERO);
        entity.setNetAmount(new BigDecimal("-1000.000000"));
        entity.setWarningsJson("[]");
        entity.setErrorsJson("[]");
        entity.setRowHash("hash-1");
        return entity;
    }
}
