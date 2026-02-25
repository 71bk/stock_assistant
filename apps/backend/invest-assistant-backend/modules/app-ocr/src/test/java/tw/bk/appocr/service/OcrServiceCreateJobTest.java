package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import tw.bk.appcommon.enums.OcrJobStatus;
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
                ocrDraftService);
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
        when(dedupeService.findJobId(userId, "sha:abcdef", portfolioId)).thenReturn(Optional.of(901L));
        when(ocrJobRepository.findByIdAndUserId(901L, userId)).thenReturn(Optional.of(existing));

        OcrJobView result = service.createJob(userId, fileId, portfolioId, false);

        assertNotNull(result);
        assertEquals(901L, result.id());
        assertEquals(OcrJobStatus.DONE, result.status());
        verify(dedupeService).findJobId(userId, "sha:abcdef", portfolioId);
        verify(dedupeService, never()).reserve(eq(userId), any(String.class), eq(portfolioId));
        verify(statementRepository, never()).save(any(StatementEntity.class));
    }
}
