package tw.bk.apppersistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.PortfolioValuationEntity;
import tw.bk.apppersistence.entity.PortfolioValuationId;

public interface PortfolioValuationRepository extends JpaRepository<PortfolioValuationEntity, PortfolioValuationId> {
    List<PortfolioValuationEntity> findByPortfolioIdAndAsOfDateBetweenOrderByAsOfDateAsc(
            Long portfolioId,
            LocalDate from,
            LocalDate to);

    Optional<PortfolioValuationEntity> findTopByPortfolioIdOrderByAsOfDateDesc(Long portfolioId);
}
