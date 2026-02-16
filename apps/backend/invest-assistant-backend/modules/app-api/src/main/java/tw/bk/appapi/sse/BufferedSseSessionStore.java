package tw.bk.appapi.sse;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BufferedSseSessionStore {
    private final ConcurrentMap<String, BufferedSseSession> sessions = new ConcurrentHashMap<>();

    @Value("${app.ai.chat.sse.max-buffer-events:1024}")
    private int maxBufferEvents;

    @Value("${app.ai.chat.sse.completed-session-ttl-seconds:180}")
    private long completedSessionTtlSeconds;

    public BufferedSseSession getOrCreate(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("sessionKey is required");
        }
        cleanupExpired();
        return sessions.computeIfAbsent(sessionKey, key -> new BufferedSseSession(maxBufferEvents));
    }

    private void cleanupExpired() {
        long ttlSeconds = Math.max(completedSessionTtlSeconds, 1L);
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(ttl));
    }

    int size() {
        return sessions.size();
    }

    Map<String, BufferedSseSession> snapshot() {
        return Map.copyOf(sessions);
    }
}
