package tw.bk.appocr.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.client.AiWorkerOcrClient;
import tw.bk.appocr.client.AiWorkerOcrResponse;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.StatementRepository;

@ExtendWith(MockitoExtension.class)
class OcrJobProcessorTest {

    @Mock
    private OcrJobRepository ocrJobRepository;
    @Mock
    private StatementRepository statementRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private AiWorkerOcrClient aiWorkerOcrClient;
    @Mock
    private OcrFileService ocrFileService;
    @Mock
    private OcrDraftService ocrDraftService;
    @Mock
    private OcrPdfPasswordVault pdfPasswordVault;

    private OcrJobProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OcrJobProcessor(
                ocrJobRepository,
                statementRepository,
                fileRepository,
                aiWorkerOcrClient,
                new ObjectMapper(),
                ocrFileService,
                ocrDraftService,
                pdfPasswordVault);
        ReflectionTestUtils.setField(processor, "maxRunningMinutes", 30L);
    }

    @Test
    void processJob_shouldMarkPasswordRequiredWhenWorkerRequiresPassword() {
        Long userId = 7L;
        Long jobId = 101L;
        byte[] content = new byte[] {1, 2, 3};
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.QUEUED.name());
        StatementEntity statement = statement(job.getStatementId(), userId);
        FileEntity file = file(job.getFileId(), userId);

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(ocrJobRepository.claimForRunning(
                eq(jobId),
                eq(OcrJobStatus.RUNNING.name()),
                eq(5),
                isNull(),
                eq(OcrJobStatus.CANCELLED.name()),
                eq(OcrJobStatus.RUNNING.name()),
                any(OffsetDateTime.class),
                any())).thenReturn(1);
        when(statementRepository.findByIdAndUserId(job.getStatementId(), userId)).thenReturn(Optional.of(statement));
        when(fileRepository.findByIdAndUserId(job.getFileId(), userId)).thenReturn(Optional.of(file));
        when(ocrFileService.loadFileBytes(file)).thenReturn(content);
        when(aiWorkerOcrClient.processFile(userId, file, content, null))
                .thenThrow(new BusinessException(ErrorCode.PDF_PASSWORD_REQUIRED, "password required"));

        processor.processJob(userId, jobId);

        verify(ocrJobRepository).updateStatusIfNotCancelled(
                eq(jobId),
                eq(OcrJobStatus.PASSWORD_REQUIRED.name()),
                eq(0),
                anyString(),
                eq(OcrJobStatus.CANCELLED.name()));
        verify(ocrDraftService, never()).saveDrafts(any(), any());
    }

    @Test
    void processJob_shouldMarkPasswordInvalidWhenWorkerRejectsPassword() {
        Long userId = 7L;
        Long jobId = 102L;
        byte[] content = new byte[] {1, 2, 3};
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.PASSWORD_REQUIRED.name());
        StatementEntity statement = statement(job.getStatementId(), userId);
        FileEntity file = file(job.getFileId(), userId);

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        when(ocrJobRepository.claimForRunning(
                eq(jobId),
                eq(OcrJobStatus.RUNNING.name()),
                eq(5),
                isNull(),
                eq(OcrJobStatus.CANCELLED.name()),
                eq(OcrJobStatus.RUNNING.name()),
                any(OffsetDateTime.class),
                any())).thenReturn(1);
        when(statementRepository.findByIdAndUserId(job.getStatementId(), userId)).thenReturn(Optional.of(statement));
        when(fileRepository.findByIdAndUserId(job.getFileId(), userId)).thenReturn(Optional.of(file));
        when(ocrFileService.loadFileBytes(file)).thenReturn(content);
        when(aiWorkerOcrClient.processFile(userId, file, content, "secret"))
                .thenThrow(new BusinessException(ErrorCode.PDF_PASSWORD_INVALID, "password invalid"));

        processor.processJob(userId, jobId, "secret");

        verify(ocrJobRepository).updateStatusIfNotCancelled(
                eq(jobId),
                eq(OcrJobStatus.PASSWORD_INVALID.name()),
                eq(0),
                anyString(),
                eq(OcrJobStatus.CANCELLED.name()));
        verify(ocrDraftService, never()).saveDrafts(any(), any());
    }

    @Test
    void processJob_shouldSkipPasswordWaitingJobWhenNoPasswordProvided() {
        Long userId = 7L;
        Long jobId = 103L;
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.PASSWORD_REQUIRED.name());

        when(ocrJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        processor.processJob(userId, jobId);

        verify(ocrJobRepository, never()).claimForRunning(any(), any(), any(), any(), any(), any(), any(), any());
        verify(aiWorkerOcrClient, never()).processFile(any(), any(), any(), any());
    }

    @Test
    void processJob_shouldConsumeVaultPasswordWhenQueuedFromPasswordSubmit() {
        Long userId = 7L;
        Long jobId = 104L;
        byte[] content = new byte[] {1, 2, 3};
        OcrJobEntity job = job(jobId, userId, OcrJobStatus.QUEUED.name());
        OcrJobEntity latest = job(jobId, userId, OcrJobStatus.RUNNING.name());
        StatementEntity statement = statement(job.getStatementId(), userId);
        FileEntity file = file(job.getFileId(), userId);
        AiWorkerOcrResponse response = new AiWorkerOcrResponse(
                "raw text",
                List.of(),
                BigDecimal.ONE,
                List.of(),
                null,
                null,
                "vision");

        when(ocrJobRepository.findByIdAndUserId(jobId, userId))
                .thenReturn(Optional.of(job), Optional.of(latest));
        when(pdfPasswordVault.contains(userId, jobId)).thenReturn(true);
        when(ocrJobRepository.claimForRunning(
                eq(jobId),
                eq(OcrJobStatus.RUNNING.name()),
                eq(5),
                isNull(),
                eq(OcrJobStatus.CANCELLED.name()),
                eq(OcrJobStatus.RUNNING.name()),
                any(OffsetDateTime.class),
                any())).thenReturn(1);
        when(pdfPasswordVault.consume(userId, jobId)).thenReturn(Optional.of("secret"));
        when(statementRepository.findByIdAndUserId(job.getStatementId(), userId)).thenReturn(Optional.of(statement));
        when(fileRepository.findByIdAndUserId(job.getFileId(), userId)).thenReturn(Optional.of(file));
        when(ocrFileService.loadFileBytes(file)).thenReturn(content);
        when(aiWorkerOcrClient.processFile(userId, file, content, "secret")).thenReturn(response);
        when(ocrJobRepository.updateStatusIfNotCancelled(
                eq(jobId),
                eq(OcrJobStatus.DONE.name()),
                eq(100),
                isNull(),
                eq(OcrJobStatus.CANCELLED.name()))).thenReturn(1);

        processor.processJob(userId, jobId);

        verify(pdfPasswordVault).consume(userId, jobId);
        verify(aiWorkerOcrClient).processFile(userId, file, content, "secret");
        verify(ocrDraftService).saveDrafts(statement, List.of());
        verify(pdfPasswordVault).discard(userId, jobId);
    }

    private OcrJobEntity job(Long id, Long userId, String status) {
        OcrJobEntity entity = new OcrJobEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setFileId(301L);
        entity.setStatementId(201L);
        entity.setStatus(status);
        entity.setProgress(0);
        entity.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        return entity;
    }

    private StatementEntity statement(Long id, Long userId) {
        StatementEntity entity = new StatementEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setStatus(StatementStatus.DRAFT.name());
        return entity;
    }

    private FileEntity file(Long id, Long userId) {
        FileEntity entity = new FileEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setObjectKey("statement.pdf");
        entity.setContentType("application/pdf");
        return entity;
    }
}
