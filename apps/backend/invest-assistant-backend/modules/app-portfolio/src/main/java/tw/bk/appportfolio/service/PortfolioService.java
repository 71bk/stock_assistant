package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appportfolio.mapper.PortfolioMapper;
import tw.bk.appportfolio.model.PortfolioSummary;
import tw.bk.appportfolio.model.PortfolioRefView;
import tw.bk.appportfolio.model.PortfolioPositionsRebuildResult;
import tw.bk.appportfolio.model.PortfolioChatContext;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;
import tw.bk.appportfolio.model.PortfolioView;
import tw.bk.appportfolio.model.PositionWithQuote;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.model.TradeView;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.PortfolioValuationEntity;
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
    private static final String DEFAULT_PORTFOLIO_NAME = "Main";
    private static final String DEFAULT_BASE_CURRENCY = "TWD";

    private final PortfolioRepository portfolioRepository;
    private final PortfolioValuationRepository portfolioValuationRepository;
    private final StockTradeRepository tradeRepository;
    private final UserPositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final ClockProvider clockProvider;
    private final PortfolioMapper mapper = new PortfolioMapper();
    private final PositionService positionService;
    private final PortfolioValuationService valuationService;
    private final TradeService tradeService;

    @Value("${app.portfolio.valuation.zone:Asia/Taipei}")
    private String valuationZone = "Asia/Taipei";

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
        this.positionService = new PositionService(tradeRepository, positionRepository, clockProvider);
        this.valuationService = new PortfolioValuationService(
                portfolioRepository,
                portfolioValuationRepository,
                tradeRepository,
                instrumentRepository,
                positionService,
                mapper,
                this::nowValuationDate);
        this.tradeService = new TradeService(
                tradeRepository,
                instrumentRepository,
                portfolioRepository,
                positionService,
                mapper);
    }

    public PortfolioView createPortfolio(Long userId, String name, String baseCurrency) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setUserId(userId);
        portfolio.setName(isBlank(name) ? DEFAULT_PORTFOLIO_NAME : name.trim());
        portfolio.setBaseCurrency(
                isBlank(baseCurrency) ? DEFAULT_BASE_CURRENCY : baseCurrency.trim().toUpperCase(Locale.ROOT));
        return mapper.toPortfolioView(portfolioRepository.save(portfolio));
    }

    public List<PortfolioView> listPortfolios(Long userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(mapper::toPortfolioView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioRefView> findPortfolioRefById(Long portfolioId) {
        return portfolioRepository.findById(portfolioId).map(mapper::toPortfolioRefView);
    }

    @Transactional(readOnly = true)
    public List<PortfolioRefView> listPortfolioRefsByUser(Long userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(mapper::toPortfolioRefView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PortfolioRefView> listAllPortfolioRefs() {
        return portfolioRepository.findAll().stream()
                .map(mapper::toPortfolioRefView)
                .toList();
    }

    public PortfolioView getPortfolio(Long userId, Long portfolioId) {
        return mapper.toPortfolioView(requirePortfolioEntity(userId, portfolioId));
    }

    @Transactional(readOnly = true)
    public List<PortfolioChatContext> listChatContexts(Long userId, QuoteProvider quoteProvider) {
        List<PortfolioEntity> portfolios = portfolioRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(PortfolioEntity::getId))
                .toList();
        if (portfolios.isEmpty()) {
            return List.of();
        }

        LocalDate today = nowValuationDate();
        List<PortfolioChatContext> contexts = new ArrayList<>(portfolios.size());
        for (PortfolioEntity portfolio : portfolios) {
            Long portfolioId = portfolio.getId();
            long holdingsCount = positionRepository.countByPortfolioId(portfolioId);
            Optional<PortfolioValuationEntity> latest = portfolioValuationRepository
                    .findTopByPortfolioIdOrderByAsOfDateDesc(portfolioId);

            if (latest.isPresent()) {
                PortfolioValuationEntity valuation = latest.get();
                contexts.add(new PortfolioChatContext(
                        portfolioId,
                        portfolio.getName(),
                        valuation.getBaseCurrency(),
                        holdingsCount,
                        valuation.getTotalValue(),
                        valuation.getCashValue(),
                        valuation.getPositionsValue(),
                        valuation.getAsOfDate(),
                        true));
                continue;
            }

            BigDecimal positionsValue = valuationService.calculatePositionsValue(portfolioId, today, quoteProvider);
            BigDecimal cashValue = valuationService.calculateCashValue(portfolioId, today);
            BigDecimal totalValue = positionsValue.add(cashValue).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            contexts.add(new PortfolioChatContext(
                    portfolioId,
                    portfolio.getName(),
                    normalizeBaseCurrency(portfolio.getBaseCurrency()),
                    holdingsCount,
                    totalValue,
                    cashValue,
                    positionsValue,
                    today,
                    false));
        }
        return List.copyOf(contexts);
    }

    private PortfolioEntity requirePortfolioEntity(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
    }

    @Transactional(readOnly = true)
    public List<PortfolioValuationView> listValuations(Long userId, Long portfolioId, LocalDate from, LocalDate to) {
        return valuationService.listValuations(userId, portfolioId, from, to);
    }

    @Transactional
    public PortfolioValuationSnapshotResult snapshotValuations(
            Long userId,
            Long portfolioId,
            LocalDate asOfDate,
            QuoteProvider quoteProvider) {
        return valuationService.snapshotValuations(userId, portfolioId, asOfDate, quoteProvider);
    }

    @Transactional
    public PortfolioValuationSnapshotResult snapshotValuations(LocalDate asOfDate, QuoteProvider quoteProvider) {
        return valuationService.snapshotValuations(asOfDate, quoteProvider);
    }

    private LocalDate nowValuationDate() {
        try {
            String zoneName = isBlank(valuationZone) ? "UTC" : valuationZone.trim();
            return clockProvider.now().atZone(ZoneId.of(zoneName)).toLocalDate();
        } catch (DateTimeException ex) {
            log.warn("Invalid valuation zone '{}', fallback to UTC", valuationZone);
            return clockProvider.nowUtc().toLocalDate();
        }
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

        // 平行預抓報價，後續迴圈只做 map 查表，避免逐檔序列等待累加成 timeout
        QuoteProvider resolvedQuotes = prefetchQuotes(positions, quoteProvider);

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
            if (resolvedQuotes != null) {
                InstrumentEntity instrument = instrumentRepository.findById(position.getInstrumentId()).orElse(null);
                if (instrument != null && instrument.getSymbolKey() != null) {
                    currentPrice = resolvedQuotes.getCurrentPrice(instrument.getSymbolKey())
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
        } else if (from != null) {
            trades = tradeRepository.findByUserIdAndPortfolioIdAndTradeDateGreaterThanEqual(userId, portfolioId, from,
                    pageable);
        } else if (to != null) {
            trades = tradeRepository.findByUserIdAndPortfolioIdAndTradeDateLessThanEqual(userId, portfolioId, to,
                    pageable);
        } else {
            trades = tradeRepository.findByUserIdAndPortfolioId(userId, portfolioId, pageable);
        }
        return toTradeViewPage(trades);
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
                positionService.rebuildPosition(userId, portfolioId, targetInstrumentId);
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

        // 平行預抓報價，下方 buildPositionWithQuote 的報價查詢改為即時 map 查表
        QuoteProvider resolvedQuotes = prefetchQuotes(positions, quoteProvider);

        return positions.stream()
                .map(pos -> buildPositionWithQuote(pos, resolvedQuotes))
                .toList();
    }

    /**
     * 平行預抓所有持倉的即時報價，回傳一個改由記憶體 map 即時回應的 QuoteProvider。
     *
     * <p>原本 summary/positions 會「逐檔」呼叫外部報價，N 檔就是 N 次序列等待；
     * 改成一次平行抓好放進 map，後續計算只做 map 查表，避免單檔變慢時累加成前端 timeout。
     * 報價查詢為純 HTTP（不碰 JPA），可安全地在 parallel stream 中執行。
     */
    private QuoteProvider prefetchQuotes(List<UserPositionEntity> positions, QuoteProvider delegate) {
        if (delegate == null || positions.isEmpty()) {
            return delegate;
        }
        List<Long> instrumentIds = positions.stream()
                .map(UserPositionEntity::getInstrumentId)
                .distinct()
                .toList();
        List<String> symbolKeys = new ArrayList<>();
        for (InstrumentEntity instrument : instrumentRepository.findAllById(instrumentIds)) {
            if (instrument.getSymbolKey() != null) {
                symbolKeys.add(instrument.getSymbolKey());
            }
        }
        Map<String, Optional<BigDecimal>> priceCache = new ConcurrentHashMap<>();
        symbolKeys.stream().distinct().parallel()
                .forEach(key -> priceCache.put(key, delegate.getCurrentPrice(key)));
        return symbolKey -> priceCache.getOrDefault(symbolKey, Optional.empty());
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
                symbolKey,
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
        return tradeService.createTrade(userId, portfolioId, command);
    }

    @Transactional
    public TradeView updateTrade(Long userId, Long tradeId, TradeCommand command) {
        return tradeService.updateTrade(userId, tradeId, command);
    }

    @Transactional
    public void deleteTrade(Long userId, Long tradeId) {
        tradeService.deleteTrade(userId, tradeId);
    }

    private Page<TradeView> toTradeViewPage(Page<StockTradeEntity> trades) {
        Map<Long, InstrumentEntity> instrumentById = loadInstrumentsById(trades.getContent().stream()
                .map(StockTradeEntity::getInstrumentId)
                .toList());
        return trades.map(trade -> mapper.toTradeView(trade, instrumentById.get(trade.getInstrumentId())));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
