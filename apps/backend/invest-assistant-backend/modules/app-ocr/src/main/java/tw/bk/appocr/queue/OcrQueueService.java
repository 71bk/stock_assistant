package tw.bk.appocr.queue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
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
    private static final ReadOffset GROUP_START_OFFSET = ReadOffset.from("0");
    private static final String INIT_FIELD = "init";
    private static final String INIT_VALUE = "1";
    private static final int MAX_PENDING_CLAIM_LIMIT = 500;

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
        try {
            ensureGroup();
            return doReadBatch();
        } catch (Exception ex) {
            if (isNoGroup(ex)) {
                log.warn("Consumer group missing, recreating...");
                groupReady = false;
                ensureGroup();
                return doReadBatch();
            }
            throw ex;
        }
    }

    public List<MapRecord<String, String, String>> readPending() {
        try {
            ensureGroup();
            claimStalePending();
            return doReadPending();
        } catch (Exception ex) {
            if (isNoGroup(ex)) {
                log.warn("Consumer group missing when reading pending, recreating...");
                groupReady = false;
                ensureGroup();
                return doReadPending();
            }
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
            String streamKey = properties.getStreamKey();
            String group = properties.getGroup();
            try {
                ensureStreamExists(ops, streamKey);
                ops.createGroup(streamKey, GROUP_START_OFFSET, group);
                groupReady = true;
                return;
            } catch (Exception ex) {
                if (isBusyGroup(ex)) {
                    groupReady = true;
                    return;
                }
                if (isMissingStream(ex)) {
                    log.warn("OCR stream missing during createGroup, retrying once: streamKey={}, group={}",
                            streamKey, group);
                    ensureStreamExists(ops, streamKey);
                    try {
                        ops.createGroup(streamKey, GROUP_START_OFFSET, group);
                        groupReady = true;
                        return;
                    } catch (Exception retryEx) {
                        if (isBusyGroup(retryEx)) {
                            groupReady = true;
                            return;
                        }
                        throw new IllegalStateException(
                                "Failed to ensure OCR consumer group after stream recreation: streamKey="
                                        + streamKey + ", group=" + group,
                                retryEx);
                    }
                }
                throw new IllegalStateException(
                        "Failed to ensure OCR consumer group: streamKey=" + streamKey + ", group=" + group, ex);
            }
        }
    }

    private List<MapRecord<String, String, String>> doReadBatch() {
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

    private List<MapRecord<String, String, String>> doReadPending() {
        StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
        StreamReadOptions options = StreamReadOptions.empty()
                .count(properties.getBatchSize());

        return ops.read(
                org.springframework.data.redis.connection.stream.Consumer.from(
                        properties.getGroup(),
                        properties.getConsumer()),
                options,
                StreamOffset.create(properties.getStreamKey(), ReadOffset.from("0")));
    }

    private boolean isBusyGroup(Exception ex) {
        return containsMessage(ex, "BUSYGROUP");
    }

    private boolean isNoGroup(Exception ex) {
        return containsMessage(ex, "NOGROUP");
    }

    private boolean isMissingStream(Exception ex) {
        return containsMessageIgnoreCase(ex, "requires the key to exist")
                || containsMessageIgnoreCase(ex, "no such key");
    }

    private void ensureStreamExists(StreamOperations<String, String, String> ops, String streamKey) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(streamKey))) {
            return;
        }
        ops.add(StreamRecords.newRecord().in(streamKey).ofMap(Map.of(INIT_FIELD, INIT_VALUE)));
    }

    private void claimStalePending() {
        Duration minIdle = properties.getPendingClaimMinIdle();
        if (minIdle == null || minIdle.isZero() || minIdle.isNegative()) {
            return;
        }
        int claimLimit = Math.min(Math.max(properties.getPendingClaimLimit(), 1), MAX_PENDING_CLAIM_LIMIT);
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            RedisStreamCommands streamCommands = connection.streamCommands();
            if (streamCommands == null) {
                return null;
            }
            byte[] streamKey = serializeStreamKey(properties.getStreamKey());
            PendingMessages pendingMessages = streamCommands.xPending(
                    streamKey,
                    properties.getGroup(),
                    Range.unbounded(),
                    Long.valueOf(claimLimit),
                    minIdle);
            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return null;
            }
            List<RecordId> staleIds = new ArrayList<>();
            for (PendingMessage pending : pendingMessages) {
                if (pending == null || pending.getId() == null) {
                    continue;
                }
                staleIds.add(pending.getId());
            }
            if (staleIds.isEmpty()) {
                return null;
            }
            streamCommands.xClaim(
                    streamKey,
                    properties.getGroup(),
                    properties.getConsumer(),
                    minIdle,
                    staleIds.toArray(new RecordId[0]));
            log.debug("Claimed {} stale pending OCR records for consumer={}", staleIds.size(), properties.getConsumer());
            return null;
        });
    }

    private byte[] serializeStreamKey(String streamKey) {
        if (streamKey == null) {
            return new byte[0];
        }
        byte[] serialized = redisTemplate.getStringSerializer() == null
                ? null
                : redisTemplate.getStringSerializer().serialize(streamKey);
        return serialized != null ? serialized : streamKey.getBytes(StandardCharsets.UTF_8);
    }

    private boolean containsMessage(Throwable ex, String keyword) {
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.contains(keyword)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean containsMessageIgnoreCase(Throwable ex, String keyword) {
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.toLowerCase().contains(keyword.toLowerCase())) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
