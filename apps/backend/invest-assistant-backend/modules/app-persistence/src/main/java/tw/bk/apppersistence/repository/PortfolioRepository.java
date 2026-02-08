package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tw.bk.apppersistence.entity.PortfolioEntity;

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long> {
    List<PortfolioEntity> findByUserId(Long userId);

    Optional<PortfolioEntity> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PortfolioEntity p WHERE p.id = :id AND p.userId = :userId")
    Optional<PortfolioEntity> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
