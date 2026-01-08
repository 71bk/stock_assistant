package tw.bk.apppersistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.ExchangeEntity;

public interface ExchangeRepository extends JpaRepository<ExchangeEntity, Long> {
    List<ExchangeEntity> findByMarketId(Long marketId);
}
