package tw.bk.appocr.service;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.repository.StatementRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrCleanupService {
    private static final String STATUS_SUPERSEDED = StatementStatus.SUPERSEDED.name();
    private static final int DEFAULT_BATCH_SIZE = 200;

    private final StatementRepository statementRepository;

    @Value("${app.ocr.cleanup.enabled:false}")
    private boolean enabled;

    @Value("${app.ocr.cleanup.superseded-days:30}")
    private long supersededDays;

    @Value("${app.ocr.cleanup.batch-size:200}")
    private int batchSize;

    @Scheduled(cron = "${app.ocr.cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void cleanupSupersededStatements() {
        if (!enabled) {
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(supersededDays);
        int totalRemoved = 0;
        int effectiveBatchSize = Math.max(1, batchSize);
        PageRequest pageRequest = PageRequest.of(0, effectiveBatchSize, Sort.by("supersededAt").ascending());
        while (true) {
            Page<StatementEntity> page = statementRepository
                    .findByStatusAndSupersededAtBeforeOrderBySupersededAtAsc(
                            STATUS_SUPERSEDED,
                            cutoff,
                            pageRequest);
            if (page.isEmpty()) {
                break;
            }
            List<StatementEntity> batch = page.getContent();
            statementRepository.deleteAllInBatch(batch);
            totalRemoved += batch.size();
            if (batch.size() < effectiveBatchSize) {
                break;
            }
        }
        if (totalRemoved > 0) {
            log.info("OCR cleanup removed {} superseded statements before {}", totalRemoved, cutoff);
        }
    }
}
