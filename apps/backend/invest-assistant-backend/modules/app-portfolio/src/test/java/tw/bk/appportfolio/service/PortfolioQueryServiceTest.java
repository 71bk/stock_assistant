package tw.bk.appportfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tw.bk.appportfolio.mapper.PortfolioMapper;
import tw.bk.appportfolio.model.PortfolioSummary;
import tw.bk.appportfolio.model.TradeView;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.apppersistence.entity.UserPositionEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.PortfolioValuationRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioQueryServiceTest {
    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioValuationRepository portfolioValuationRepository;

    @Mock
    private StockTradeRepository tradeRepository;

    @Mock
    private UserPositionRepository positionRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private PortfolioValuationService valuationService;

    @Mock
    private PortfolioValuationDateProvider valuationDateProvider;

    private PortfolioQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new PortfolioQueryService(
                portfolioRepository,
                portfolioValuationRepository,
                tradeRepository,
                positionRepository,
                instrumentRepository,
                new PortfolioMapper(),
                valuationService,
                valuationDateProvider);
    }

    @Test
    void getPortfolioSummary_shouldUseBulkLoadedInstrumentAndQuote() {
        PortfolioEntity portfolio = portfolio(20L, 10L);
        UserPositionEntity position = new UserPositionEntity();
        position.setPortfolioId(20L);
        position.setInstrumentId(30L);
        position.setTotalQuantity(new BigDecimal("2"));
        position.setAvgCostNative(new BigDecimal("100"));
        position.setCurrency("TWD");
        InstrumentEntity instrument = instrument(30L, "TW:TWSE:2330");

        when(portfolioRepository.findByIdAndUserId(20L, 10L)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findByPortfolioId(20L)).thenReturn(List.of(position));
        when(instrumentRepository.findAllById(any())).thenReturn(List.of(instrument));

        PortfolioSummary summary = queryService.getPortfolioSummary(
                10L,
                20L,
                symbolKey -> Optional.of(new BigDecimal("120")));

        assertDecimalEquals("240.000000", summary.totalMarketValue());
        assertDecimalEquals("200.000000", summary.totalCost());
        assertDecimalEquals("40.000000", summary.totalPnl());
        assertDecimalEquals("20.00", summary.totalPnlPercent());
        verify(instrumentRepository, never()).findById(anyLong());
    }

    @Test
    void listTrades_shouldMapInstrumentFromSingleBulkLoad() {
        PortfolioEntity portfolio = portfolio(20L, 10L);
        StockTradeEntity trade = new StockTradeEntity();
        trade.setId(40L);
        trade.setUserId(10L);
        trade.setPortfolioId(20L);
        trade.setInstrumentId(30L);
        InstrumentEntity instrument = instrument(30L, "TW:TWSE:2330");
        Pageable pageable = PageRequest.of(0, 20);

        when(portfolioRepository.findByIdAndUserId(20L, 10L)).thenReturn(Optional.of(portfolio));
        when(tradeRepository.findByUserIdAndPortfolioId(10L, 20L, pageable))
                .thenReturn(new PageImpl<>(List.of(trade), pageable, 1));
        when(instrumentRepository.findAllById(any())).thenReturn(List.of(instrument));

        Page<TradeView> result = queryService.listTrades(10L, 20L, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("TW:TWSE:2330", result.getContent().getFirst().symbolKey());
        assertEquals("2330", result.getContent().getFirst().ticker());
        verify(instrumentRepository, never()).findById(anyLong());
    }

    private PortfolioEntity portfolio(Long id, Long userId) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(id);
        portfolio.setUserId(userId);
        return portfolio;
    }

    private InstrumentEntity instrument(Long id, String symbolKey) {
        InstrumentEntity instrument = new InstrumentEntity();
        instrument.setId(id);
        instrument.setSymbolKey(symbolKey);
        instrument.setTicker("2330");
        instrument.setNameZh("TSMC");
        return instrument;
    }

    private void assertDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
