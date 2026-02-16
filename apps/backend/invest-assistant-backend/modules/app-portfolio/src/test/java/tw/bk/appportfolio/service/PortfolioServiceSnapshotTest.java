package tw.bk.appportfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.PortfolioValuationEntity;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.PortfolioValuationRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceSnapshotTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioValuationRepository portfolioValuationRepository;

    @Mock
    private StockTradeRepository stockTradeRepository;

    @Mock
    private UserPositionRepository userPositionRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private ClockProvider clockProvider;

    private PortfolioService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioService(
                portfolioRepository,
                portfolioValuationRepository,
                stockTradeRepository,
                userPositionRepository,
                instrumentRepository,
                clockProvider);
    }

    @Test
    void snapshotValuations_shouldPersistValuationWithQuoteAndCashValue() {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(7L);
        portfolio.setUserId(99L);
        portfolio.setBaseCurrency("TWD");

        StockTradeEntity trade = new StockTradeEntity();
        trade.setPortfolioId(7L);
        trade.setInstrumentId(1001L);
        trade.setTradeDate(LocalDate.parse("2026-02-01"));
        trade.setSide("BUY");
        trade.setQuantity(new BigDecimal("10"));
        trade.setPrice(new BigDecimal("100"));
        trade.setFee(BigDecimal.ZERO);
        trade.setTax(BigDecimal.ZERO);
        trade.setCurrency("TWD");

        InstrumentEntity instrument = new InstrumentEntity();
        instrument.setId(1001L);
        instrument.setSymbolKey("TW:XTAI:2330");

        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(stockTradeRepository.findDistinctInstrumentIdsByPortfolioIdAndTradeDateLessThanEqual(
                7L,
                LocalDate.parse("2026-02-09"))).thenReturn(List.of(1001L));
        when(stockTradeRepository.findByPortfolioIdAndInstrumentIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                7L,
                1001L,
                LocalDate.parse("2026-02-09"))).thenReturn(List.of(trade));
        when(instrumentRepository.findAllById(any())).thenReturn(List.of(instrument));
        when(stockTradeRepository.sumNetAmountByPortfolioIdAsOfDate(7L, LocalDate.parse("2026-02-09")))
                .thenReturn(new BigDecimal("-1000"));
        when(portfolioValuationRepository.findById(any())).thenReturn(Optional.empty());
        when(portfolioValuationRepository.save(any(PortfolioValuationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(clockProvider.now()).thenReturn(Instant.parse("2026-02-09T00:00:00Z"));

        PortfolioValuationSnapshotResult result = service.snapshotValuations(
                LocalDate.parse("2026-02-09"),
                symbolKey -> Optional.of(new BigDecimal("120")));

        assertEquals(LocalDate.parse("2026-02-09"), result.asOfDate());
        assertEquals(1, result.total());
        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());

        ArgumentCaptor<PortfolioValuationEntity> captor = ArgumentCaptor.forClass(PortfolioValuationEntity.class);
        verify(portfolioValuationRepository).save(captor.capture());
        PortfolioValuationEntity saved = captor.getValue();
        assertEquals(7L, saved.getPortfolioId());
        assertEquals(LocalDate.parse("2026-02-09"), saved.getAsOfDate());
        assertEquals("TWD", saved.getBaseCurrency());
        assertEquals(new BigDecimal("1200.000000"), saved.getPositionsValue());
        assertEquals(new BigDecimal("-1000.000000"), saved.getCashValue());
        assertEquals(new BigDecimal("200.000000"), saved.getTotalValue());
    }

    @Test
    void snapshotValuations_shouldUseHistoricalTradesAsOfDate() {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(8L);
        portfolio.setUserId(99L);
        portfolio.setBaseCurrency("TWD");

        StockTradeEntity buyTrade = new StockTradeEntity();
        buyTrade.setPortfolioId(8L);
        buyTrade.setInstrumentId(2002L);
        buyTrade.setTradeDate(LocalDate.parse("2026-01-01"));
        buyTrade.setSide("BUY");
        buyTrade.setQuantity(new BigDecimal("10"));
        buyTrade.setPrice(new BigDecimal("100"));
        buyTrade.setFee(BigDecimal.ZERO);
        buyTrade.setTax(BigDecimal.ZERO);
        buyTrade.setCurrency("TWD");

        InstrumentEntity instrument = new InstrumentEntity();
        instrument.setId(2002L);
        instrument.setSymbolKey("TW:XTAI:0050");

        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(stockTradeRepository.findDistinctInstrumentIdsByPortfolioIdAndTradeDateLessThanEqual(
                8L,
                LocalDate.parse("2026-01-05"))).thenReturn(List.of(2002L));
        when(stockTradeRepository.findByPortfolioIdAndInstrumentIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                8L,
                2002L,
                LocalDate.parse("2026-01-05"))).thenReturn(List.of(buyTrade));
        when(instrumentRepository.findAllById(any())).thenReturn(List.of(instrument));
        when(stockTradeRepository.sumNetAmountByPortfolioIdAsOfDate(8L, LocalDate.parse("2026-01-05")))
                .thenReturn(BigDecimal.ZERO);
        when(portfolioValuationRepository.findById(any())).thenReturn(Optional.empty());
        when(portfolioValuationRepository.save(any(PortfolioValuationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(clockProvider.now()).thenReturn(Instant.parse("2026-02-09T00:00:00Z"));

        PortfolioValuationSnapshotResult result = service.snapshotValuations(
                LocalDate.parse("2026-01-05"),
                symbolKey -> Optional.empty());

        assertEquals(1, result.succeeded());
        ArgumentCaptor<PortfolioValuationEntity> captor = ArgumentCaptor.forClass(PortfolioValuationEntity.class);
        verify(portfolioValuationRepository).save(captor.capture());
        PortfolioValuationEntity saved = captor.getValue();
        assertEquals(new BigDecimal("1000.000000"), saved.getPositionsValue());
        assertEquals(new BigDecimal("0.000000"), saved.getCashValue());
        assertEquals(new BigDecimal("1000.000000"), saved.getTotalValue());
        verifyNoInteractions(userPositionRepository);
    }

    @Test
    void snapshotValuations_shouldUseAsOfDatePriceResolver() {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(9L);
        portfolio.setUserId(99L);
        portfolio.setBaseCurrency("TWD");

        StockTradeEntity buyTrade = new StockTradeEntity();
        buyTrade.setPortfolioId(9L);
        buyTrade.setInstrumentId(3003L);
        buyTrade.setTradeDate(LocalDate.parse("2026-01-01"));
        buyTrade.setSide("BUY");
        buyTrade.setQuantity(new BigDecimal("10"));
        buyTrade.setPrice(new BigDecimal("100"));
        buyTrade.setFee(BigDecimal.ZERO);
        buyTrade.setTax(BigDecimal.ZERO);
        buyTrade.setCurrency("TWD");

        InstrumentEntity instrument = new InstrumentEntity();
        instrument.setId(3003L);
        instrument.setSymbolKey("TW:XTAI:3003");

        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(stockTradeRepository.findDistinctInstrumentIdsByPortfolioIdAndTradeDateLessThanEqual(
                9L,
                LocalDate.parse("2026-01-05"))).thenReturn(List.of(3003L));
        when(stockTradeRepository.findByPortfolioIdAndInstrumentIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                9L,
                3003L,
                LocalDate.parse("2026-01-05"))).thenReturn(List.of(buyTrade));
        when(instrumentRepository.findAllById(any())).thenReturn(List.of(instrument));
        when(stockTradeRepository.sumNetAmountByPortfolioIdAsOfDate(9L, LocalDate.parse("2026-01-05")))
                .thenReturn(BigDecimal.ZERO);
        when(portfolioValuationRepository.findById(any())).thenReturn(Optional.empty());
        when(portfolioValuationRepository.save(any(PortfolioValuationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(clockProvider.now()).thenReturn(Instant.parse("2026-02-09T00:00:00Z"));

        AtomicBoolean asOfResolverCalled = new AtomicBoolean(false);
        QuoteProvider quoteProvider = new QuoteProvider() {
            @Override
            public Optional<BigDecimal> getCurrentPrice(String symbolKey) {
                return Optional.of(new BigDecimal("999"));
            }

            @Override
            public Optional<BigDecimal> getPrice(String symbolKey, LocalDate asOfDate, LocalDate today) {
                asOfResolverCalled.set(true);
                assertEquals("TW:XTAI:3003", symbolKey);
                assertEquals(LocalDate.parse("2026-01-05"), asOfDate);
                assertEquals(LocalDate.parse("2026-02-09"), today);
                return Optional.of(new BigDecimal("80"));
            }
        };

        PortfolioValuationSnapshotResult result = service.snapshotValuations(
                LocalDate.parse("2026-01-05"),
                quoteProvider);

        assertEquals(1, result.succeeded());
        assertTrue(asOfResolverCalled.get());
        ArgumentCaptor<PortfolioValuationEntity> captor = ArgumentCaptor.forClass(PortfolioValuationEntity.class);
        verify(portfolioValuationRepository).save(captor.capture());
        PortfolioValuationEntity saved = captor.getValue();
        assertEquals(new BigDecimal("800.000000"), saved.getPositionsValue());
    }

    @Test
    void snapshotValuations_shouldContinueWhenSinglePortfolioFails() {
        PortfolioEntity bad = new PortfolioEntity();
        bad.setId(1L);
        bad.setUserId(99L);
        bad.setBaseCurrency("TWD");

        PortfolioEntity good = new PortfolioEntity();
        good.setId(2L);
        good.setUserId(99L);
        good.setBaseCurrency("TWD");

        when(portfolioRepository.findAll()).thenReturn(List.of(bad, good));
        when(stockTradeRepository.findDistinctInstrumentIdsByPortfolioIdAndTradeDateLessThanEqual(
                1L,
                LocalDate.parse("2026-02-09"))).thenThrow(new RuntimeException("boom"));
        when(stockTradeRepository.findDistinctInstrumentIdsByPortfolioIdAndTradeDateLessThanEqual(
                2L,
                LocalDate.parse("2026-02-09"))).thenReturn(List.of());
        when(portfolioValuationRepository.findById(any())).thenReturn(Optional.empty());
        when(portfolioValuationRepository.save(any(PortfolioValuationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioValuationSnapshotResult result = service.snapshotValuations(
                LocalDate.parse("2026-02-09"),
                symbolKey -> Optional.empty());

        assertEquals(2, result.total());
        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());
        assertTrue(result.failedPortfolioIds().contains(1L));
    }
}
