package tw.bk.appapi.sse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class BufferedSseSession {
    private final int maxBufferEvents;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<BufferedSseEvent> buffer = new ArrayList<>();
    private final Set<SseEmitter> emitters = new LinkedHashSet<>();

    private long sequence = 0L;
    private boolean completed = false;
    private Instant completedAt = null;

    public BufferedSseSession(int maxBufferEvents) {
        this.maxBufferEvents = Math.max(maxBufferEvents, 32);
    }

    public void startIfNeeded(Runnable starter) {
        if (starter == null) {
            return;
        }
        if (started.compareAndSet(false, true)) {
            starter.run();
        }
    }

    public void attachEmitter(SseEmitter emitter, String lastEventId) {
        if (emitter == null) {
            return;
        }

        emitter.onCompletion(() -> detachEmitter(emitter));
        emitter.onTimeout(() -> detachEmitter(emitter));
        emitter.onError(ex -> detachEmitter(emitter));

        List<BufferedSseEvent> replayEvents;
        boolean shouldComplete;
        synchronized (this) {
            if (!completed) {
                emitters.add(emitter);
            }
            replayEvents = replayEvents(lastEventId);
            shouldComplete = completed;
        }

        for (BufferedSseEvent event : replayEvents) {
            sendToEmitter(emitter, event);
        }
        if (shouldComplete) {
            safelyCompleteEmitter(emitter);
        }
    }

    public void sendEvent(String name, Object data) {
        BufferedSseEvent event;
        List<SseEmitter> targets;
        synchronized (this) {
            if (completed) {
                return;
            }
            String id = Long.toString(++sequence);
            event = new BufferedSseEvent(id, name, data);
            buffer.add(event);
            trimBuffer();
            targets = new ArrayList<>(emitters);
        }

        for (SseEmitter emitter : targets) {
            sendToEmitter(emitter, event);
        }
    }

    public void complete() {
        List<SseEmitter> targets;
        synchronized (this) {
            if (completed) {
                return;
            }
            completed = true;
            completedAt = Instant.now();
            targets = new ArrayList<>(emitters);
            emitters.clear();
        }
        for (SseEmitter emitter : targets) {
            safelyCompleteEmitter(emitter);
        }
    }

    public boolean isExpired(Duration ttl) {
        if (ttl == null) {
            return false;
        }
        Instant completedAtSnapshot;
        synchronized (this) {
            if (!completed || completedAt == null) {
                return false;
            }
            completedAtSnapshot = completedAt;
        }
        return completedAtSnapshot.plus(ttl).isBefore(Instant.now());
    }

    private synchronized void detachEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }

    private synchronized List<BufferedSseEvent> replayEvents(String lastEventId) {
        if (buffer.isEmpty()) {
            return List.of();
        }
        long lastId = parseLastEventId(lastEventId);
        if (lastId <= 0) {
            return new ArrayList<>(buffer);
        }
        List<BufferedSseEvent> result = new ArrayList<>();
        for (BufferedSseEvent event : buffer) {
            long eventId = parseLastEventId(event.id());
            if (eventId > lastId) {
                result.add(event);
            }
        }
        return result;
    }

    private synchronized void trimBuffer() {
        while (buffer.size() > maxBufferEvents) {
            buffer.remove(0);
        }
    }

    private void sendToEmitter(SseEmitter emitter, BufferedSseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.id())
                    .name(event.name())
                    .data(event.data()));
        } catch (Exception ex) {
            log.debug("SSE emitter send failed: {}", ex.getMessage());
            detachEmitter(emitter);
        }
    }

    private void safelyCompleteEmitter(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.debug("SSE emitter complete failed: {}", ex.getMessage());
        }
    }

    private long parseLastEventId(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
