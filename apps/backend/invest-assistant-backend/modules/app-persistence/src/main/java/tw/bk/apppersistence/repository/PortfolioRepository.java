package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.PortfolioEntity;

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long> {
    List<PortfolioEntity> findByUserId(Long userId);

    Optional<PortfolioEntity> findByIdAndUserId(Long id, Long userId);
}
