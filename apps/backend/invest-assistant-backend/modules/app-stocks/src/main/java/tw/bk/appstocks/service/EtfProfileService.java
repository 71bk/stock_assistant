package tw.bk.appstocks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appstocks.model.EtfProfileView;
import tw.bk.apppersistence.entity.EtfProfileEntity;
import tw.bk.apppersistence.repository.EtfProfileRepository;

import java.util.Optional;

/**
 * Service for ETF Profile operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EtfProfileService {

    private final EtfProfileRepository etfProfileRepository;

    /**
     * Find ETF profile by instrument ID
     */
    @Transactional(readOnly = true)
    public Optional<EtfProfileEntity> findByInstrumentId(Long instrumentId) {
        return etfProfileRepository.findByInstrumentId(instrumentId);
    }

    @Transactional(readOnly = true)
    public Optional<EtfProfileView> findViewByInstrumentId(Long instrumentId) {
        return findByInstrumentId(instrumentId).map(this::toView);
    }

    private EtfProfileView toView(EtfProfileEntity entity) {
        return new EtfProfileView(
                entity.getUnderlyingType(),
                entity.getUnderlyingName(),
                entity.getAsOfDate());
    }
}
