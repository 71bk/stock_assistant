package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appportfolio.mapper.PortfolioMapper;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.PortfolioValuationEntity;
import tw.bk.apppersistence.entity.PortfolioValuationId;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.PortfolioValuationRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;

/**
 * 投資組合估值與每日快照。
 *
 * <p>計算持倉市值（含即時報價）、現金（淨交易額）與總值，並 upsert 每日快照。
 * 從 {@code PortfolioService} 抽出以集中估值邏輯；交易語意（{@code @Transactional}）
 * 維持在呼叫端 {@code PortfolioService} 的公開方法上。
 */
class PortfolioValuationService {
    private static final Logger log = LoggerFactory.getLogger(PortfolioValuationService.class);

    private final PortfolioRepository portfolioRepository;
    private final PortfolioValuationRepository portfolioValuationRepository;
    private final StockTradeRepository tradeRepository;
    private final InstrumentRepository instrumentRepository;
    private final PositionService positionService;
    private final PortfolioMapper mapper;
    private final Supplier<LocalDate> nowValuationDate;

    PortfolioValuationService(PortfolioRepository portfolioRepository,
            PortfolioValuationRepository portfolioValuationRepository,
            StockTradeRepository tradeRepository,
            InstrumentRepository instrumentRepository,
            PositionService positionService,
            PortfolioMapper mapper,
            Supplier<LocalDate> nowValuationDate) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioValuationRepository = portfolioValuationRepository;
        this.tradeRepository = tradeRepository;
        this.instrumentRepository = instrumentRepository;
        this.positionService = positionService;
        this.mapper = mapper;
        this.nowValuationDate = nowValuationDate;
    }

    List<PortfolioValuationView> listValuations(Long userId, Long portfolioId, LocalDate from, LocalDate to) {
        requireOwnedPortfolio(userId, portfolioId);

        LocalDate safeTo = to != null ? to : nowValuationDate.get();
        LocalDate safeFrom = from != null ? from : safeTo.minusDays(30);
        if (safeFrom.isAfter(safeTo)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from must be <= to");
        }

        return portfolioValuationRepository.findByPortfolioIdAndAsOfDateBetweenOrderByAsOfDateAsc(
                portfolioId,
                safeFrom,
                safeTo)
                .stream()
                .map(mapper::toPortfolioValuationView)
                .toList();
    }

    PortfolioValuationSnapshotResult snapshotValuations(
            Long userId,
            Long portfolioId,
            LocalDate asOfDate,
            QuoteProvider quoteProvider) {
        LocalDate snapshotDate = asOfDate != null ? asOfDate : nowValuationDate.get();
        List<PortfolioEntity> targets = resolveValuationTargets(userId, portfolioId);

        int succeeded = 0;
        List<Long> failedPortfolioIds = new ArrayList<>();
        for (PortfolioEntity portfolio : targets) {
            Long targetPortfolioId = portfolio.getId();
            try {
                upsertValuationSnapshot(portfolio, snapshotDate, quoteProvider);
                succeeded++;
            } catch (RuntimeException ex) {
                failedPortfolioIds.add(targetPortfolioId);
                log.warn("Portfolio valuation snapshot failed: portfolioId={}, userId={}, asOfDate={}, error={}",
                        targetPortfolioId, portfolio.getUserId(), snapshotDate, ex.getMessage(), ex);
            }
        }

        return new PortfolioValuationSnapshotResult(
                snapshotDate,
                targets.size(),
                succeeded,
                failedPortfolioIds.size(),
                failedPortfolioIds);
    }

    PortfolioValuationSnapshotResult snapshotValuations(LocalDate asOfDate, QuoteProvider quoteProvider) {
        return snapshotValuations(null, null, asOfDate, quoteProvider);
    }

    BigDecimal calculateCashValue(Long portfolioId, LocalDate asOfDate) {
        BigDecimal netAmount = tradeRepository.sumNetAmountByPortfolioIdAsOfDate(portfolioId, asOfDate);
        if (netAmount == null) {
            return PortfolioAmounts.zeroAmount();
        }
        return netAmount.setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    BigDecimal calculatePositionsValue(Long portfolioId, LocalDate asOfDate, QuoteProvider quoteProvider) {
        List<Long> instrumentIds = tradeRepository.findDistinctInstrumentIdsByPortfolioIdAndTradeDateLessThanEqual(
                portfolioId,
                asOfDate);
        if (instrumentIds.isEmpty()) {
            return PortfolioAmounts.zeroAmount();
        }

        Map<Long, InstrumentEntity> instrumentById = loadInstrumentsById(instrumentIds);
        LocalDate today = nowValuationDate.get();
        BigDecimal total = BigDecimal.ZERO;

        for (Long instrumentId : instrumentIds) {
            List<StockTradeEntity> trades = tradeRepository
                    .findByPortfolioIdAndInstrumentIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                            portfolioId,
                            instrumentId,
                            asOfDate);
            var state = positionService.calculatePositionState(trades);
            if (state.totalQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal currentPrice = state.avgCostNative();
            InstrumentEntity instrument = instrumentById.get(instrumentId);
            String symbolKey = instrument != null ? instrument.getSymbolKey() : null;
            if (quoteProvider != null && symbolKey != null && !symbolKey.isBlank()) {
                try {
                    currentPrice = quoteProvider.getPrice(symbolKey, asOfDate, today).orElse(currentPrice);
                } catch (RuntimeException ex) {
                    log.warn(
                            "Quote lookup failed during valuation snapshot: portfolioId={}, instrumentId={}, symbolKey={}, error={}",
                            portfolioId,
                            instrumentId,
                            symbolKey,
                            ex.getMessage());
                }
            }

            BigDecimal marketValue = currentPrice.multiply(state.totalQuantity())
                    .setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
            total = total.add(marketValue);
        }

        return total.setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private void upsertValuationSnapshot(PortfolioEntity portfolio, LocalDate asOfDate, QuoteProvider quoteProvider) {
        BigDecimal positionsValue = calculatePositionsValue(portfolio.getId(), asOfDate, quoteProvider);
        BigDecimal cashValue = calculateCashValue(portfolio.getId(), asOfDate);
        BigDecimal totalValue = positionsValue.add(cashValue).setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);

        PortfolioValuationId id = new PortfolioValuationId(portfolio.getId(), asOfDate);
        PortfolioValuationEntity entity = portfolioValuationRepository.findById(id)
                .orElseGet(PortfolioValuationEntity::new);

        entity.setPortfolioId(portfolio.getId());
        entity.setAsOfDate(asOfDate);
        entity.setBaseCurrency(PortfolioAmounts.normalizeBaseCurrency(portfolio.getBaseCurrency()));
        entity.setTotalValue(totalValue);
        entity.setCashValue(cashValue);
        entity.setPositionsValue(positionsValue);
        portfolioValuationRepository.save(entity);
    }

    private List<PortfolioEntity> resolveValuationTargets(Long userId, Long portfolioId) {
        if (portfolioId != null) {
            Optional<PortfolioEntity> target = userId != null
                    ? portfolioRepository.findByIdAndUserId(portfolioId, userId)
                    : portfolioRepository.findById(portfolioId);
            return target.map(List::of).orElseGet(List::of);
        }
        if (userId != null) {
            return portfolioRepository.findByUserId(userId);
        }
        return portfolioRepository.findAll();
    }

    private Map<Long, InstrumentEntity> loadInstrumentsById(List<Long> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> dedupIds = new LinkedHashSet<>(instrumentIds);
        Map<Long, InstrumentEntity> result = new HashMap<>();
        for (InstrumentEntity instrument : instrumentRepository.findAllById(dedupIds)) {
            result.put(instrument.getId(), instrument);
        }
        return result;
    }

    private void requireOwnedPortfolio(Long userId, Long portfolioId) {
        portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
    }
}
