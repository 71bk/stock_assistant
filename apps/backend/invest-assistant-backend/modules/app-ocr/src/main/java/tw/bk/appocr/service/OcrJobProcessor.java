package tw.bk.appocr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrJobProcessor {
    private static final String JOB_QUEUED = OcrJobStatus.QUEUED.name();
    private static final String JOB_RUNNING = OcrJobStatus.RUNNING.name();
    private static final String JOB_DONE = OcrJobStatus.DONE.name();
    private static final String JOB_FAILED = OcrJobStatus.FAILED.name();
    private static final String JOB_CANCELLED = OcrJobStatus.CANCELLED.name();

    private final OcrJobRepository ocrJobRepository;
    private final StatementRepository statementRepository;
    private final FileRepository fileRepository;
    private final AiWorkerOcrClient aiWorkerOcrClient;
    private final ObjectMapper objectMapper;
    private final OcrFileService ocrFileService;
    private final OcrDraftService ocrDraftService;

    @Value("${app.ocr.queue.max-running-minutes:30}")
    private long maxRunningMinutes;

    public void processJob(Long userId, Long jobId) {
        if (userId == null || jobId == null) {
            return;
        }
        OcrJobEntity job = ocrJobRepository.findByIdAndUserId(jobId, userId).orElse(null);
        if (job == null) {
            log.warn("OCR job not found: jobId={}, userId={}", jobId, userId);
            return;
        }
        if (JOB_CANCELLED.equals(job.getStatus())) {
            log.info("OCR job cancelled, skip: jobId={}", jobId);
            return;
        }
        // Note: We don't check for JOB_DONE here because of race conditions.
        // The claimForRunning() below will atomically verify the status is
        // QUEUED/FAILED.
        OffsetDateTime staleBefore = OffsetDateTime.now().minusMinutes(maxRunningMinutes);
        if (JOB_RUNNING.equals(job.getStatus())) {
            OffsetDateTime updatedAt = job.getUpdatedAt();
            if (updatedAt != null && updatedAt.isAfter(staleBefore)) {
                log.info("OCR job is still running, skip: jobId={}", jobId);
                return;
            }
        }

        int claimed = ocrJobRepository.claimForRunning(
                jobId,
                JOB_RUNNING,
                5,
                null,
                JOB_CANCELLED,
                JOB_RUNNING,
                staleBefore,
                List.of(JOB_QUEUED, JOB_FAILED));
        if (claimed == 0) {
            log.info("OCR job already claimed or cancelled, skip: jobId={}", jobId);
            return;
        }

        try {
            StatementEntity statement = requireStatement(job.getStatementId(), userId);
            FileEntity file = fileRepository.findByIdAndUserId(job.getFileId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "File not found"));

            log.info("Starting OCR processing: jobId={}, fileId={}", jobId, file.getId());
            byte[] content = ocrFileService.loadFileBytes(file);

            log.info("Calling AI worker OCR...");
            AiWorkerOcrResponse response = aiWorkerOcrClient.processFile(userId, file, content);
            if (response == null) {
                throw new BusinessException(ErrorCode.OCR_PARSE_FAILED, "Empty OCR response");
            }

            OcrJobEntity latest = ocrJobRepository.findByIdAndUserId(jobId, userId).orElse(null);
            if (latest == null
                    || !JOB_RUNNING.equals(latest.getStatus())
                    || !Objects.equals(latest.getStatementId(), statement.getId())) {
                log.info("OCR job cancelled or requeued, discard result: jobId={}, status={}, statementId={}",
                        jobId,
                        latest != null ? latest.getStatus() : "N/A",
                        latest != null ? latest.getStatementId() : null);
                return;
            }

            if (!StatementStatus.DRAFT.name().equals(statement.getStatus())) {
                log.warn("OCR job statement not in DRAFT, skip: jobId={}, statementId={}, status={}",
                        jobId, statement.getId(), statement.getStatus());
                ocrJobRepository.updateStatusIfNotCancelled(
                        jobId, JOB_FAILED, 100, "Statement not in DRAFT", JOB_CANCELLED);
                return;
            }

            statement.setRawText(response.rawText());
            statement.setParsedJson(objectMapper.writeValueAsString(response));
            statementRepository.save(statement);

            ocrDraftService.saveDrafts(statement, response.trades());

            int updated = ocrJobRepository.updateStatusIfNotCancelled(
                    jobId, JOB_DONE, 100, null, JOB_CANCELLED);
            if (updated == 0) {
                log.info("OCR job cancelled before completion, skip DONE update: jobId={}", jobId);
                return;
            }
            log.info("OCR job completed: jobId={}", jobId);

        } catch (Exception ex) {
            log.error("OCR job failed: jobId={}, error={}", jobId, ex.getMessage(), ex);
            try {
                int updated = ocrJobRepository.updateStatusIfNotCancelled(
                        jobId, JOB_FAILED, 100, trimMessage(ex.getMessage()), JOB_CANCELLED);
                if (updated == 0) {
                    log.info("OCR job cancelled before failure update, skip FAILED: jobId={}", jobId);
                }
            } catch (Exception saveEx) {
                log.error("Failed to persist OCR job failure: jobId={}, error={}", jobId, saveEx.getMessage());
            }
        }
    }

    private StatementEntity requireStatement(Long statementId, Long userId) {
        Optional<StatementEntity> statement = statementRepository.findByIdAndUserId(statementId, userId);
        return statement.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Statement not found"));
    }

    private String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
