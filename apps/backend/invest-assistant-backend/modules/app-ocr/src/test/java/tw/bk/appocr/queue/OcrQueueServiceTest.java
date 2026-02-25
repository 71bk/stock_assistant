package tw.bk.appocr.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class OcrQueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("rawtypes")
    private org.springframework.data.redis.core.StreamOperations streamOperations;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private RedisStreamCommands redisStreamCommands;

    private OcrQueueService service;
    private OcrQueueProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OcrQueueProperties();
        properties.setStreamKey("ocr:queue:test");
        properties.setGroup("ocr-workers-test");
        properties.setConsumer("ocr-worker-test");
        properties.setBatchSize(5);
        properties.setBlock(Duration.ofMillis(1));
        properties.setPendingClaimLimit(5);
        properties.setPendingClaimMinIdle(Duration.ofSeconds(30));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        service = new OcrQueueService(redisTemplate, properties);
    }

    @Test
    void readBatch_shouldSeedStreamBeforeCreateGroupWhenStreamMissing() {
        when(redisTemplate.hasKey(properties.getStreamKey())).thenReturn(false);
        when(streamOperations.read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of());

        service.readBatch();

        verify(streamOperations, times(1)).add(any());
        verify(streamOperations, times(1)).createGroup(
                properties.getStreamKey(),
                ReadOffset.from("0"),
                properties.getGroup());
    }

    @Test
    void readBatch_shouldAcceptBusyGroupAsReady() {
        when(redisTemplate.hasKey(properties.getStreamKey())).thenReturn(true);
        when(streamOperations.read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of());
        doThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"))
                .when(streamOperations)
                .createGroup(properties.getStreamKey(), ReadOffset.from("0"), properties.getGroup());

        service.readBatch();

        verify(streamOperations, times(1)).createGroup(
                properties.getStreamKey(),
                ReadOffset.from("0"),
                properties.getGroup());
        verify(streamOperations, times(1)).read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class));
    }

    @Test
    void readBatch_shouldThrowWhenCreateGroupFailsWithUnexpectedError() {
        when(redisTemplate.hasKey(properties.getStreamKey())).thenReturn(true);
        doThrow(new RuntimeException("redis down"))
                .when(streamOperations)
                .createGroup(properties.getStreamKey(), ReadOffset.from("0"), properties.getGroup());

        assertThrows(IllegalStateException.class, () -> service.readBatch());
    }

    @Test
    void readBatch_shouldRetryCreateGroupWhenStreamMissingDuringCreate() {
        when(redisTemplate.hasKey(properties.getStreamKey())).thenReturn(true, false);
        when(streamOperations.read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of());
        when(streamOperations.createGroup(
                properties.getStreamKey(),
                ReadOffset.from("0"),
                properties.getGroup()))
                .thenThrow(new RuntimeException("ERR The XGROUP subcommand requires the key to exist"))
                .thenReturn("OK");

        service.readBatch();

        verify(streamOperations, times(1)).add(any());
        verify(streamOperations, times(2)).createGroup(
                properties.getStreamKey(),
                ReadOffset.from("0"),
                properties.getGroup());
        verify(streamOperations, times(1)).read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class));
    }

    @Test
    void readBatch_shouldThrowWhenRetryAfterMissingStreamStillFails() {
        when(redisTemplate.hasKey(properties.getStreamKey())).thenReturn(true, false);
        doThrow(new RuntimeException("ERR The XGROUP subcommand requires the key to exist"))
                .doThrow(new RuntimeException("redis down"))
                .when(streamOperations)
                .createGroup(properties.getStreamKey(), ReadOffset.from("0"), properties.getGroup());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.readBatch());

        assertTrue(ex.getMessage().contains("after stream recreation"));
        verify(streamOperations, times(1)).add(any());
        verify(streamOperations, times(2)).createGroup(
                properties.getStreamKey(),
                ReadOffset.from("0"),
                properties.getGroup());
    }

    @Test
    void readPending_shouldClaimStaleMessagesBeforeReading() {
        when(redisTemplate.hasKey(properties.getStreamKey())).thenReturn(true);
        when(streamOperations.read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of());

        PendingMessages pendingMessages = org.mockito.Mockito.mock(PendingMessages.class);
        PendingMessage pendingMessage = org.mockito.Mockito.mock(PendingMessage.class);
        when(pendingMessages.isEmpty()).thenReturn(false);
        when(pendingMessages.iterator()).thenReturn(List.of(pendingMessage).iterator());
        when(pendingMessage.getId()).thenReturn(RecordId.of("1-0"));

        mockRedisExecute();
        when(redisStreamCommands.xPending(
                any(byte[].class),
                eq(properties.getGroup()),
                any(Range.class),
                eq(Long.valueOf(properties.getPendingClaimLimit())),
                eq(properties.getPendingClaimMinIdle()))).thenReturn(pendingMessages);

        service.readPending();

        ArgumentCaptor<RecordId[]> idsCaptor = ArgumentCaptor.forClass(RecordId[].class);
        verify(redisStreamCommands).xClaim(
                any(byte[].class),
                eq(properties.getGroup()),
                eq(properties.getConsumer()),
                eq(properties.getPendingClaimMinIdle()),
                idsCaptor.capture());
        assertEquals(1, idsCaptor.getValue().length);
        assertEquals("1-0", idsCaptor.getValue()[0].getValue());
    }

    @Test
    void readPending_shouldSkipClaimWhenMinIdleDisabled() {
        properties.setPendingClaimMinIdle(Duration.ZERO);
        when(redisTemplate.hasKey(properties.getStreamKey())).thenReturn(true);
        when(streamOperations.read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of());

        service.readPending();

        verify(redisTemplate, never()).execute(any(RedisCallback.class));
    }

    @SuppressWarnings("unchecked")
    private void mockRedisExecute() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<Object> callback = invocation.getArgument(0);
            return callback.doInRedis(redisConnection);
        });
        when(redisConnection.streamCommands()).thenReturn(redisStreamCommands);
    }
}
