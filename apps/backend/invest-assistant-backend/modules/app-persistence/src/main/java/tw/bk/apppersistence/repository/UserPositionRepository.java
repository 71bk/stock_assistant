package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tw.bk.apppersistence.entity.UserPositionEntity;
import tw.bk.apppersistence.entity.UserPositionId;

public interface UserPositionRepository extends JpaRepository<UserPositionEntity, UserPositionId> {
    List<UserPositionEntity> findByPortfolioId(Long portfolioId);

    long countByPortfolioId(Long portfolioId);

    Optional<UserPositionEntity> findByPortfolioIdAndInstrumentId(Long portfolioId, Long instrumentId);

    @Query("""
            select distinct p.instrumentId
            from UserPositionEntity p
            where p.portfolioId = :portfolioId
            """)
    List<Long> findDistinctInstrumentIdsByPortfolioId(@Param("portfolioId") Long portfolioId);

    void deleteByPortfolioIdAndInstrumentId(Long portfolioId, Long instrumentId);
}
