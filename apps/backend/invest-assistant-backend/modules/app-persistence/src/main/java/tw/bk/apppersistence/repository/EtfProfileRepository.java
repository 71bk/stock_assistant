package tw.bk.apppersistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.bk.apppersistence.entity.EtfProfileEntity;

import java.util.Optional;

/**
 * Repository for ETF Profile data
 */
@Repository
public interface EtfProfileRepository extends JpaRepository<EtfProfileEntity, Long> {

    /**
     * Find ETF profile by instrument ID
     */
    Optional<EtfProfileEntity> findByInstrumentId(Long instrumentId);
}
