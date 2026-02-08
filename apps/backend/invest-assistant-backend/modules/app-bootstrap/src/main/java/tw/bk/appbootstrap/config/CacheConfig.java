package tw.bk.appbootstrap.config;

import java.util.List;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${app.ai.chat.instrument-search.cache-ttl:5m}") Duration instrumentSearchTtl,
            @Value("${app.ai.chat.last-mentioned.cache-ttl:12h}") Duration lastMentionedTtl) {
        Caffeine<Object, Object> instrumentSearchCaffeine = Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(instrumentSearchTtl)
                .maximumSize(1000);
        Caffeine<Object, Object> lastMentionedCaffeine = Caffeine.newBuilder()
                .expireAfterWrite(lastMentionedTtl)
                .maximumSize(10000);

        CaffeineCache instrumentSearchCache = new CaffeineCache(
                "instrumentSearch",
                instrumentSearchCaffeine.build());
        CaffeineCache lastMentionedCache = new CaffeineCache(
                "conversationLastMentioned",
                lastMentionedCaffeine.build());
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(instrumentSearchCache, lastMentionedCache));
        return manager;
    }
}
