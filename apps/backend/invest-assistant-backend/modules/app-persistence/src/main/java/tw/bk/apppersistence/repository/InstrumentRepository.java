package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.bk.apppersistence.entity.InstrumentEntity;

/**
 * Instrument repository.
 */
@Repository
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

        /**
         * Find by symbol_key (e.g., US:XNAS:AAPL).
         */
        Optional<InstrumentEntity> findBySymbolKey(String symbolKey);

        /**
         * Find by symbol_key with market/exchange relations loaded.
         */
        @EntityGraph(attributePaths = { "market", "exchange" })
        @Query("SELECT i FROM InstrumentEntity i WHERE i.symbolKey = :symbolKey")
        Optional<InstrumentEntity> findBySymbolKeyWithRelations(@Param("symbolKey") String symbolKey);

        /**
         * Find by id with market/exchange relations loaded.
         */
        @EntityGraph(attributePaths = { "market", "exchange" })
        @Query("SELECT i FROM InstrumentEntity i WHERE i.id = :id")
        Optional<InstrumentEntity> findByIdWithRelations(@Param("id") Long id);

        /**
         * Find by ticker substring (case-insensitive).
         */
        List<InstrumentEntity> findByTickerContainingIgnoreCase(String ticker);

        /**
         * Find first by exact ticker match (case-insensitive).
         */
        Optional<InstrumentEntity> findFirstByTickerIgnoreCase(String ticker);

        /**
         * Find by market and status.
         */
        List<InstrumentEntity> findByMarketIdAndStatus(Long marketId, String status);

        /**
         * Search by ticker/name fields with pagination.
         */
        @Query("SELECT i FROM InstrumentEntity i WHERE " +
                        "LOWER(i.ticker) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(i.nameEn) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(i.nameZh) LIKE LOWER(CONCAT('%', :query, '%'))")
        List<InstrumentEntity> searchInstruments(@Param("query") String query, Pageable pageable);

        /**
         * Search with market/exchange relations loaded.
         */
        @EntityGraph(attributePaths = { "market", "exchange" })
        @Query("SELECT i FROM InstrumentEntity i WHERE " +
                        "LOWER(i.ticker) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(i.nameEn) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(i.nameZh) LIKE LOWER(CONCAT('%', :query, '%'))")
        List<InstrumentEntity> searchInstrumentsWithRelations(@Param("query") String query, Pageable pageable);

        /**
         * Find all with market/exchange relations loaded.
         */
        @EntityGraph(attributePaths = { "market", "exchange" })
        @Query("SELECT i FROM InstrumentEntity i")
        List<InstrumentEntity> findAllWithRelations();

        /**
         * Find all with market/exchange relations loaded (paged).
         */
        @EntityGraph(attributePaths = { "market", "exchange" })
        @Query("SELECT i FROM InstrumentEntity i")
        Page<InstrumentEntity> findAllWithRelations(Pageable pageable);

        /**
         * Find by symbol_key list.
         */
        List<InstrumentEntity> findBySymbolKeyIn(List<String> symbolKeys);
}
