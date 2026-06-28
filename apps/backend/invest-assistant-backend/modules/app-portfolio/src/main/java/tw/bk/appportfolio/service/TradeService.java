package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appportfolio.mapper.PortfolioMapper;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.model.TradeView;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;

/**
 * 交易（買賣紀錄）建立、更新、刪除。
 *
 * <p>負責交易欄位正規化、淨額/row hash 計算、幣別校驗與重複偵測，寫入後透過
 * {@link PositionService} 重建受影響持倉。從 {@code PortfolioService} 抽出；
 * 交易語意（{@code @Transactional}）維持在呼叫端 {@code PortfolioService} 上。
 */
class TradeService {
    private static final String SOURCE_MANUAL = TradeSource.MANUAL.name();

    private final StockTradeRepository tradeRepository;
    private final InstrumentRepository instrumentRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionService positionService;
    private final PortfolioMapper mapper;

    TradeService(StockTradeRepository tradeRepository,
            InstrumentRepository instrumentRepository,
            PortfolioRepository portfolioRepository,
            PositionService positionService,
            PortfolioMapper mapper) {
        this.tradeRepository = tradeRepository;
        this.instrumentRepository = instrumentRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionService = positionService;
        this.mapper = mapper;
    }

    TradeView createTrade(Long userId, Long portfolioId, TradeCommand command) {
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

        positionService.rebuildPosition(userId, portfolioId, command.instrumentId());
        return mapper.toTradeView(trade, instrument);
    }

    TradeView updateTrade(Long userId, Long tradeId, TradeCommand command) {
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
            positionService.rebuildPosition(userId, portfolioId, originalInstrumentId);
        }
        positionService.rebuildPosition(userId, portfolioId, command.instrumentId());
        return mapper.toTradeView(trade, instrument);
    }

    void deleteTrade(Long userId, Long tradeId) {
        StockTradeEntity trade = tradeRepository.findByIdAndUserId(tradeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Trade not found"));

        Long portfolioId = trade.getPortfolioId();
        Long instrumentId = trade.getInstrumentId();
        lockPortfolio(userId, portfolioId);
        tradeRepository.delete(trade);
        tradeRepository.flush();
        positionService.rebuildPosition(userId, portfolioId, instrumentId);
    }

    private void lockPortfolio(Long userId, Long portfolioId) {
        portfolioRepository.findByIdAndUserIdForUpdate(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
    }

    private void applyTradeFields(StockTradeEntity trade, TradeCommand command) {
        BigDecimal quantity = PortfolioAmounts.normalizeQuantity(command.quantity());
        BigDecimal price = PortfolioAmounts.normalizePrice(command.price());
        BigDecimal fee = PortfolioAmounts.normalizeAmount(command.fee());
        BigDecimal tax = PortfolioAmounts.normalizeAmount(command.tax());

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
        trade.setSourceRefId(command.sourceRefId());

        BigDecimal gross = price.multiply(quantity).setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal fees = fee.add(tax).setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal net;
        if (command.side() == TradeSide.BUY) {
            net = gross.add(fees).negate();
        } else {
            net = gross.subtract(fees);
        }
        trade.setGrossAmount(gross);
        trade.setNetAmount(net.setScale(PortfolioAmounts.AMOUNT_SCALE, RoundingMode.HALF_UP));
        trade.setRowHash(buildRowHash(trade));
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

    private String buildRowHash(StockTradeEntity trade) {
        String payload = trade.getUserId() + "|"
                + trade.getPortfolioId() + "|"
                + trade.getInstrumentId() + "|"
                + trade.getTradeDate() + "|"
                + trade.getSide() + "|"
                + trade.getQuantity().toPlainString() + "|"
                + trade.getPrice().toPlainString() + "|"
                + trade.getCurrency() + "|"
                + PortfolioAmounts.normalizeAmount(trade.getFee()).toPlainString() + "|"
                + PortfolioAmounts.normalizeAmount(trade.getTax()).toPlainString() + "|"
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
}
