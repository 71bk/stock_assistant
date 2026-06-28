package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.apppersistence.entity.UserPositionEntity;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

/**
 * 持倉計算與重建。
 *
 * <p>由交易紀錄推導某 (portfolio, instrument) 的總量與加權平均成本，並把結果寫回
 * {@code user_position}。從 {@code PortfolioService} 抽出，集中持倉領域邏輯。
 * 由 {@code PortfolioService} 在其交易型 {@code @Transactional} 方法中呼叫，
 * 共用同一交易上下文。
 */
class PositionService {

    private final StockTradeRepository tradeRepository;
    private final UserPositionRepository positionRepository;
    private final ClockProvider clockProvider;

    PositionService(StockTradeRepository tradeRepository,
            UserPositionRepository positionRepository,
            ClockProvider clockProvider) {
        this.tradeRepository = tradeRepository;
        this.positionRepository = positionRepository;
        this.clockProvider = clockProvider;
    }

    /** 依交易紀錄重建單一持倉；無持倉或數量歸零時刪除該筆 position。 */
    void rebuildPosition(Long userId, Long portfolioId, Long instrumentId) {
        List<StockTradeEntity> trades = tradeRepository
                .findByUserIdAndPortfolioIdAndInstrumentIdOrderByTradeDateAscIdAsc(
                        userId, portfolioId, instrumentId);
        if (trades.isEmpty()) {
            positionRepository.deleteByPortfolioIdAndInstrumentId(portfolioId, instrumentId);
            return;
        }

        PositionState state = calculatePositionState(trades);

        if (state.totalQuantity().compareTo(BigDecimal.ZERO) == 0) {
            positionRepository.deleteByPortfolioIdAndInstrumentId(portfolioId, instrumentId);
            return;
        }

        UserPositionEntity position = positionRepository
                .findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)
                .orElseGet(UserPositionEntity::new);
        position.setPortfolioId(portfolioId);
        position.setInstrumentId(instrumentId);
        position.setTotalQuantity(state.totalQuantity());
        position.setAvgCostNative(state.avgCostNative().setScale(PortfolioAmounts.AVG_COST_SCALE, RoundingMode.HALF_UP));
        String currency = state.currency() == null ? PortfolioAmounts.DEFAULT_BASE_CURRENCY : state.currency();
        position.setCurrency(currency.toUpperCase(Locale.ROOT));
        position.setUpdatedAt(OffsetDateTime.ofInstant(clockProvider.now(), ZoneOffset.UTC));
        positionRepository.save(position);
    }

    /** 依時間排序的交易計算總量、加權平均成本與幣別；遇混幣或不合理數量時拋出 {@link BusinessException}。 */
    PositionState calculatePositionState(List<StockTradeEntity> trades) {
        if (trades == null || trades.isEmpty()) {
            return new PositionState(
                    BigDecimal.ZERO.setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(PortfolioAmounts.AVG_COST_SCALE, RoundingMode.HALF_UP),
                    null);
        }

        BigDecimal totalQuantity = BigDecimal.ZERO.setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal avgCost = BigDecimal.ZERO.setScale(PortfolioAmounts.AVG_COST_SCALE, RoundingMode.HALF_UP);
        String currency = null;

        for (StockTradeEntity trade : trades) {
            if (currency == null) {
                currency = trade.getCurrency();
            } else if (!currency.equalsIgnoreCase(trade.getCurrency())) {
                throw new BusinessException(ErrorCode.CONFLICT, "Mixed currencies in the same position");
            }

            BigDecimal fee = PortfolioAmounts.normalizeAmount(trade.getFee());
            BigDecimal tax = PortfolioAmounts.normalizeAmount(trade.getTax());

            if (TradeSide.BUY.name().equals(trade.getSide())) {
                BigDecimal newQty = totalQuantity.add(trade.getQuantity());
                if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Invalid buy quantity");
                }
                BigDecimal tradeCost = trade.getPrice().multiply(trade.getQuantity())
                        .add(fee)
                        .add(tax);
                BigDecimal totalCost = avgCost.multiply(totalQuantity).add(tradeCost);
                avgCost = totalCost.divide(newQty, PortfolioAmounts.AVG_COST_SCALE, RoundingMode.HALF_UP);
                totalQuantity = newQty.setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
            } else {
                BigDecimal newQty = totalQuantity.subtract(trade.getQuantity());
                if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Sell quantity exceeds holdings");
                }
                totalQuantity = newQty.setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
                if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    avgCost = BigDecimal.ZERO.setScale(PortfolioAmounts.AVG_COST_SCALE, RoundingMode.HALF_UP);
                }
            }
        }

        return new PositionState(totalQuantity, avgCost, currency);
    }

    /** 單一持倉的計算結果：總量、原幣加權平均成本、幣別。 */
    record PositionState(
            BigDecimal totalQuantity,
            BigDecimal avgCostNative,
            String currency) {
    }
}
