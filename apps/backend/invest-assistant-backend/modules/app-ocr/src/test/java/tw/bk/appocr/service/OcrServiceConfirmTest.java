package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;

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
    private OcrQueueService queueService;
    @Mock
    private OcrDedupeService dedupeService;
    @Mock
    private OcrPdfPasswordVault pdfPasswordVault;
    @Mock
    private OcrJobProcessor jobProcessor;
    @Mock
    private OcrDraftService ocrDraftService;
    @Mock
    private OcrDedupeContentKeyResolver dedupeContentKeyResolver;
    @Mock
    private OcrImportTxService importTxService;
    @Mock
    private OcrViewMapper viewMapper;

    private OcrService service;

    @BeforeEach
    void setUp() {
        service = OcrServiceTestFactory.create(
                fileRepository,
                statementRepository,
                statementTradeRepository,
                ocrJobRepository,
                portfolioRepository,
                queueService,
                dedupeService,
                pdfPasswordVault,
                jobProcessor,
                ocrDraftService,
                dedupeContentKeyResolver,
                importTxService,
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

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId))
                .thenReturn(List.of(draft1, draft2), List.of(draft1));
        when(ocrDraftService.isDuplicateDraft(draft2, portfolioId)).thenReturn(false);

        ConfirmResult result = service.confirm(userId, jobId, Set.of(12L));

        assertEquals(1, result.getImportedCount());
        assertEquals(0, result.getErrors().size());
        verify(importTxService).importDraft(userId, portfolioId, draft2);
        verify(importTxService, never()).importDraft(userId, portfolioId, draft1);
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

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId))
                .thenReturn(List.of(draft), List.of());
        when(ocrDraftService.isDuplicateDraft(draft, portfolioId)).thenReturn(false);
        when(ocrJobRepository.updateStatusIfNotCancelled(
                jobId, OcrJobStatus.DONE.name(), 100, null, OcrJobStatus.CANCELLED.name()))
                .thenReturn(1);

        ConfirmResult result = service.confirm(userId, jobId, null);

        assertEquals(1, result.getImportedCount());
        verify(importTxService).importDraft(userId, portfolioId, draft);
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
        assertEquals(31L, result.getErrors().get(0).draftId());
        verify(ocrDraftService, never()).isDuplicateDraft(any(StatementTradeEntity.class), anyLong());
        verify(importTxService, never()).importDraft(anyLong(), anyLong(), any(StatementTradeEntity.class));
    }

    @Test
    void confirm_shouldIsolateFailedDraftAndStillImportOthers() {
        // Regression guard for the rollback-only contamination bug: a single draft
        // failing its (REQUIRES_NEW) import must be recorded as a per-draft error
        // without aborting the rest of the batch.
        Long userId = 7L;
        Long jobId = 105L;
        Long statementId = 205L;
        Long portfolioId = 306L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), statementId);
        StatementEntity statement = statement(statementId, userId, portfolioId);
        StatementTradeEntity failing = draft(41L, statementId, 1004L);
        StatementTradeEntity ok = draft(42L, statementId, 1005L);

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        // After processing, the failed draft remains; the imported one is gone.
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId))
                .thenReturn(List.of(failing, ok), List.of(failing));
        when(ocrDraftService.isDuplicateDraft(failing, portfolioId)).thenReturn(false);
        when(ocrDraftService.isDuplicateDraft(ok, portfolioId)).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.CONFLICT, "幣別不符"))
                .when(importTxService).importDraft(userId, portfolioId, failing);

        ConfirmResult result = service.confirm(userId, jobId, null);

        assertEquals(1, result.getImportedCount());
        assertEquals(1, result.getErrors().size());
        assertEquals(41L, result.getErrors().get(0).draftId());
        assertEquals("幣別不符", result.getErrors().get(0).reason());
        verify(importTxService).importDraft(userId, portfolioId, failing);
        verify(importTxService).importDraft(userId, portfolioId, ok);
        // Not all drafts imported -> statement must NOT be marked confirmed.
        verify(statementRepository, never()).save(any(StatementEntity.class));
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
