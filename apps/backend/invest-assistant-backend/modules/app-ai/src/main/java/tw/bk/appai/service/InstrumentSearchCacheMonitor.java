package tw.bk.appai.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InstrumentSearchCacheMonitor {
    private final CacheManager cacheManager;

    @Scheduled(fixedDelayString = "${app.ai.chat.instrument-search.cache-stats-interval:5m}")
    public void logStats() {
        org.springframework.cache.Cache cache = cacheManager.getCache("instrumentSearch");
        if (!(cache instanceof CaffeineCache caffeineCache)) {
            return;
        }
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        CacheStats stats = nativeCache.stats();

        // 只在有活動時才記錄 INFO，避免日誌噪音
        if (stats.requestCount() > 0) {
            log.info(
                    "Instrument search cache stats: size={}, hitRate={}, hits={}, misses={}, evictions={}",
                    nativeCache.estimatedSize(),
                    String.format("%.2f", stats.hitRate()),
                    stats.hitCount(),
                    stats.missCount(),
                    stats.evictionCount());
        } else {
            log.debug("Instrument search cache idle");
        }
    }
}
