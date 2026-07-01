package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.time.ClockProvider;

class OcrJobStatePolicyTest {
    private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");
    private final OcrJobStatePolicy policy = new OcrJobStatePolicy(fixedClock(), 30L);

    @Test
    void validateRetry_shouldRejectFreshRunningJob() {
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> policy.validateRetry(OcrJobStatus.RUNNING.name(), updatedAt, false));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals("OCR job is still running", error.getMessage());
    }

    @Test
    void validateRetry_shouldAllowStaleRunningJob() {
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(NOW.minusSeconds(31 * 60), ZoneOffset.UTC);

        assertDoesNotThrow(() -> policy.validateRetry(OcrJobStatus.RUNNING.name(), updatedAt, false));
    }

    @Test
    void validateRetry_shouldAllowForceForCompletedJob() {
        assertDoesNotThrow(() -> policy.validateRetry(OcrJobStatus.DONE.name(), null, true));
    }

    @Test
    void validateReparse_shouldAllowForceForFreshRunningJob() {
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC);

        assertDoesNotThrow(() -> policy.validateReparse(OcrJobStatus.RUNNING.name(), updatedAt, true));
    }

    @Test
    void validatePasswordSubmission_shouldRejectCancelledJob() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> policy.validatePasswordSubmission(OcrJobStatus.CANCELLED.name(), null));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals("OCR job is cancelled", error.getMessage());
    }

    private ClockProvider fixedClock() {
        return () -> NOW;
    }
}
