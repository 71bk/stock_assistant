package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.UserPositionEntity;
import tw.bk.apppersistence.entity.UserPositionId;

public interface UserPositionRepository extends JpaRepository<UserPositionEntity, UserPositionId> {
    List<UserPositionEntity> findByPortfolioId(Long portfolioId);

    Optional<UserPositionEntity> findByPortfolioIdAndInstrumentId(Long portfolioId, Long instrumentId);

    void deleteByPortfolioIdAndInstrumentId(Long portfolioId, Long instrumentId);
}
