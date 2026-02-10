package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appportfolio.model.PortfolioSummary;
import tw.bk.appportfolio.model.PortfolioRefView;
import tw.bk.appportfolio.model.PortfolioPositionsRebuildResult;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;
import tw.bk.appportfolio.model.PortfolioView;
import tw.bk.appportfolio.model.PositionWithQuote;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.model.TradeView;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.PortfolioValuationEntity;
import tw.bk.apppersistence.entity.PortfolioValuationId;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.apppersistence.entity.UserPositionEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.PortfolioValuationRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

@Service
public class PortfolioService {
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final int AMOUNT_SCALE = 6;
    private static final int PRICE_SCALE = 8;
    private static final int AVG_COST_SCALE = 8;
    private static final String DEFAULT_PORTFOLIO_NAME = "Main";
    private static final String DEFAULT_BASE_CURRENCY = "TWD";
    private static final String SOURCE_MANUAL = TradeSource.MANUAL.name();

    private final PortfolioRepository portfolioRepository;
    private final PortfolioValuationRepository portfolioValuationRepository;
    private final StockTradeRepository tradeRepository;
    private final UserPositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final ClockProvider clockProvider;

    public PortfolioService(PortfolioRepository portfolioRepository,
            PortfolioValuationRepository portfolioValuationRepository,
            StockTradeRepository tradeRepository,
            UserPositionRepository positionRepository,
            InstrumentRepository instrumentRepository,
            ClockProvider clockProvider) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioValuationRepository = portfolioValuationRepository;
        this.tradeRepository = tradeRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.clockProvider = clockProvider;
    }

    public PortfolioView createPortfolio(Long userId, String name, String baseCurrency) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setUserId(userId);
        portfolio.setName(isBlank(name) ? DEFAULT_PORTFOLIO_NAME : name.trim());
        portfolio.setBaseCurrency(
                isBlank(baseCurrency) ? DEFAULT_BASE_CURRENCY : baseCurrency.trim().toUpperCase(Locale.ROOT));
        return toPortfolioView(portfolioRepository.save(portfolio));
    }

    public List<PortfolioView> listPortfolios(Long userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(this::toPortfolioView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioRefView> findPortfolioRefById(Long portfolioId) {
        return portfolioRepository.findById(portfolioId).map(this::toPortfolioRefView);
    }

    @Transactional(readOnly = true)
    public List<PortfolioRefView> listPortfolioRefsByUser(Long userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(this::toPortfolioRefView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PortfolioRefView> listAllPortfolioRefs() {
        return portfolioRepository.findAll().stream()
                .map(this::toPortfolioRefView)
                .toList();
    }

    public PortfolioView getPortfolio(Long userId, Long portfolioId) {
        return toPortfolioView(requirePortfolioEntity(userId, portfolioId));
    }

    private PortfolioEntity requirePortfolioEntity(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
    }

    @Transactional(readOnly = true)
    public List<PortfolioValuationView> listValuations(Long userId, Long portfolioId, LocalDate from, LocalDate to) {
        requirePortfolioEntity(userId, portfolioId);

        LocalDate safeTo = to != null ? to : clockProvider.nowUtc().toLocalDate();
        LocalDate safeFrom = from != null ? from : safeTo.minusDays(30);
        if (safeFrom.isAfter(safeTo)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from must be <= to");
        }

        return portfolioValuationRepository.findByPortfolioIdAndAsOfDateBetweenOrderByAsOfDateAsc(
                portfolioId,
                safeFrom,
                safeTo)
                .stream()
                .map(this::toPortfolioValuationView)
                .toList();
    }

    @Transactional
    public PortfolioValuationSnapshotResult snapshotValuations(
            Long userId,
            Long portfolioId,
            LocalDate asOfDate,
            QuoteProvider quoteProvider) {
        LocalDate snapshotDate = asOfDate != null ? asOfDate : clockProvider.nowUtc().toLocalDate();
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

    @Transactional
    public PortfolioValuationSnapshotResult snapshotValuations(LocalDate asOfDate, QuoteProvider quoteProvider) {
        return snapshotValuations(null, null, asOfDate, quoteProvider);
    }

    private PortfolioEntity lockPortfolio(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserIdForUpdate(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
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

    private void upsertValuationSnapshot(PortfolioEntity portfolio, LocalDate asOfDate, QuoteProvider quoteProvider) {
        BigDecimal positionsValue = calculatePositionsValue(portfolio.getId(), asOfDate, quoteProvider);
        BigDecimal cashValue = calculateCashValue(portfolio.getId(), asOfDate);
        BigDecimal totalValue = positionsValue.add(cashValue).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        PortfolioValuationId id = new PortfolioValuationId(portfolio.getId(), asOfDate);
        PortfolioValuationEntity entity = portfolioValuationRepository.findById(id)
                .orElseGet(PortfolioValuationEntity::new);

        entity.setPortfolioId(portfolio.getId());
        entity.setAsOfDate(asOfDate);
        entity.setBaseCurrency(normalizeBaseCurrency(portfolio.getBaseCurrency()));
        entity.setTotalValue(totalValue);
        entity.setCashValue(cashValue);
        entity.setPositionsValue(positionsValue);
        portfolioValuationRepository.save(entity);
    }

    private BigDecimal calculateCashValue(Long portfolioId, LocalDate asOfDate) {
        BigDecimal netAmount = tradeRepository.sumNetAmountByPortfolioIdAsOfDate(portfolioId, asOfDate);
        if (netAmount == null) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return netAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePositionsValue(Long portfolioId, LocalDate asOfDate, QuoteProvider quoteProvider) {
        List<Long> instrumentIds = tradeRepository.findDistinctInstrumentIdsByPortfolioIdAndTradeDateLessThanEqual(
                portfolioId,
                asOfDate);
        if (instrumentIds.isEmpty()) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }

        Map<Long, InstrumentEntity> instrumentById = loadInstrumentsById(instrumentIds);
        LocalDate today = clockProvider.nowUtc().toLocalDate();
        BigDecimal total = BigDecimal.ZERO;

        for (Long instrumentId : instrumentIds) {
            List<StockTradeEntity> trades = tradeRepository
                    .findByPortfolioIdAndInstrumentIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                            portfolioId,
                            instrumentId,
                            asOfDate);
            PositionState state = calculatePositionState(trades);
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
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            total = total.add(marketValue);
        }

        return total.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
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

    private String normalizeBaseCurrency(String baseCurrency) {
        if (isBlank(baseCurrency)) {
            return DEFAULT_BASE_CURRENCY;
        }
        return baseCurrency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Calculate portfolio summary statistics with real-time quotes.
     *
     * @param userId        User ID
     * @param portfolioId   Portfolio ID
     * @param quoteProvider Quote provider for real-time prices
     * @return PortfolioSummary with totalMarketValue, totalCost, totalPnl,
     *         totalPnlPercent
     */
    public PortfolioSummary getPortfolioSummary(Long userId, Long portfolioId, QuoteProvider quoteProvider) {
        requirePortfolioEntity(userId, portfolioId);
        List<UserPositionEntity> positions = positionRepository.findByPortfolioId(portfolioId);

        if (positions.isEmpty()) {
            return PortfolioSummary.empty();
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMarketValue = BigDecimal.ZERO;

        for (UserPositionEntity position : positions) {
            BigDecimal quantity = position.getTotalQuantity();
            BigDecimal avgCost = position.getAvgCostNative();

            // Calculate cost for this position
            BigDecimal positionCost = avgCost.multiply(quantity)
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            totalCost = totalCost.add(positionCost);

            // Get real-time price from quote provider, fallback to avgCost if unavailable
            BigDecimal currentPrice = avgCost;
            if (quoteProvider != null) {
                InstrumentEntity instrument = instrumentRepository.findById(position.getInstrumentId()).orElse(null);
                if (instrument != null && instrument.getSymbolKey() != null) {
                    currentPrice = quoteProvider.getCurrentPrice(instrument.getSymbolKey())
                            .orElse(avgCost);
                }
            }
            BigDecimal positionMarketValue = currentPrice.multiply(quantity)
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            totalMarketValue = totalMarketValue.add(positionMarketValue);
        }

        BigDecimal totalPnl = totalMarketValue.subtract(totalCost)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalPnlPercent = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            totalPnlPercent = totalPnl
                    .divide(totalCost, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new PortfolioSummary(totalMarketValue, totalCost, totalPnl, totalPnlPercent);
    }

    public Page<TradeView> listTrades(Long userId,
            Long portfolioId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        requirePortfolioEntity(userId, portfolioId);
        Page<StockTradeEntity> trades;
        if (from != null && to != null) {
            trades = tradeRepository.findByUserIdAndPortfolioIdAndTradeDateBetween(userId, portfolioId, from, to,
                    pageable);
            return trades.map(this::toTradeView);
        }
        if (from != null) {
            trades = tradeRepository.findByUserIdAndPortfolioIdAndTradeDateGreaterThanEqual(userId, portfolioId, from,
                    pageable);
            return trades.map(this::toTradeView);
        }
        if (to != null) {
            trades = tradeRepository.findByUserIdAndPortfolioIdAndTradeDateLessThanEqual(userId, portfolioId, to,
                    pageable);
            return trades.map(this::toTradeView);
        }
        trades = tradeRepository.findByUserIdAndPortfolioId(userId, portfolioId, pageable);
        return trades.map(this::toTradeView);
    }

    public List<UserPositionEntity> listPositions(Long userId, Long portfolioId) {
        requirePortfolioEntity(userId, portfolioId);
        return positionRepository.findByPortfolioId(portfolioId);
    }

    @Transactional
    public PortfolioPositionsRebuildResult rebuildPositions(Long portfolioId, Long instrumentId) {
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
        Long userId = portfolio.getUserId();

        Set<Long> targetInstrumentIds = resolveRebuildTargets(userId, portfolioId, instrumentId);
        if (targetInstrumentIds.isEmpty()) {
            return new PortfolioPositionsRebuildResult(
                    portfolioId,
                    userId,
                    0,
                    0,
                    0,
                    List.of());
        }

        int rebuiltCount = 0;
        List<Long> failedInstrumentIds = new ArrayList<>();
        for (Long targetInstrumentId : targetInstrumentIds) {
            try {
                rebuildPosition(userId, portfolioId, targetInstrumentId);
                rebuiltCount++;
            } catch (RuntimeException ex) {
                failedInstrumentIds.add(targetInstrumentId);
                log.warn(
                        "Rebuild position failed: portfolioId={}, userId={}, instrumentId={}, error={}",
                        portfolioId,
                        userId,
                        targetInstrumentId,
                        ex.getMessage(),
                        ex);
            }
        }

        return new PortfolioPositionsRebuildResult(
                portfolioId,
                userId,
                targetInstrumentIds.size(),
                rebuiltCount,
                failedInstrumentIds.size(),
                List.copyOf(failedInstrumentIds));
    }

    /**
     * 取得持倉列表（含報價與損益計算）
     *
     * @param userId        使用者 ID
     * @param portfolioId   投資組合 ID
     * @param quoteProvider 報價查詢函數
     * @return 持倉資料列表（含市值與損益）
     */
    public List<PositionWithQuote> listPositionsWithQuotes(Long userId, Long portfolioId, QuoteProvider quoteProvider) {
        requirePortfolioEntity(userId, portfolioId);
        List<UserPositionEntity> positions = positionRepository.findByPortfolioId(portfolioId);

        return positions.stream()
                .map(pos -> buildPositionWithQuote(pos, quoteProvider))
                .toList();
    }

    private Set<Long> resolveRebuildTargets(Long userId, Long portfolioId, Long instrumentId) {
        Set<Long> targets = new LinkedHashSet<>();
        if (instrumentId != null) {
            targets.add(instrumentId);
            return targets;
        }
        targets.addAll(tradeRepository.findDistinctInstrumentIdsByUserIdAndPortfolioId(userId, portfolioId));
        targets.addAll(positionRepository.findDistinctInstrumentIdsByPortfolioId(portfolioId));
        return targets;
    }

    private PositionWithQuote buildPositionWithQuote(UserPositionEntity pos, QuoteProvider quoteProvider) {
        InstrumentEntity instrument = instrumentRepository.findById(pos.getInstrumentId()).orElse(null);

        String ticker = instrument != null ? instrument.getTicker() : null;
        String name = instrument != null ? instrument.getNameZh() : null;
        String symbolKey = instrument != null ? instrument.getSymbolKey() : null;

        BigDecimal quantity = pos.getTotalQuantity();
        BigDecimal avgCost = pos.getAvgCostNative();

        // 查詢報價
        BigDecimal currentPrice = null;
        if (symbolKey != null && quoteProvider != null) {
            currentPrice = quoteProvider.getCurrentPrice(symbolKey).orElse(null);
        }

        // 計算損益
        BigDecimal marketValue = null;
        BigDecimal unrealizedPnl = null;
        BigDecimal unrealizedPnlPercent = null;

        if (currentPrice != null && quantity != null && avgCost != null) {
            marketValue = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalCost = avgCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            unrealizedPnl = marketValue.subtract(totalCost);

            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                unrealizedPnlPercent = unrealizedPnl
                        .divide(totalCost, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        return new PositionWithQuote(
                pos.getPortfolioId(),
                pos.getInstrumentId(),
                ticker,
                name,
                quantity,
                avgCost,
                pos.getCurrency(),
                currentPrice,
                marketValue,
                unrealizedPnl,
                unrealizedPnlPercent);
    }

    @Transactional
    public TradeView createTrade(Long userId, Long portfolioId, TradeCommand command) {
        lockPortfolio(userId, portfolioId);
        InstrumentEntity instrument = requireInstrument(command.instrumentId());
        validateCurrency(instrument, command.currency());

        StockTradeEntity trade = new StockTradeEntity();
        trade.setUserId(userId);
        trade.setPortfolioId(portfolioId);
        applyTradeFields(trade, command);

        try {
            tradeRepository.saveAndFlush(trade);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "Duplicate trade");
        }

        rebuildPosition(userId, portfolioId, command.instrumentId());
        return toTradeView(trade);
    }

    @Transactional
    public TradeView updateTrade(Long userId, Long tradeId, TradeCommand command) {
        StockTradeEntity trade = tradeRepository.findByIdAndUserId(tradeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Trade not found"));

        Long portfolioId = trade.getPortfolioId();
        lockPortfolio(userId, portfolioId);

        Long originalInstrumentId = trade.getInstrumentId();
        InstrumentEntity instrument = requireInstrument(command.instrumentId());
        validateCurrency(instrument, command.currency());

        applyTradeFields(trade, command);

        try {
            tradeRepository.saveAndFlush(trade);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "Duplicate trade");
        }

        if (!originalInstrumentId.equals(command.instrumentId())) {
            rebuildPosition(userId, portfolioId, originalInstrumentId);
        }
        rebuildPosition(userId, portfolioId, command.instrumentId());
        return toTradeView(trade);
    }

    @Transactional
    public void deleteTrade(Long userId, Long tradeId) {
        StockTradeEntity trade = tradeRepository.findByIdAndUserId(tradeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Trade not found"));

        Long portfolioId = trade.getPortfolioId();
        Long instrumentId = trade.getInstrumentId();
        lockPortfolio(userId, portfolioId);
        tradeRepository.delete(trade);
        tradeRepository.flush();
        rebuildPosition(userId, portfolioId, instrumentId);
    }

    private void applyTradeFields(StockTradeEntity trade, TradeCommand command) {
        BigDecimal quantity = normalizeQuantity(command.quantity());
        BigDecimal price = normalizePrice(command.price());
        BigDecimal fee = normalizeAmount(command.fee());
        BigDecimal tax = normalizeAmount(command.tax());

        trade.setInstrumentId(command.instrumentId());
        trade.setTradeDate(command.tradeDate());
        trade.setSettlementDate(command.settlementDate());
        trade.setSide(command.side().name());
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setCurrency(command.currency().toUpperCase(Locale.ROOT));
        trade.setFee(fee);
        trade.setTax(tax);
        trade.setAccountId(command.accountId());
        trade.setSource(command.source() == null ? SOURCE_MANUAL : command.source());

        BigDecimal gross = price.multiply(quantity).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal fees = fee.add(tax).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal net;
        if (command.side() == TradeSide.BUY) {
            net = gross.add(fees).negate();
        } else {
            net = gross.subtract(fees);
        }
        trade.setGrossAmount(gross);
        trade.setNetAmount(net.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        trade.setRowHash(buildRowHash(trade));
    }

    private void rebuildPosition(Long userId, Long portfolioId, Long instrumentId) {
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
        position.setAvgCostNative(state.avgCostNative().setScale(AVG_COST_SCALE, RoundingMode.HALF_UP));
        String currency = state.currency() == null ? DEFAULT_BASE_CURRENCY : state.currency();
        position.setCurrency(currency.toUpperCase(Locale.ROOT));
        position.setUpdatedAt(OffsetDateTime.ofInstant(clockProvider.now(), java.time.ZoneOffset.UTC));
        positionRepository.save(position);
    }

    private PositionState calculatePositionState(List<StockTradeEntity> trades) {
        if (trades == null || trades.isEmpty()) {
            return new PositionState(
                    BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(AVG_COST_SCALE, RoundingMode.HALF_UP),
                    null);
        }

        BigDecimal totalQuantity = BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal avgCost = BigDecimal.ZERO.setScale(AVG_COST_SCALE, RoundingMode.HALF_UP);
        String currency = null;

        for (StockTradeEntity trade : trades) {
            if (currency == null) {
                currency = trade.getCurrency();
            } else if (!currency.equalsIgnoreCase(trade.getCurrency())) {
                throw new BusinessException(ErrorCode.CONFLICT, "Mixed currencies in the same position");
            }

            BigDecimal fee = normalizeAmount(trade.getFee());
            BigDecimal tax = normalizeAmount(trade.getTax());

            if (TradeSide.BUY.name().equals(trade.getSide())) {
                BigDecimal newQty = totalQuantity.add(trade.getQuantity());
                if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Invalid buy quantity");
                }
                BigDecimal tradeCost = trade.getPrice().multiply(trade.getQuantity())
                        .add(fee)
                        .add(tax);
                BigDecimal totalCost = avgCost.multiply(totalQuantity).add(tradeCost);
                avgCost = totalCost.divide(newQty, AVG_COST_SCALE, RoundingMode.HALF_UP);
                totalQuantity = newQty.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            } else {
                BigDecimal newQty = totalQuantity.subtract(trade.getQuantity());
                if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Sell quantity exceeds holdings");
                }
                totalQuantity = newQty.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
                if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    avgCost = BigDecimal.ZERO.setScale(AVG_COST_SCALE, RoundingMode.HALF_UP);
                }
            }
        }

        return new PositionState(totalQuantity, avgCost, currency);
    }

    private InstrumentEntity requireInstrument(Long instrumentId) {
        Optional<InstrumentEntity> instrument = instrumentRepository.findById(instrumentId);
        return instrument.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Instrument not found"));
    }

    private void validateCurrency(InstrumentEntity instrument, String currency) {
        if (currency == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Currency is required");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals(instrument.getCurrency())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Currency does not match instrument currency");
        }
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quantity must be greater than 0");
        }
        return quantity.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Price must be greater than 0");
        }
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Amount cannot be negative");
        }
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private String buildRowHash(StockTradeEntity trade) {
        String payload = trade.getUserId() + "|"
                + trade.getPortfolioId() + "|"
                + trade.getInstrumentId() + "|"
                + trade.getTradeDate() + "|"
                + trade.getSide() + "|"
                + trade.getQuantity().toPlainString() + "|"
                + trade.getPrice().toPlainString() + "|"
                + trade.getCurrency() + "|"
                + normalizeAmount(trade.getFee()).toPlainString() + "|"
                + normalizeAmount(trade.getTax()).toPlainString() + "|"
                + (trade.getAccountId() == null ? "" : trade.getAccountId());
        return sha256Hex(payload);
    }

    private String sha256Hex(String input) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private PortfolioView toPortfolioView(PortfolioEntity entity) {
        return new PortfolioView(
                entity.getId(),
                entity.getName(),
                entity.getBaseCurrency());
    }

    private PortfolioRefView toPortfolioRefView(PortfolioEntity entity) {
        return new PortfolioRefView(
                entity.getId(),
                entity.getUserId());
    }

    private PortfolioValuationView toPortfolioValuationView(PortfolioValuationEntity entity) {
        return new PortfolioValuationView(
                entity.getAsOfDate(),
                entity.getTotalValue(),
                entity.getCashValue(),
                entity.getPositionsValue(),
                entity.getBaseCurrency());
    }

    private TradeView toTradeView(StockTradeEntity entity) {
        return new TradeView(
                entity.getId(),
                entity.getInstrumentId(),
                entity.getTradeDate(),
                entity.getSettlementDate(),
                entity.getSideEnum(),
                entity.getQuantity(),
                entity.getPrice(),
                entity.getCurrency(),
                entity.getGrossAmount(),
                entity.getFee(),
                entity.getTax(),
                entity.getNetAmount(),
                entity.getSourceEnum(),
                entity.getAccountId());
    }

    private record PositionState(
            BigDecimal totalQuantity,
            BigDecimal avgCostNative,
            String currency) {
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
