package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.bk.apppersistence.entity.InstrumentEntity;

/**
 * 商品資料存取層
 */
@Repository
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    /**
     * 根據 symbol_key 精確查詢（如 US:XNAS:AAPL）
     */
    Optional<InstrumentEntity> findBySymbolKey(String symbolKey);

    /**
     * 根據 ticker 模糊查詢
     */
    List<InstrumentEntity> findByTickerContainingIgnoreCase(String ticker);

    /**
     * 根據市場和狀態查詢
     */
    List<InstrumentEntity> findByMarketIdAndStatus(Long marketId, String status);

    /**
     * 搜尋商品（自動補全用）
     * 支援 ticker 和 name 的模糊搜尋
     */
    @Query("SELECT i FROM InstrumentEntity i WHERE " +
            "LOWER(i.ticker) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(i.nameEn) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(i.nameZh) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<InstrumentEntity> searchInstruments(@Param("query") String query, Pageable pageable);
}
