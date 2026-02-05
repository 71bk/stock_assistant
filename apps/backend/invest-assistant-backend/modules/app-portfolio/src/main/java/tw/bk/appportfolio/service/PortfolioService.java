package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.model.TradeSide;
import tw.bk.appportfolio.model.PortfolioSummary;
import tw.bk.appportfolio.model.PositionWithQuote;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.apppersistence.entity.UserPositionEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

@Service
public class PortfolioService {
    private static final int AMOUNT_SCALE = 6;
    private static final int PRICE_SCALE = 8;
    private static final int AVG_COST_SCALE = 8;
    private static final String DEFAULT_PORTFOLIO_NAME = "Main";
    private static final String DEFAULT_BASE_CURRENCY = "TWD";
    private static final String SOURCE_MANUAL = "MANUAL";

    private final PortfolioRepository portfolioRepository;
    private final StockTradeRepository tradeRepository;
    private final UserPositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final ClockProvider clockProvider;

    public PortfolioService(PortfolioRepository portfolioRepository,
            StockTradeRepository tradeRepository,
            UserPositionRepository positionRepository,
            InstrumentRepository instrumentRepository,
            ClockProvider clockProvider) {
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.clockProvider = clockProvider;
    }

    public PortfolioEntity createPortfolio(Long userId, String name, String baseCurrency) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setUserId(userId);
        portfolio.setName(isBlank(name) ? DEFAULT_PORTFOLIO_NAME : name.trim());
        portfolio.setBaseCurrency(
                isBlank(baseCurrency) ? DEFAULT_BASE_CURRENCY : baseCurrency.trim().toUpperCase(Locale.ROOT));
        return portfolioRepository.save(portfolio);
    }

    public List<PortfolioEntity> listPortfolios(Long userId) {
        return portfolioRepository.findByUserId(userId);
    }

    public PortfolioEntity getPortfolio(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
    }

    private PortfolioEntity lockPortfolio(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserIdForUpdate(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
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
        getPortfolio(userId, portfolioId);
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

    public Page<StockTradeEntity> listTrades(Long userId,
            Long portfolioId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        getPortfolio(userId, portfolioId);
        if (from != null && to != null) {
            return tradeRepository.findByUserIdAndPortfolioIdAndTradeDateBetween(userId, portfolioId, from, to,
                    pageable);
        }
        if (from != null) {
            return tradeRepository.findByUserIdAndPortfolioIdAndTradeDateGreaterThanEqual(userId, portfolioId, from,
                    pageable);
        }
        if (to != null) {
            return tradeRepository.findByUserIdAndPortfolioIdAndTradeDateLessThanEqual(userId, portfolioId, to,
                    pageable);
        }
        return tradeRepository.findByUserIdAndPortfolioId(userId, portfolioId, pageable);
    }

    public List<UserPositionEntity> listPositions(Long userId, Long portfolioId) {
        getPortfolio(userId, portfolioId);
        return positionRepository.findByPortfolioId(portfolioId);
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
        getPortfolio(userId, portfolioId);
        List<UserPositionEntity> positions = positionRepository.findByPortfolioId(portfolioId);

        return positions.stream()
                .map(pos -> buildPositionWithQuote(pos, quoteProvider))
                .toList();
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
    public StockTradeEntity createTrade(Long userId, Long portfolioId, TradeCommand command) {
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
        return trade;
    }

    @Transactional
    public StockTradeEntity updateTrade(Long userId, Long tradeId, TradeCommand command) {
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
        return trade;
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
                        .add(fee).add(tax);
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

        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            positionRepository.deleteByPortfolioIdAndInstrumentId(portfolioId, instrumentId);
            return;
        }

        UserPositionEntity position = positionRepository
                .findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)
                .orElseGet(UserPositionEntity::new);
        position.setPortfolioId(portfolioId);
        position.setInstrumentId(instrumentId);
        position.setTotalQuantity(totalQuantity);
        position.setAvgCostNative(avgCost.setScale(AVG_COST_SCALE, RoundingMode.HALF_UP));
        position.setCurrency(currency == null ? DEFAULT_BASE_CURRENCY : currency.toUpperCase(Locale.ROOT));
        position.setUpdatedAt(OffsetDateTime.ofInstant(clockProvider.now(), java.time.ZoneOffset.UTC));
        positionRepository.save(position);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
