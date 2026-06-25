package tw.bk.appanalytics.service;

import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appanalytics.config.AnalyticsProperties;
import tw.bk.apppersistence.repository.AnalyticsRepository;

@Service
public class AnalyticsCleanupService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsCleanupService.class);

    private final AnalyticsRepository repository;
    private final AnalyticsProperties properties;

    public AnalyticsCleanupService(AnalyticsRepository repository, AnalyticsProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.analytics.cleanup.cron:0 10 4 * * *}")
    @Transactional
    public void purgeExpiredEvents() {
        int retentionDays = Math.max(1, properties.getRetentionDays());
        int deleted = repository.deleteEventsBefore(OffsetDateTime.now().minusDays(retentionDays));
        if (deleted > 0) {
            log.info("Purged expired analytics events: count={}, retentionDays={}", deleted, retentionDays);
        }
    }
}
