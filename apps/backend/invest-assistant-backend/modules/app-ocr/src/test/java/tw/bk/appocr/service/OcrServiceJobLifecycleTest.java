package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.appocr.queue.OcrDedupeService;
import tw.bk.appocr.queue.OcrQueueService;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;

@ExtendWith(MockitoExtension.class)
class OcrServiceJobLifecycleTest {

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
        ReflectionTestUtils.setField(service, "maxRunningMinutes", 30L);
    }

    @Test
    void retryJob_shouldRejectCompletedJobWithoutForce() {
        Long userId = 7L;
        Long jobId = 101L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), 201L);
        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.retryJob(userId, jobId, false));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertEquals("OCR job already completed", ex.getMessage());
    }

    @Test
    void retryJob_shouldResetStatementAndRequeue() {
        Long userId = 7L;
        Long jobId = 102L;
        Long statementId = 202L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.FAILED.name(), statementId);
        StatementEntity statement = statement(statementId, userId, StatementStatus.DRAFT.name());

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(statementId, userId)).thenReturn(Optional.of(statement));
        when(viewMapper.toJobView(job)).thenReturn(new OcrJobView(jobId, statementId, OcrJobStatus.QUEUED, 0, null));

        OcrJobView view = service.retryJob(userId, jobId, false);

        assertNotNull(view);
        assertEquals(OcrJobStatus.QUEUED, view.status());
        verify(statementTradeRepository).deleteByStatementId(statementId);
        ArgumentCaptor<StatementEntity> statementCaptor = ArgumentCaptor.forClass(StatementEntity.class);
        verify(statementRepository).save(statementCaptor.capture());
        assertEquals(StatementStatus.DRAFT.name(), statementCaptor.getValue().getStatus());
        assertEquals("{}", statementCaptor.getValue().getParsedJson());
        verify(ocrJobRepository).save(job);
        verify(queueService).enqueue(job);
    }

    @Test
    void reparse_shouldRejectWhenJobHasNoStatement() {
        Long userId = 7L;
        Long jobId = 103L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), null);

        when(ocrJobRepository.findByIdAndUserIdForUpdate(jobId, userId)).thenReturn(Optional.of(job));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.reparse(userId, jobId, false));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertEquals("OCR job has no statement", ex.getMessage());
    }

    @Test
    void reparse_shouldSupersedeOldStatementAndCreateNewOne() {
        Long userId = 7L;
        Long jobId = 104L;
        Long oldStatementId = 204L;
        Long newStatementId = 904L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), oldStatementId);
        StatementEntity oldStatement = statement(oldStatementId, userId, StatementStatus.DRAFT.name());
        oldStatement.setPortfolioId(33L);
        oldStatement.setSource("OCR");
        oldStatement.setFileId(55L);

        when(ocrJobRepository.findByIdAndUserIdForUpdate(jobId, userId)).thenReturn(Optional.of(job));
        when(statementRepository.findByIdAndUserId(oldStatementId, userId)).thenReturn(Optional.of(oldStatement));
        when(statementRepository.save(any(StatementEntity.class))).thenAnswer(invocation -> {
            StatementEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(newStatementId);
            }
            return entity;
        });
        when(viewMapper.toJobView(job)).thenReturn(new OcrJobView(jobId, newStatementId, OcrJobStatus.QUEUED, 0, null));

        OcrJobView view = service.reparse(userId, jobId, false);

        assertNotNull(view);
        assertEquals(newStatementId, view.statementId());
        ArgumentCaptor<StatementEntity> statementCaptor = ArgumentCaptor.forClass(StatementEntity.class);
        verify(statementRepository, org.mockito.Mockito.times(2)).save(statementCaptor.capture());
        List<StatementEntity> savedStatements = statementCaptor.getAllValues();
        StatementEntity savedOld = savedStatements.get(0);
        StatementEntity savedNew = savedStatements.get(1);
        assertEquals(StatementStatus.SUPERSEDED.name(), savedOld.getStatus());
        assertNotNull(savedOld.getSupersededAt());
        assertEquals(StatementStatus.DRAFT.name(), savedNew.getStatus());
        assertEquals("{}", savedNew.getParsedJson());
        assertEquals(oldStatement.getPortfolioId(), savedNew.getPortfolioId());
        assertEquals(oldStatement.getSource(), savedNew.getSource());
        assertEquals(oldStatement.getFileId(), savedNew.getFileId());

        ArgumentCaptor<OcrJobEntity> jobCaptor = ArgumentCaptor.forClass(OcrJobEntity.class);
        verify(ocrJobRepository).save(jobCaptor.capture());
        OcrJobEntity savedJob = jobCaptor.getValue();
        assertEquals(newStatementId, savedJob.getStatementId());
        assertEquals(OcrJobStatus.QUEUED.name(), savedJob.getStatus());
        assertEquals(0, savedJob.getProgress());
        verify(queueService).enqueue(savedJob);
    }

    @Test
    void cancel_shouldRejectRunningJobWithoutForce() {
        Long userId = 7L;
        Long jobId = 105L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.RUNNING.name(), 305L);

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(userId, jobId, false));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertEquals("OCR job is still running", ex.getMessage());
    }

    @Test
    void cancel_shouldReturnExistingJobForTerminalState() {
        Long userId = 7L;
        Long jobId = 106L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.DONE.name(), 306L);
        OcrJobView expected = new OcrJobView(jobId, 306L, OcrJobStatus.DONE, 100, null);

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(viewMapper.toJobView(job)).thenReturn(expected);

        OcrJobView view = service.cancel(userId, jobId, false);

        assertEquals(expected, view);
        verify(ocrJobRepository, never()).save(any(OcrJobEntity.class));
    }

    @Test
    void cancel_shouldMarkJobCancelledAndPersist() {
        Long userId = 7L;
        Long jobId = 107L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.QUEUED.name(), 307L);
        OcrJobView expected = new OcrJobView(jobId, 307L, OcrJobStatus.CANCELLED, 100, "Cancelled by user");

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(ocrJobRepository.save(job)).thenReturn(job);
        when(viewMapper.toJobView(job)).thenReturn(expected);

        OcrJobView view = service.cancel(userId, jobId, false);

        assertEquals(expected, view);
        assertEquals(OcrJobStatus.CANCELLED.name(), job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals("Cancelled by user", job.getErrorMessage());
        verify(ocrJobRepository).save(job);
    }

    private OcrJobEntity job(Long id, Long userId, String status, Long statementId) {
        OcrJobEntity entity = new OcrJobEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setStatus(status);
        entity.setStatementId(statementId);
        entity.setProgress(100);
        entity.setUpdatedAt(OffsetDateTime.now().minusHours(2));
        return entity;
    }

    private StatementEntity statement(Long id, Long userId, String status) {
        StatementEntity entity = new StatementEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setStatus(status);
        return entity;
    }
}
