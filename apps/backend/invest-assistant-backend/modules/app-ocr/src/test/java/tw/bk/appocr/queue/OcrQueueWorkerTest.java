package tw.bk.appocr.queue;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import tw.bk.appocr.service.OcrService;

@ExtendWith(MockitoExtension.class)
class OcrQueueWorkerTest {

    @Mock
    private OcrQueueService queueService;

    @Mock
    private OcrService ocrService;

    @Mock
    private MapRecord<String, String, String> validRecord;

    @Mock
    private MapRecord<String, String, String> invalidRecord;

    private OcrQueueWorker worker;
    private final Executor directExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        worker = new OcrQueueWorker(queueService, ocrService, directExecutor);
    }

    @Test
    void poll_shouldProcessAndAcknowledgeValidRecords() {
        when(queueService.readPending()).thenReturn(List.of(validRecord));
        when(queueService.readBatch()).thenReturn(List.of());
        when(validRecord.getId()).thenReturn(RecordId.of("1-0"));
        when(validRecord.getValue()).thenReturn(Map.of("job_id", "101", "user_id", "7"));

        worker.poll();

        verify(ocrService, times(1)).processJob(7L, 101L);
        verify(queueService, times(1)).acknowledge(RecordId.of("1-0"));
    }

    @Test
    void poll_shouldAcknowledgeInvalidRecordsWithoutProcessing() {
        when(queueService.readPending()).thenReturn(List.of(invalidRecord));
        when(queueService.readBatch()).thenReturn(List.of());
        when(invalidRecord.getId()).thenReturn(RecordId.of("2-0"));
        when(invalidRecord.getValue()).thenReturn(Map.of("job_id", "x", "user_id", ""));

        worker.poll();

        verify(ocrService, never()).processJob(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(queueService, times(1)).acknowledge(RecordId.of("2-0"));
    }

    @Test
    void poll_shouldNotAcknowledgeWhenProcessingFails() {
        when(queueService.readPending()).thenReturn(List.of(validRecord));
        when(queueService.readBatch()).thenReturn(List.of());
        when(validRecord.getId()).thenReturn(RecordId.of("3-0"));
        when(validRecord.getValue()).thenReturn(Map.of("job_id", "202", "user_id", "8"));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(ocrService).processJob(8L, 202L);

        worker.poll();

        verify(queueService, never()).acknowledge(RecordId.of("3-0"));
    }
}
