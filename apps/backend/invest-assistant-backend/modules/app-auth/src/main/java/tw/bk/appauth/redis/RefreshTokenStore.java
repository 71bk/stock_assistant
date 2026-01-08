package tw.bk.appauth.redis;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenStore {
    private static final String KEY_PREFIX = "sess:refresh:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void store(String jti, Long userId, Duration ttl) {
        String key = KEY_PREFIX + jti;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), ttl);
    }

    public Optional<Long> findUserId(String jti) {
        String key = KEY_PREFIX + jti;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(value));
    }

    public void revoke(String jti) {
        redisTemplate.delete(KEY_PREFIX + jti);
    }
}
