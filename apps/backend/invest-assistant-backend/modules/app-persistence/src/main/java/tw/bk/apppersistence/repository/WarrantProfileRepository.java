package tw.bk.apppersistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.WarrantProfileEntity;

public interface WarrantProfileRepository extends JpaRepository<WarrantProfileEntity, Long> {
    Optional<WarrantProfileEntity> findByInstrumentId(Long instrumentId);
}
