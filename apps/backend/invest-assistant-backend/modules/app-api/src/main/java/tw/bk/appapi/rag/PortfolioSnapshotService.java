package tw.bk.appapi.rag;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.bk.appportfolio.model.PortfolioSnapshot;
import tw.bk.appportfolio.service.PortfolioSnapshotBuilder;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apprag.client.AiWorkerRagClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {
    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotBuilder snapshotBuilder;
    private final AiWorkerRagClient ragClient;

    @Value("${app.rag.snapshot.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${app.rag.snapshot.cron:0 30 2 * * *}")
    public void runScheduled() {
        if (!enabled) {
            return;
        }
        SnapshotResult result = runSnapshots(null, null);
        log.info("Portfolio snapshot scheduled run finished: total={}, ingested={}, skipped={}, failed={}",
                result.total(), result.ingested(), result.skipped(), result.failed());
    }

    public SnapshotResult runSnapshots(Long userId, Long portfolioId) {
        List<PortfolioEntity> portfolios = resolvePortfolios(userId, portfolioId);
        int total = portfolios.size();
        int ingested = 0;
        int skipped = 0;
        int failed = 0;

        for (PortfolioEntity portfolio : portfolios) {
            try {
                PortfolioSnapshot snapshot = snapshotBuilder.build(portfolio.getUserId(), portfolio.getId());
                if (snapshot == null || snapshot.content() == null || snapshot.content().isBlank()) {
                    skipped++;
                    continue;
                }
                ragClient.ingestText(
                        snapshot.userId(),
                        snapshot.content(),
                        snapshot.title(),
                        "portfolio",
                        snapshot.tags());
                ingested++;
            } catch (Exception ex) {
                failed++;
                log.warn("Portfolio snapshot ingestion failed: portfolioId={}, error={}",
                        portfolio.getId(), ex.getMessage(), ex);
            }
        }

        return new SnapshotResult(total, ingested, skipped, failed);
    }

    private List<PortfolioEntity> resolvePortfolios(Long userId, Long portfolioId) {
        if (portfolioId != null) {
            return portfolioRepository.findById(portfolioId)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        if (userId != null) {
            return portfolioRepository.findByUserId(userId);
        }
        return new ArrayList<>(portfolioRepository.findAll());
    }

    public record SnapshotResult(int total, int ingested, int skipped, int failed) {
    }
}
