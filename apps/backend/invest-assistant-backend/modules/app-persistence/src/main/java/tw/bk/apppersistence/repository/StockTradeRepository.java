package tw.bk.apppersistence.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.StockTradeEntity;

public interface StockTradeRepository extends JpaRepository<StockTradeEntity, Long> {
        Optional<StockTradeEntity> findByIdAndUserId(Long id, Long userId);

        Page<StockTradeEntity> findByUserIdAndPortfolioId(Long userId, Long portfolioId, Pageable pageable);

        Page<StockTradeEntity> findByUserIdAndPortfolioIdAndTradeDateBetween(
                        Long userId, Long portfolioId, LocalDate from, LocalDate to, Pageable pageable);

        Page<StockTradeEntity> findByUserIdAndPortfolioIdAndTradeDateGreaterThanEqual(
                        Long userId, Long portfolioId, LocalDate from, Pageable pageable);

        Page<StockTradeEntity> findByUserIdAndPortfolioIdAndTradeDateLessThanEqual(
                        Long userId, Long portfolioId, LocalDate to, Pageable pageable);

        List<StockTradeEntity> findByUserIdAndPortfolioIdAndInstrumentIdOrderByTradeDateAscIdAsc(
                        Long userId, Long portfolioId, Long instrumentId);

        // 檢查是否已存在相同的交易（用於 OCR 重複檢測）
        boolean existsByPortfolioIdAndInstrumentIdAndTradeDateAndSideAndQuantityAndPrice(
                        Long portfolioId, Long instrumentId, LocalDate tradeDate,
                        String side, BigDecimal quantity, BigDecimal price);
}
