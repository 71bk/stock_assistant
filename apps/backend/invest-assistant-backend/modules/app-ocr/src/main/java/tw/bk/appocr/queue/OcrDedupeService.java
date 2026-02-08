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

    @Value("${app.ocr.dedupe-reserve-ttl-seconds:60}")
    private long reserveTtlSeconds;

    public Optional<Long> findJobId(Long userId, String sha256, Long portfolioId) {
        if (userId == null || sha256 == null || portfolioId == null) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(buildKey(userId, sha256, portfolioId));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if ("PENDING".equalsIgnoreCase(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public boolean reserve(Long userId, String sha256, Long portfolioId) {
        if (userId == null || sha256 == null || portfolioId == null) {
            return false;
        }
        String key = buildKey(userId, sha256, portfolioId);
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(key, "PENDING", Duration.ofSeconds(reserveTtlSeconds));
        return Boolean.TRUE.equals(reserved);
    }

    public void store(Long userId, String sha256, Long portfolioId, Long jobId) {
        if (userId == null || sha256 == null || portfolioId == null || jobId == null) {
            return;
        }
        String key = buildKey(userId, sha256, portfolioId);
        redisTemplate.opsForValue().set(key, String.valueOf(jobId), Duration.ofSeconds(ttlSeconds));
    }

    private String buildKey(Long userId, String sha256, Long portfolioId) {
        return "ocr:job:" + userId + ":" + sha256 + ":" + portfolioId;
    }
}
