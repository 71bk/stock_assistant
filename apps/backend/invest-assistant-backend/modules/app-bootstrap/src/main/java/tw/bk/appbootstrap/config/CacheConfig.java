package tw.bk.appbootstrap.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${app.ai.chat.instrument-search.cache-ttl:5m}") Duration ttl) {
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(ttl)
                .maximumSize(1000);

        CaffeineCacheManager manager = new CaffeineCacheManager("instrumentSearch");
        manager.setCaffeine(caffeine);
        return manager;
    }
}
