package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.appocr.queue.OcrDedupeService;
import tw.bk.appocr.queue.OcrQueueService;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;

@ExtendWith(MockitoExtension.class)
class OcrServiceCreateJobTest {

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
    void createJob_shouldFallbackToFileIdDedupeKeyWhenShaMissing() {
        Long userId = 7L;
        Long fileId = 11L;
        Long portfolioId = 13L;

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setUserId(userId);
        file.setSha256("   ");

        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(portfolioId);
        portfolio.setUserId(userId);

        when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(file));
        when(portfolioRepository.findByIdAndUserId(portfolioId, userId)).thenReturn(Optional.of(portfolio));
        when(dedupeContentKeyResolver.resolve(file)).thenReturn("file-id:11");
        when(dedupeService.findJobId(userId, "file-id:11", portfolioId)).thenReturn(Optional.empty());
        when(dedupeService.reserve(userId, "file-id:11", portfolioId)).thenReturn(true);
        when(statementRepository.save(any(StatementEntity.class))).thenAnswer(invocation -> {
            StatementEntity statement = invocation.getArgument(0);
            statement.setId(21L);
            return statement;
        });
        when(ocrJobRepository.save(any(OcrJobEntity.class))).thenAnswer(invocation -> {
            OcrJobEntity job = invocation.getArgument(0);
            job.setId(31L);
            return job;
        });
        when(viewMapper.toJobView(any(OcrJobEntity.class))).thenAnswer(invocation -> {
            OcrJobEntity job = invocation.getArgument(0);
            return new OcrJobView(
                    job.getId(),
                    job.getStatementId(),
                    job.getStatusEnum(),
                    job.getProgress(),
                    job.getErrorMessage());
        });

        OcrJobView result = service.createJob(userId, fileId, portfolioId, false);

