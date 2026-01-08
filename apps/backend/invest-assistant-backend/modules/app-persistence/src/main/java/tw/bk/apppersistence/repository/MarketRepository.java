package tw.bk.apppersistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.MarketEntity;

public interface MarketRepository extends JpaRepository<MarketEntity, Long> {
    Optional<MarketEntity> findByCode(String code);
}
