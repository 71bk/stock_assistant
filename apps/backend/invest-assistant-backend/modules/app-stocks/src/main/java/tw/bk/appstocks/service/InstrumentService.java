package tw.bk.appstocks.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.ExchangeEntity;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.MarketEntity;
import tw.bk.apppersistence.repository.ExchangeRepository;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.MarketRepository;

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
    private final MarketRepository marketRepository;
    private final ExchangeRepository exchangeRepository;

    /**
     * Create a new instrument manually.
     */
    @Transactional
    public InstrumentEntity createInstrument(String ticker, String nameZh, String nameEn,
            String marketCode, String exchangeCode,
            String currency, String assetType) {
        // Find market
        MarketEntity market = marketRepository.findByCode(marketCode.toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "不支援的市場: " + marketCode));

        // Find exchange
        ExchangeEntity exchange = exchangeRepository.findByMarketIdAndCodeIgnoreCase(market.getId(), exchangeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "不支援的交易所: " + exchangeCode + " (市場: " + marketCode + ")"));

        // Build symbol_key with MIC to keep uniqueness consistent with sync process
        String symbolKey = marketCode.toUpperCase() + ":" + exchange.getMic().toUpperCase() + ":"
                + ticker.toUpperCase();

        // Check if already exists
        if (instrumentRepository.findBySymbolKey(symbolKey).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "商品已存在: " + symbolKey);
        }

        // Create entity
        InstrumentEntity entity = new InstrumentEntity();
        entity.setMarket(market);
        entity.setExchange(exchange);
        entity.setTicker(ticker.toUpperCase());
        entity.setNameZh(nameZh);
        entity.setNameEn(nameEn);
        entity.setCurrency(currency.toUpperCase());
        entity.setAssetType(assetType != null ? assetType.toUpperCase() : "STOCK");
        entity.setStatus("ACTIVE");
        entity.setSymbolKey(symbolKey);

        return instrumentRepository.save(entity);
    }

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