        assertNotNull(result);
        assertEquals(31L, result.id());
        assertEquals(OcrJobStatus.QUEUED, result.status());
        verify(dedupeService).findJobId(userId, "file-id:11", portfolioId);
        verify(dedupeService).reserve(userId, "file-id:11", portfolioId);
        verify(dedupeService).store(userId, "file-id:11", portfolioId, 31L);
        verify(queueService).enqueue(any(OcrJobEntity.class));
    }

    @Test
    void createJob_shouldNormalizeShaWhenCheckingExistingJob() {
        Long userId = 7L;
        Long fileId = 15L;
        Long portfolioId = 19L;

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setUserId(userId);
        file.setSha256(" AbCdEf ");

        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(portfolioId);
        portfolio.setUserId(userId);

        OcrJobEntity existing = new OcrJobEntity();
        existing.setId(901L);
        existing.setStatus(OcrJobStatus.DONE.name());
        existing.setProgress(100);
        existing.setFileId(fileId);

        when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(file));
        when(portfolioRepository.findByIdAndUserId(portfolioId, userId)).thenReturn(Optional.of(portfolio));
        when(dedupeContentKeyResolver.resolve(file)).thenReturn("sha:abcdef");
        when(dedupeService.findJobId(userId, "sha:abcdef", portfolioId)).thenReturn(Optional.of(901L));
        when(ocrJobRepository.findByIdAndUserId(901L, userId)).thenReturn(Optional.of(existing));
        when(viewMapper.toJobView(existing)).thenReturn(new OcrJobView(
                existing.getId(),
                existing.getStatementId(),
                existing.getStatusEnum(),
                existing.getProgress(),
                existing.getErrorMessage()));

        OcrJobView result = service.createJob(userId, fileId, portfolioId, false);

        assertNotNull(result);
        assertEquals(901L, result.id());
        assertEquals(OcrJobStatus.DONE, result.status());
        verify(dedupeService).findJobId(userId, "sha:abcdef", portfolioId);
        verify(dedupeService, never()).reserve(eq(userId), any(String.class), eq(portfolioId));
        verify(statementRepository, never()).save(any(StatementEntity.class));
    }

    @Test
    void createJob_shouldRequeueExistingFailedJob() {
        Long userId = 7L;
        Long fileId = 21L;
        Long portfolioId = 22L;

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setUserId(userId);
        file.setSha256("deadbeef");

        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(portfolioId);
        portfolio.setUserId(userId);

        OcrJobEntity existing = new OcrJobEntity();
        existing.setId(902L);
        existing.setStatus(OcrJobStatus.FAILED.name());
        existing.setProgress(100);
        existing.setErrorMessage("boom");
        existing.setFileId(fileId);

        when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(file));
        when(portfolioRepository.findByIdAndUserId(portfolioId, userId)).thenReturn(Optional.of(portfolio));
        when(dedupeContentKeyResolver.resolve(file)).thenReturn("sha:deadbeef");
        when(dedupeService.findJobId(userId, "sha:deadbeef", portfolioId)).thenReturn(Optional.of(902L));
        when(ocrJobRepository.findByIdAndUserId(902L, userId)).thenReturn(Optional.of(existing));
        when(viewMapper.toJobView(existing)).thenAnswer(invocation -> {
            OcrJobEntity job = invocation.getArgument(0);
            return new OcrJobView(
                    job.getId(),
                    job.getStatementId(),
                    job.getStatusEnum(),
                    job.getProgress(),
                    job.getErrorMessage());
        });

        OcrJobView result = service.createJob(userId, fileId, portfolioId, false);

        assertEquals(OcrJobStatus.QUEUED, result.status());
        assertEquals(0, result.progress());
        assertEquals(null, result.errorMessage());
        verify(ocrJobRepository).save(existing);
        verify(queueService).enqueue(existing);
        verify(dedupeService, never()).reserve(eq(userId), any(String.class), eq(portfolioId));
    }

    @Test
    void createJob_shouldThrowConflictWhenReserveFailsAndNoVisibleJob() {
        Long userId = 7L;
        Long fileId = 31L;
        Long portfolioId = 32L;

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setUserId(userId);
        file.setSha256("abc123");

        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(portfolioId);
        portfolio.setUserId(userId);

        when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(file));
        when(portfolioRepository.findByIdAndUserId(portfolioId, userId)).thenReturn(Optional.of(portfolio));
        when(dedupeContentKeyResolver.resolve(file)).thenReturn("sha:abc123");
        when(dedupeService.findJobId(userId, "sha:abc123", portfolioId))
                .thenReturn(Optional.empty(), Optional.empty());
        when(dedupeService.reserve(userId, "sha:abc123", portfolioId)).thenReturn(false);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.createJob(userId, fileId, portfolioId, false));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(statementRepository, never()).save(any(StatementEntity.class));
        verify(ocrJobRepository, never()).save(any(OcrJobEntity.class));
        verify(queueService, never()).enqueue(any(OcrJobEntity.class));
    }

    @Test
    void createJob_shouldReturnVisibleJobWhenReserveFailsButJobAppears() {
        Long userId = 7L;
        Long fileId = 41L;
        Long portfolioId = 42L;

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setUserId(userId);
        file.setSha256("def456");

        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(portfolioId);
        portfolio.setUserId(userId);

        OcrJobEntity existing = new OcrJobEntity();
        existing.setId(903L);
        existing.setStatus(OcrJobStatus.QUEUED.name());
        existing.setProgress(0);
        existing.setFileId(fileId);

        when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(file));
        when(portfolioRepository.findByIdAndUserId(portfolioId, userId)).thenReturn(Optional.of(portfolio));
        when(dedupeContentKeyResolver.resolve(file)).thenReturn("sha:def456");
        when(dedupeService.findJobId(userId, "sha:def456", portfolioId))
                .thenReturn(Optional.empty(), Optional.of(903L));
        when(dedupeService.reserve(userId, "sha:def456", portfolioId)).thenReturn(false);
        when(ocrJobRepository.findByIdAndUserId(903L, userId)).thenReturn(Optional.of(existing));
        when(viewMapper.toJobView(existing)).thenReturn(new OcrJobView(
                existing.getId(),
                existing.getStatementId(),
                existing.getStatusEnum(),
                existing.getProgress(),
                existing.getErrorMessage()));

        OcrJobView result = service.createJob(userId, fileId, portfolioId, false);

        assertEquals(903L, result.id());
        assertEquals(OcrJobStatus.QUEUED, result.status());
        verify(statementRepository, never()).save(any(StatementEntity.class));
    }

    @Test
    void createJob_shouldSkipDedupeWhenForceTrue() {
        Long userId = 7L;
        Long fileId = 51L;
        Long portfolioId = 52L;

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setUserId(userId);
        file.setSha256("aa11bb22");

        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(portfolioId);
        portfolio.setUserId(userId);

        when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(file));
        when(portfolioRepository.findByIdAndUserId(portfolioId, userId)).thenReturn(Optional.of(portfolio));
        when(dedupeContentKeyResolver.resolve(file)).thenReturn("sha:aa11bb22");
        when(statementRepository.save(any(StatementEntity.class))).thenAnswer(invocation -> {
            StatementEntity statement = invocation.getArgument(0);
            statement.setId(61L);
            return statement;
        });
        when(ocrJobRepository.save(any(OcrJobEntity.class))).thenAnswer(invocation -> {
            OcrJobEntity job = invocation.getArgument(0);
            job.setId(71L);
            return job;
        });
        when(viewMapper.toJobView(any(OcrJobEntity.class))).thenAnswer(invocation -> {
            OcrJobEntity job = invocation.getArgument(0);
            return new OcrJobView(
                    job.getId(),
                    job.getStatementId(),
                    job.getStatusEnum(),
                    job.getProgress(),
                    job.getErrorMessage());
        });

        OcrJobView result = service.createJob(userId, fileId, portfolioId, true);

        assertEquals(71L, result.id());
        assertEquals(OcrJobStatus.QUEUED, result.status());
        verify(dedupeService, never()).findJobId(eq(userId), any(String.class), eq(portfolioId));
        verify(dedupeService, never()).reserve(eq(userId), any(String.class), eq(portfolioId));
        verify(dedupeService, never()).store(eq(userId), any(String.class), eq(portfolioId), any(Long.class));
        verify(queueService).enqueue(any(OcrJobEntity.class));
    }
}
