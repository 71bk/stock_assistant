package tw.bk.appportfolio.service;

import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appportfolio.mapper.PortfolioMapper;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.PortfolioValuationRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

final class PortfolioServiceTestFactory {
    private PortfolioServiceTestFactory() {
    }

    static PortfolioService create(
            PortfolioRepository portfolioRepository,
            PortfolioValuationRepository portfolioValuationRepository,
            StockTradeRepository tradeRepository,
            UserPositionRepository positionRepository,
            InstrumentRepository instrumentRepository,
            ClockProvider clockProvider) {
        PortfolioMapper mapper = new PortfolioMapper();
        PortfolioValuationDateProvider valuationDateProvider =
                new PortfolioValuationDateProvider(clockProvider);
        PositionService positionService = new PositionService(
                tradeRepository,
                positionRepository,
                portfolioRepository,
                clockProvider);
        PortfolioValuationService valuationService = new PortfolioValuationService(
                portfolioRepository,
                portfolioValuationRepository,
                tradeRepository,
                instrumentRepository,
                positionService,
                mapper,
                valuationDateProvider);
        PortfolioQueryService queryService = new PortfolioQueryService(
                portfolioRepository,
                portfolioValuationRepository,
                tradeRepository,
                positionRepository,
                instrumentRepository,
                mapper,
                valuationService,
                valuationDateProvider);
        TradeService tradeService = new TradeService(
                tradeRepository,
                instrumentRepository,
                portfolioRepository,
                positionService,
                mapper);
        return new PortfolioService(
                portfolioRepository,
                mapper,
                queryService,
                positionService,
                valuationService,
                tradeService);
    }
}
