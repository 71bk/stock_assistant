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
class OcrServiceDeleteDraftTest {

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
    void deleteDraft_shouldRejectWhenDraftNotFound() {
        Long userId = 7L;
        Long draftId = 1001L;
        when(statementTradeRepository.findById(draftId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteDraft(userId, draftId));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void deleteDraft_shouldRejectWhenJobNotCompleted() {
        Long userId = 7L;
        Long draftId = 1002L;
        Long statementId = 2002L;
        StatementTradeEntity draft = draft(draftId, statementId);
        StatementEntity statement = statement(statementId, userId);
        OcrJobEntity job = job(3002L, OcrJobStatus.RUNNING.name());

        when(statementTradeRepository.findById(draftId)).thenReturn(Optional.of(draft));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(ocrJobRepository.findByStatementId(statementId)).thenReturn(Optional.of(job));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteDraft(userId, draftId));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(statementTradeRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteDraft_shouldDeleteOnlyWhenRemainingDraftsExist() {
        Long userId = 7L;
        Long draftId = 1003L;
        Long statementId = 2003L;
        StatementTradeEntity draft = draft(draftId, statementId);
        StatementTradeEntity remaining = draft(8888L, statementId);
        StatementEntity statement = statement(statementId, userId);
        OcrJobEntity job = job(3003L, OcrJobStatus.DONE.name());

        when(statementTradeRepository.findById(draftId)).thenReturn(Optional.of(draft));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(ocrJobRepository.findByStatementId(statementId)).thenReturn(Optional.of(job));
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId)).thenReturn(List.of(remaining));

        service.deleteDraft(userId, draftId);

        verify(statementTradeRepository).deleteById(draftId);
        verify(statementRepository, never()).save(any(StatementEntity.class));
        verify(ocrJobRepository, never()).updateStatusIfNotCancelled(
                anyLong(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    void deleteDraft_shouldConfirmStatementAndUpdateJobWhenLastDraftRemoved() {
        Long userId = 7L;
        Long draftId = 1004L;
        Long statementId = 2004L;
        Long jobId = 3004L;
        StatementTradeEntity draft = draft(draftId, statementId);
        StatementEntity statement = statement(statementId, userId);
        OcrJobEntity job = job(jobId, OcrJobStatus.DONE.name());

        when(statementTradeRepository.findById(draftId)).thenReturn(Optional.of(draft));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(ocrJobRepository.findByStatementId(statementId)).thenReturn(Optional.of(job));
        when(statementTradeRepository.findByStatementIdOrderByIdAsc(statementId)).thenReturn(List.of());
        when(ocrJobRepository.updateStatusIfNotCancelled(
                jobId, OcrJobStatus.DONE.name(), 100, null, OcrJobStatus.CANCELLED.name()))
                .thenReturn(1);

        service.deleteDraft(userId, draftId);

        verify(statementTradeRepository).deleteById(draftId);
        ArgumentCaptor<StatementEntity> statementCaptor = ArgumentCaptor.forClass(StatementEntity.class);
        verify(statementRepository).save(statementCaptor.capture());
        assertEquals(StatementStatus.CONFIRMED.name(), statementCaptor.getValue().getStatus());
        verify(ocrJobRepository).updateStatusIfNotCancelled(
                jobId, OcrJobStatus.DONE.name(), 100, null, OcrJobStatus.CANCELLED.name());
    }

    private StatementTradeEntity draft(Long id, Long statementId) {
        StatementTradeEntity entity = new StatementTradeEntity();
        entity.setId(id);
        entity.setStatementId(statementId);
        return entity;
    }

    private StatementEntity statement(Long id, Long userId) {
        StatementEntity entity = new StatementEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setStatus(StatementStatus.DRAFT.name());
        return entity;
    }

    private OcrJobEntity job(Long id, String status) {
        OcrJobEntity entity = new OcrJobEntity();
        entity.setId(id);
        entity.setStatus(status);
        return entity;
    }
}
