package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.model.ConfirmResult;
import tw.bk.appocr.queue.OcrDedupeService;
import tw.bk.appocr.queue.OcrQueueService;
import tw.bk.appportfolio.model.TradeCommand;
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
class OcrServiceConfirmTest {

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
    void confirm_shouldRejectWhenJobNotCompleted() {
        Long userId = 7L;
        Long jobId = 101L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.RUNNING.name(), 201L);
        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirm(userId, jobId, null));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void confirm_shouldImportOnlySelectedDrafts() {
        Long userId = 7L;
        Long jobId = 102L;
        Long statementId = 202L;
        Long portfolioId = 303L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), statementId);
        StatementEntity statement = statement(statementId, userId, portfolioId);
        StatementTradeEntity draft1 = draft(11L, statementId, 1001L);
        StatementTradeEntity draft2 = draft(12L, statementId, 1002L);
        TradeCommand command = new TradeCommand(null, null, null, null, null, null, null, null, null, null, "OCR");

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId))
                .thenReturn(List.of(draft1, draft2), List.of(draft1));
        when(ocrDraftService.isDuplicateDraft(draft2, portfolioId)).thenReturn(false);
        when(tradeCommandFactory.toTradeCommand(draft2)).thenReturn(command);

        ConfirmResult result = service.confirm(userId, jobId, Set.of(12L));

        assertEquals(1, result.getImportedCount());
        assertEquals(0, result.getErrors().size());
        verify(portfolioService).createTrade(userId, portfolioId, command);
        verify(statementTradeRepository).deleteById(12L);
        verify(statementTradeRepository, never()).deleteById(11L);
        verify(statementRepository, never()).save(any(StatementEntity.class));
        verify(ocrJobRepository, never()).updateStatusIfNotCancelled(
                anyLong(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    void confirm_shouldMarkStatementConfirmedWhenAllDraftsImported() {
        Long userId = 7L;
        Long jobId = 103L;
        Long statementId = 203L;
        Long portfolioId = 304L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), statementId);
        StatementEntity statement = statement(statementId, userId, portfolioId);
        StatementTradeEntity draft = draft(21L, statementId, 1003L);
        TradeCommand command = new TradeCommand(null, null, null, null, null, null, null, null, null, null, "OCR");

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId))
                .thenReturn(List.of(draft), List.of());
        when(ocrDraftService.isDuplicateDraft(draft, portfolioId)).thenReturn(false);
        when(tradeCommandFactory.toTradeCommand(draft)).thenReturn(command);
        when(ocrJobRepository.updateStatusIfNotCancelled(
                jobId, OcrJobStatus.DONE.name(), 100, null, OcrJobStatus.CANCELLED.name()))
                .thenReturn(1);

        ConfirmResult result = service.confirm(userId, jobId, null);

        assertEquals(1, result.getImportedCount());
        verify(statementTradeRepository).deleteById(21L);
        ArgumentCaptor<StatementEntity> statementCaptor = ArgumentCaptor.forClass(StatementEntity.class);
        verify(statementRepository).save(statementCaptor.capture());
        assertEquals(StatementStatus.CONFIRMED.name(), statementCaptor.getValue().getStatus());
        verify(ocrJobRepository).updateStatusIfNotCancelled(
                jobId, OcrJobStatus.DONE.name(), 100, null, OcrJobStatus.CANCELLED.name());
    }

    @Test
    void confirm_shouldCollectErrorsAndSkipInvalidDraft() {
        Long userId = 7L;
        Long jobId = 104L;
        Long statementId = 204L;
        Long portfolioId = 305L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), statementId);
        StatementEntity statement = statement(statementId, userId, portfolioId);
        StatementTradeEntity invalidDraft = draft(31L, statementId, null);

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId))
                .thenReturn(List.of(invalidDraft), List.of(invalidDraft));

        ConfirmResult result = service.confirm(userId, jobId, null);

        assertEquals(0, result.getImportedCount());
        assertEquals(1, result.getErrors().size());
        assertEquals(31L, result.getErrors().get(0).getDraftId());
        verify(ocrDraftService, never()).isDuplicateDraft(any(StatementTradeEntity.class), anyLong());
        verify(portfolioService, never()).createTrade(anyLong(), anyLong(), any(TradeCommand.class));
        verify(statementTradeRepository, never()).deleteById(anyLong());
    }

    private OcrJobEntity job(Long id, Long userId, String status, Long statementId) {
        OcrJobEntity entity = new OcrJobEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setStatus(status);
        entity.setStatementId(statementId);
        entity.setProgress(100);
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

    private StatementTradeEntity draft(Long id, Long statementId, Long instrumentId) {
        StatementTradeEntity entity = new StatementTradeEntity();
        entity.setId(id);
        entity.setStatementId(statementId);
        entity.setInstrumentId(instrumentId);
        return entity;
    }
}
