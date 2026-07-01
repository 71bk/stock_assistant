package tw.bk.appocr.service;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.time.ClockProvider;

/** Validation rules for user-initiated OCR job state transitions. */
@Component
class OcrJobStatePolicy {
    private static final String JOB_QUEUED = OcrJobStatus.QUEUED.name();
    private static final String JOB_PASSWORD_REQUIRED = OcrJobStatus.PASSWORD_REQUIRED.name();
    private static final String JOB_PASSWORD_INVALID = OcrJobStatus.PASSWORD_INVALID.name();
    private static final String JOB_RUNNING = OcrJobStatus.RUNNING.name();
    private static final String JOB_DONE = OcrJobStatus.DONE.name();
    private static final String JOB_FAILED = OcrJobStatus.FAILED.name();
    private static final String JOB_CANCELLED = OcrJobStatus.CANCELLED.name();
    private static final String STATEMENT_CONFIRMED = StatementStatus.CONFIRMED.name();

    private final ClockProvider clockProvider;
    private final long maxRunningMinutes;

    OcrJobStatePolicy(
            ClockProvider clockProvider,
            @Value("${app.ocr.queue.max-running-minutes:30}") long maxRunningMinutes) {
        this.clockProvider = clockProvider;
        this.maxRunningMinutes = maxRunningMinutes;
    }

    void validatePasswordSubmission(String status, OffsetDateTime updatedAt) {
        if (JOB_CANCELLED.equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job is cancelled");
        }
        if (isFreshRunning(status, updatedAt)) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job is still running");
        }
        if (!JOB_PASSWORD_REQUIRED.equals(status) && !JOB_PASSWORD_INVALID.equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job does not require a PDF password");
        }
    }

    void validateRetry(String status, OffsetDateTime updatedAt, boolean force) {
        if (JOB_DONE.equals(status) && !force) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job already completed");
        }
        if (!force && isFreshRunning(status, updatedAt)) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job is still running");
        }
    }

    void validateReparse(String status, OffsetDateTime updatedAt, boolean force) {
        if (!force && isFreshRunning(status, updatedAt)) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job is still running");
        }
    }

    void validateRetryStatement(String status) {
        if (STATEMENT_CONFIRMED.equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR statement already confirmed");
        }
    }

    void validateReparseStatement(String status, boolean force) {
        if (STATEMENT_CONFIRMED.equals(status) && !force) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR statement already confirmed");
        }
    }

    boolean isDone(String status) {
        return JOB_DONE.equals(status);
    }

    boolean isTerminalForCancel(String status) {
        return JOB_DONE.equals(status) || JOB_FAILED.equals(status) || JOB_CANCELLED.equals(status);
    }

    boolean shouldRequeueDuplicate(String status) {
        return JOB_FAILED.equals(status) || JOB_QUEUED.equals(status) || JOB_CANCELLED.equals(status);
    }

    private boolean isFreshRunning(String status, OffsetDateTime updatedAt) {
        if (!JOB_RUNNING.equals(status) || updatedAt == null) {
            return false;
        }
        OffsetDateTime staleBefore = clockProvider.nowUtc().minusMinutes(maxRunningMinutes);
        return updatedAt.isAfter(staleBefore);
    }
}
