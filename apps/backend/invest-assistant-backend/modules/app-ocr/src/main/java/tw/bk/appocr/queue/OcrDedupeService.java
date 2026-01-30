package tw.bk.appocr.queue;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OcrDedupeService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.ocr.dedupe-ttl-seconds:604800}")
    private long ttlSeconds;

    public Optional<Long> findJobId(Long userId, String sha256) {
        if (userId == null || sha256 == null) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(buildKey(userId, sha256));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public void store(Long userId, String sha256, Long jobId) {
        if (userId == null || sha256 == null || jobId == null) {
            return;
        }
        String key = buildKey(userId, sha256);
        redisTemplate.opsForValue().set(key, String.valueOf(jobId), Duration.ofSeconds(ttlSeconds));
    }

    private String buildKey(Long userId, String sha256) {
        return "ocr:dedupe:" + userId + ":" + sha256;
    }
}
