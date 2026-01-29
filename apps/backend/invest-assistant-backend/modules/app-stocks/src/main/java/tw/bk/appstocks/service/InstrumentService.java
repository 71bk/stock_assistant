package tw.bk.appstocks.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;

import java.util.List;
import java.util.Optional;

/**
 * Instrument service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    /**
     * Find by symbol_key.
     */
    public Optional<InstrumentEntity> findBySymbolKey(String symbolKey) {
        return instrumentRepository.findBySymbolKeyWithRelations(symbolKey);
    }

    /**
     * Find by id.
     */
    public Optional<InstrumentEntity> findById(Long id) {
        return instrumentRepository.findById(id);
    }

    /**
     * Find by id with market/exchange relations loaded.
     */
    public Optional<InstrumentEntity> findByIdWithRelations(Long id) {
        return instrumentRepository.findByIdWithRelations(id);
    }

    /**
     * Search instruments by ticker or name (limit 1-50).
     */
    public List<InstrumentEntity> searchInstruments(String query, int limit) {
        // Clamp limit between 1 and 50.
        int validLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, validLimit);

        return instrumentRepository.searchInstrumentsWithRelations(query, pageable);
    }

    /**
     * Find all instruments.
     */
    public List<InstrumentEntity> findAll() {
        return instrumentRepository.findAllWithRelations();
    }

    /**
     * Find all instruments (paged).
     */
    public Page<InstrumentEntity> findAll(Pageable pageable) {
        return instrumentRepository.findAllWithRelations(pageable);
    }
}
