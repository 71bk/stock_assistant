package tw.bk.appstocks.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 股票資料快取服務
 */
@Service
@RequiredArgsConstructor
public class StockCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 從快取取得資料
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(type.cast(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 寫入快取
     */
    public void set(String key, Object value, long ttlMs) {
        redisTemplate.opsForValue().set(key, value, Duration.ofMillis(ttlMs));
    }

    /**
     * 從快取取得列表資料
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<List<T>> getList(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of((List<T>) value);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 寫入列表快取
     */
    public <T> void setList(String key, List<T> value, long ttlMs) {
        redisTemplate.opsForValue().set(key, value, Duration.ofMillis(ttlMs));
    }

    /**
     * 刪除快取
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
