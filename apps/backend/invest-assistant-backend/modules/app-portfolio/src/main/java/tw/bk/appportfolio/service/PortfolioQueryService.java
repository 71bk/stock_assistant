package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appportfolio.mapper.PortfolioMapper;
import tw.bk.appportfolio.model.PortfolioChatContext;
import tw.bk.appportfolio.model.PortfolioRefView;
import tw.bk.appportfolio.model.PortfolioSummary;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.appportfolio.model.PortfolioView;
import tw.bk.appportfolio.model.PositionWithQuote;
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

/** Read-side operations and quote enrichment for portfolios. */
@Service
class PortfolioQueryService {
    private final PortfolioRepository portfolioRepository;
    private final PortfolioValuationRepository portfolioValuationRepository;
    private final StockTradeRepository tradeRepository;
    private final UserPositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PortfolioMapper mapper;
    private final PortfolioValuationService valuationService;
    private final PortfolioValuationDateProvider valuationDateProvider;

    PortfolioQueryService(
            PortfolioRepository portfolioRepository,
            PortfolioValuationRepository portfolioValuationRepository,
            StockTradeRepository tradeRepository,
            UserPositionRepository positionRepository,
            InstrumentRepository instrumentRepository,
            PortfolioMapper mapper,
            PortfolioValuationService valuationService,
            PortfolioValuationDateProvider valuationDateProvider) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioValuationRepository = portfolioValuationRepository;
        this.tradeRepository = tradeRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.mapper = mapper;
        this.valuationService = valuationService;
        this.valuationDateProvider = valuationDateProvider;
    }

    List<PortfolioView> listPortfolios(Long userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(mapper::toPortfolioView)
                .toList();
    }

    Optional<PortfolioRefView> findPortfolioRefById(Long portfolioId) {
        return portfolioRepository.findById(portfolioId).map(mapper::toPortfolioRefView);
    }

    List<PortfolioRefView> listPortfolioRefsByUser(Long userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(mapper::toPortfolioRefView)
                .toList();
    }

    List<PortfolioRefView> listAllPortfolioRefs() {
        return portfolioRepository.findAll().stream()
                .map(mapper::toPortfolioRefView)
                .toList();
    }

    PortfolioView getPortfolio(Long userId, Long portfolioId) {
        return mapper.toPortfolioView(requirePortfolioEntity(userId, portfolioId));
    }

    List<PortfolioChatContext> listChatContexts(Long userId, QuoteProvider quoteProvider) {
        List<PortfolioEntity> portfolios = portfolioRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(PortfolioEntity::getId))
                .toList();
        if (portfolios.isEmpty()) {
            return List.of();
        }

        LocalDate today = valuationDateProvider.currentDate();
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
            BigDecimal totalValue = positionsValue.add(cashValue)
                    .setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
            contexts.add(new PortfolioChatContext(
                    portfolioId,
                    portfolio.getName(),
                    PortfolioAmounts.normalizeBaseCurrency(portfolio.getBaseCurrency()),
                    holdingsCount,
                    totalValue,
                    cashValue,
                    positionsValue,
                    today,
                    false));
        }
        return List.copyOf(contexts);
    }

    List<PortfolioValuationView> listValuations(
            Long userId,
            Long portfolioId,
            LocalDate from,
            LocalDate to) {
        return valuationService.listValuations(userId, portfolioId, from, to);
    }

    PortfolioSummary getPortfolioSummary(Long userId, Long portfolioId, QuoteProvider quoteProvider) {
        requirePortfolioEntity(userId, portfolioId);
        List<UserPositionEntity> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) {
            return PortfolioSummary.empty();
        }

        Map<Long, InstrumentEntity> instrumentById = loadInstrumentsById(positions.stream()
                .map(UserPositionEntity::getInstrumentId)
                .toList());
        QuoteProvider resolvedQuotes = prefetchQuotes(instrumentById.values(), quoteProvider);

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        for (UserPositionEntity position : positions) {
            BigDecimal quantity = position.getTotalQuantity();
            BigDecimal avgCost = position.getAvgCostNative();
            BigDecimal positionCost = avgCost.multiply(quantity)
                    .setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
            totalCost = totalCost.add(positionCost);

            BigDecimal currentPrice = avgCost;
            InstrumentEntity instrument = instrumentById.get(position.getInstrumentId());
            if (resolvedQuotes != null && instrument != null && instrument.getSymbolKey() != null) {
                currentPrice = resolvedQuotes.getCurrentPrice(instrument.getSymbolKey()).orElse(avgCost);
            }
            totalMarketValue = totalMarketValue.add(currentPrice.multiply(quantity)
                    .setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP));
        }

        BigDecimal totalPnl = totalMarketValue.subtract(totalCost)
                .setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalPnlPercent = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            totalPnlPercent = totalPnl
                    .divide(totalCost, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return new PortfolioSummary(totalMarketValue, totalCost, totalPnl, totalPnlPercent);
    }

    Page<TradeView> listTrades(
            Long userId,
            Long portfolioId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        requirePortfolioEntity(userId, portfolioId);
        Page<StockTradeEntity> trades;
        if (from != null && to != null) {
            trades = tradeRepository.findByUserIdAndPortfolioIdAndTradeDateBetween(
                    userId, portfolioId, from, to, pageable);
        } else if (from != null) {
            trades = tradeRepository.findByUserIdAndPortfolioIdAndTradeDateGreaterThanEqual(
                    userId, portfolioId, from, pageable);
        } else if (to != null) {
            trades = tradeRepository.findByUserIdAndPortfolioIdAndTradeDateLessThanEqual(
                    userId, portfolioId, to, pageable);
        } else {
            trades = tradeRepository.findByUserIdAndPortfolioId(userId, portfolioId, pageable);
        }
        return toTradeViewPage(trades);
    }
    List<PositionWithQuote> listPositionsWithQuotes(
            Long userId,
            Long portfolioId,
            QuoteProvider quoteProvider) {
        requirePortfolioEntity(userId, portfolioId);
        List<UserPositionEntity> positions = positionRepository.findByPortfolioId(portfolioId);
        Map<Long, InstrumentEntity> instrumentById = loadInstrumentsById(positions.stream()
                .map(UserPositionEntity::getInstrumentId)
                .toList());
        QuoteProvider resolvedQuotes = prefetchQuotes(instrumentById.values(), quoteProvider);

        return positions.stream()
                .map(position -> buildPositionWithQuote(
                        position,
                        instrumentById.get(position.getInstrumentId()),
                        resolvedQuotes))
                .toList();
    }

    private PortfolioEntity requirePortfolioEntity(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
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

    private QuoteProvider prefetchQuotes(
            Iterable<InstrumentEntity> instruments,
            QuoteProvider delegate) {
        if (delegate == null) {
            return null;
        }

        List<String> symbolKeys = new ArrayList<>();
        for (InstrumentEntity instrument : instruments) {
            if (instrument.getSymbolKey() != null) {
                symbolKeys.add(instrument.getSymbolKey());
            }
        }
        if (symbolKeys.isEmpty()) {
            return delegate;
        }

        Map<String, Optional<BigDecimal>> priceCache = new ConcurrentHashMap<>();
        symbolKeys.stream().distinct().parallel()
                .forEach(key -> priceCache.put(key, delegate.getCurrentPrice(key)));
        return symbolKey -> priceCache.getOrDefault(symbolKey, Optional.empty());
    }

    private PositionWithQuote buildPositionWithQuote(
            UserPositionEntity position,
            InstrumentEntity instrument,
            QuoteProvider quoteProvider) {
        String ticker = instrument != null ? instrument.getTicker() : null;
        String name = instrument != null ? instrument.getNameZh() : null;
        String symbolKey = instrument != null ? instrument.getSymbolKey() : null;

        BigDecimal quantity = position.getTotalQuantity();
        BigDecimal avgCost = position.getAvgCostNative();
        BigDecimal currentPrice = null;
        if (symbolKey != null && quoteProvider != null) {
            currentPrice = quoteProvider.getCurrentPrice(symbolKey).orElse(null);
        }

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
                position.getPortfolioId(),
                position.getInstrumentId(),
                symbolKey,
                ticker,
                name,
                quantity,
                avgCost,
                position.getCurrency(),
                currentPrice,
                marketValue,
                unrealizedPnl,
                unrealizedPnlPercent);
    }

    private Page<TradeView> toTradeViewPage(Page<StockTradeEntity> trades) {
        Map<Long, InstrumentEntity> instrumentById = loadInstrumentsById(trades.getContent().stream()
                .map(StockTradeEntity::getInstrumentId)
                .toList());
        return trades.map(trade -> mapper.toTradeView(trade, instrumentById.get(trade.getInstrumentId())));
    }
}
