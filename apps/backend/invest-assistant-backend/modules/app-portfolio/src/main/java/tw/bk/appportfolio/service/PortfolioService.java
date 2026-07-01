package tw.bk.appportfolio.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appportfolio.mapper.PortfolioMapper;
import tw.bk.appportfolio.model.PortfolioChatContext;
import tw.bk.appportfolio.model.PortfolioPositionsRebuildResult;
import tw.bk.appportfolio.model.PortfolioRefView;
import tw.bk.appportfolio.model.PortfolioSummary;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.appportfolio.model.PortfolioView;
import tw.bk.appportfolio.model.PositionWithQuote;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.model.TradeView;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.repository.PortfolioRepository;

/**
 * Public portfolio facade. Query composition and domain-specific commands are
 * delegated to focused package services while this class preserves the module API.
 */
@Service
public class PortfolioService {
    private static final String DEFAULT_PORTFOLIO_NAME = "Main";

    private final PortfolioRepository portfolioRepository;
    private final PortfolioMapper mapper;
    private final PortfolioQueryService queryService;
    private final PositionService positionService;
    private final PortfolioValuationService valuationService;
    private final TradeService tradeService;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            PortfolioMapper mapper,
            PortfolioQueryService queryService,
            PositionService positionService,
            PortfolioValuationService valuationService,
            TradeService tradeService) {
        this.portfolioRepository = portfolioRepository;
        this.mapper = mapper;
        this.queryService = queryService;
        this.positionService = positionService;
        this.valuationService = valuationService;
        this.tradeService = tradeService;
    }

    public PortfolioView createPortfolio(Long userId, String name, String baseCurrency) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setUserId(userId);
        portfolio.setName(PortfolioAmounts.isBlank(name) ? DEFAULT_PORTFOLIO_NAME : name.trim());
        portfolio.setBaseCurrency(PortfolioAmounts.normalizeBaseCurrency(baseCurrency));
        return mapper.toPortfolioView(portfolioRepository.save(portfolio));
    }

    public List<PortfolioView> listPortfolios(Long userId) {
        return queryService.listPortfolios(userId);
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioRefView> findPortfolioRefById(Long portfolioId) {
        return queryService.findPortfolioRefById(portfolioId);
    }

    @Transactional(readOnly = true)
    public List<PortfolioRefView> listPortfolioRefsByUser(Long userId) {
        return queryService.listPortfolioRefsByUser(userId);
    }

    @Transactional(readOnly = true)
    public List<PortfolioRefView> listAllPortfolioRefs() {
        return queryService.listAllPortfolioRefs();
    }

    public PortfolioView getPortfolio(Long userId, Long portfolioId) {
        return queryService.getPortfolio(userId, portfolioId);
    }

    @Transactional(readOnly = true)
    public List<PortfolioChatContext> listChatContexts(Long userId, QuoteProvider quoteProvider) {
        return queryService.listChatContexts(userId, quoteProvider);
    }

    @Transactional(readOnly = true)
    public List<PortfolioValuationView> listValuations(
            Long userId,
            Long portfolioId,
            LocalDate from,
            LocalDate to) {
        return queryService.listValuations(userId, portfolioId, from, to);
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

    public PortfolioSummary getPortfolioSummary(Long userId, Long portfolioId, QuoteProvider quoteProvider) {
        return queryService.getPortfolioSummary(userId, portfolioId, quoteProvider);
    }

    public Page<TradeView> listTrades(
            Long userId,
            Long portfolioId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        return queryService.listTrades(userId, portfolioId, from, to, pageable);
    }
    @Transactional
    public PortfolioPositionsRebuildResult rebuildPositions(Long portfolioId, Long instrumentId) {
        return positionService.rebuildPositions(portfolioId, instrumentId);
    }

    public List<PositionWithQuote> listPositionsWithQuotes(
            Long userId,
            Long portfolioId,
            QuoteProvider quoteProvider) {
        return queryService.listPositionsWithQuotes(userId, portfolioId, quoteProvider);
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

}
