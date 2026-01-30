package tw.bk.appocr.queue;

import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tw.bk.appocr.service.OcrService;

@Component
@RequiredArgsConstructor
public class OcrQueueWorker {
    private static final Logger log = LoggerFactory.getLogger(OcrQueueWorker.class);

    private final OcrQueueService queueService;
    private final OcrService ocrService;

    /**
     * 在應用程式啟動時清除所有 pending 的 OCR 訊息，
     * 避免後端重啟後一直重複處理舊的 OCR job。
     */
    @PostConstruct
    public void clearPendingOnStartup() {
        try {
            List<MapRecord<String, String, String>> pending = queueService.readPending();
            if (pending != null && !pending.isEmpty()) {
                log.warn("啟動時發現 {} 筆 pending OCR jobs，將全部清除", pending.size());
                for (MapRecord<String, String, String> record : pending) {
                    queueService.acknowledge(record.getId());
                    log.warn("已清除 pending OCR job: recordId={}, payload={}",
                            record.getId(), record.getValue());
                }
                log.info("啟動時 pending OCR jobs 清除完成");
            } else {
                log.info("啟動時沒有 pending OCR jobs");
            }
        } catch (Exception e) {
            log.error("啟動時清除 pending OCR jobs 失敗: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${app.ocr.queue.poll-interval-ms:1000}")
    public void poll() {
        try {
            log.debug("OCR Queue Worker: 開始 poll...");
            List<MapRecord<String, String, String>> batch = queueService.readBatch();
            if (batch != null && !batch.isEmpty()) {
                log.info("OCR Queue poll: 讀取到 {} 筆新記錄", batch.size());
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
        log.info("Processing {} OCR queue records", records.size());
        for (MapRecord<String, String, String> record : records) {
            RecordId recordId = record.getId();
            Map<String, String> value = record.getValue();
            try {
                Long jobId = parseLong(value.get("job_id"));
                Long userId = parseLong(value.get("user_id"));
                log.info("Processing OCR job: jobId={}, userId={}", jobId, userId);
                if (jobId != null && userId != null) {
                    ocrService.processJob(userId, jobId);
                }
            } catch (Exception e) {
                log.error("Failed to process OCR job: {}", e.getMessage(), e);
            } finally {
                queueService.acknowledge(recordId);
            }
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
