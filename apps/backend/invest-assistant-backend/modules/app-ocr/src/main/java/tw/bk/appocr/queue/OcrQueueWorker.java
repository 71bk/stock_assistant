package tw.bk.appocr.queue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tw.bk.appocr.service.OcrService;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrQueueWorker {

    private final OcrQueueService queueService;
    private final OcrService ocrService;
    @Qualifier("ocrQueueExecutor")
    private final Executor ocrQueueExecutor;

    @Scheduled(fixedDelayString = "${app.ocr.queue.poll-interval-ms:1000}")
    public void poll() {
        try {
            // 1. Process pending messages first (messages delivered but not ACKed)
            List<MapRecord<String, String, String>> pending = queueService.readPending();
            if (pending != null && !pending.isEmpty()) {
                log.info("Found {} pending OCR jobs, reprocessing...", pending.size());
                processRecords(pending);
            }

            // 2. Process new messages
            List<MapRecord<String, String, String>> batch = queueService.readBatch();
            if (batch != null && !batch.isEmpty()) {
                log.debug("OCR Queue poll: 讀取到 {} 筆新記錄", batch.size());
                processRecords(batch);
            }
        } catch (Exception e) {
            log.error("OCR Queue poll error: {}", e.getMessage(), e);
        }
    }

    private void processRecords(List<MapRecord<String, String, String>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (MapRecord<String, String, String> record : records) {
            CompletableFuture.runAsync(() -> processRecord(record), ocrQueueExecutor)
                    .exceptionally(ex -> {
                        log.error("Failed to dispatch OCR job record: {}", ex.getMessage(), ex);
                        return null;
                    });
        }
    }

    private void processRecord(MapRecord<String, String, String> record) {
        if (record == null) {
            return;
        }
        RecordId recordId = record.getId();
        Map<String, String> value = record.getValue();
        try {
            Long jobId = parseLong(value.get("job_id"));
            Long userId = parseLong(value.get("user_id"));

            // Skip init record
            if (value.containsKey("init")) {
                queueService.acknowledge(recordId);
                return;
            }

            log.info("Processing OCR job: jobId={}, userId={}", jobId, userId);
            if (jobId != null && userId != null) {
                ocrService.processJob(userId, jobId);
                // Only acknowledge on success (or known handling)
                queueService.acknowledge(recordId);
            } else {
                log.warn("Invalid OCR job record: {}", value);
                // Acknowledge invalid records to avoid infinite loops
                queueService.acknowledge(recordId);
            }
        } catch (Exception e) {
            log.error("Failed to process OCR job: {}", e.getMessage(), e);
            // Do NOT acknowledge on error, so it remains in pending for retry
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
