package tw.bk.appocr.queue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;
import tw.bk.apppersistence.entity.OcrJobEntity;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrQueueService {

    private final StringRedisTemplate redisTemplate;
    private final OcrQueueProperties properties;

    private volatile boolean groupReady = false;

    public void enqueue(OcrJobEntity job) {
        log.info("準備將 OCR Job 加入 Queue: jobId={}, userId={}", job.getId(), job.getUserId());
        Map<String, String> payload = new HashMap<>();
        payload.put("job_id", String.valueOf(job.getId()));
        payload.put("user_id", String.valueOf(job.getUserId()));
        payload.put("file_id", String.valueOf(job.getFileId()));
        if (job.getStatementId() != null) {
            payload.put("statement_id", String.valueOf(job.getStatementId()));
        }
        StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
        RecordId recordId = ops.add(StreamRecords.newRecord().in(properties.getStreamKey()).ofMap(payload));
        log.info("OCR Job 已加入 Queue: streamKey={}, recordId={}", properties.getStreamKey(), recordId);
    }

    public List<MapRecord<String, String, String>> readBatch() {
        ensureGroup();
        StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
        StreamReadOptions options = StreamReadOptions.empty()
                .count(properties.getBatchSize())
                .block(properties.getBlock() == null ? Duration.ofSeconds(2) : properties.getBlock());

        return ops.read(
                org.springframework.data.redis.connection.stream.Consumer.from(
                        properties.getGroup(),
                        properties.getConsumer()),
                options,
                StreamOffset.create(properties.getStreamKey(), ReadOffset.lastConsumed()));
    }

    public List<MapRecord<String, String, String>> readPending() {
        ensureGroup();
        StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
        StreamReadOptions options = StreamReadOptions.empty()
                .count(properties.getBatchSize());
        try {
            return ops.read(
                    org.springframework.data.redis.connection.stream.Consumer.from(
                            properties.getGroup(),
                            properties.getConsumer()),
                    options,
                    StreamOffset.create(properties.getStreamKey(), ReadOffset.from("0")));
        } catch (Exception ex) {
            return List.of();
        }
    }

    public void acknowledge(RecordId recordId) {
        if (recordId == null) {
            return;
        }
        redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), properties.getGroup(), recordId);
    }

    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        synchronized (this) {
            if (groupReady) {
                return;
            }
            StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
            try {
                ops.createGroup(properties.getStreamKey(), ReadOffset.from("0"), properties.getGroup());
                groupReady = true;
                return;
            } catch (Exception ignored) {
                // stream may not exist or group already exists
            }

            try {
                ops.add(StreamRecords.newRecord().in(properties.getStreamKey()).ofMap(Map.of("init", "1")));
                ops.createGroup(properties.getStreamKey(), ReadOffset.from("0"), properties.getGroup());
            } catch (Exception ignored) {
                // group already exists
            }
            groupReady = true;
        }
    }
}
