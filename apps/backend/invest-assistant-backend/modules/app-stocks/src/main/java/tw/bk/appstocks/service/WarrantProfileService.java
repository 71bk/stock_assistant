package tw.bk.appstocks.service;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.apppersistence.entity.WarrantProfileEntity;
import tw.bk.apppersistence.repository.WarrantProfileRepository;

/**
 * Service for Warrant Profile operations.
 */
@Service
@RequiredArgsConstructor
public class WarrantProfileService {

    private final WarrantProfileRepository warrantProfileRepository;

    @Transactional(readOnly = true)
    public Optional<WarrantProfileEntity> findByInstrumentId(Long instrumentId) {
        return warrantProfileRepository.findByInstrumentId(instrumentId);
    }

    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public WarrantProfileEntity upsert(Long instrumentId, String underlyingSymbol, LocalDate expiryDate) {
        WarrantProfileEntity entity = warrantProfileRepository.findByInstrumentId(instrumentId)
                .orElseGet(WarrantProfileEntity::new);
        entity.setInstrumentId(instrumentId);
        if (underlyingSymbol != null && !underlyingSymbol.isBlank()) {
            entity.setUnderlyingSymbol(underlyingSymbol.trim());
        }
        if (expiryDate != null) {
            entity.setExpiryDate(expiryDate);
        }
        try {
            return warrantProfileRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            WarrantProfileEntity existing = warrantProfileRepository.findByInstrumentId(instrumentId)
                    .orElseThrow(() -> ex);
            if (underlyingSymbol != null && !underlyingSymbol.isBlank()) {
                existing.setUnderlyingSymbol(underlyingSymbol.trim());
            }
            if (expiryDate != null) {
                existing.setExpiryDate(expiryDate);
            }
            return warrantProfileRepository.save(existing);
        }
    }
}
