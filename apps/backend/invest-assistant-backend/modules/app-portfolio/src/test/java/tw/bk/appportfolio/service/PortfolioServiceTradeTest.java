package tw.bk.appportfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appportfolio.model.TradeView;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.StockTradeEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.PortfolioValuationRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTradeTest {

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
        service = PortfolioServiceTestFactory.create(
                portfolioRepository,
                portfolioValuationRepository,
                stockTradeRepository,
                userPositionRepository,
                instrumentRepository,
                clockProvider);
    }

    @Test
    void listTrades_shouldBatchLoadInstrumentDisplayFields() {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(20L);
        portfolio.setUserId(10L);
        when(portfolioRepository.findByIdAndUserId(20L, 10L)).thenReturn(Optional.of(portfolio));

        Pageable pageable = PageRequest.of(0, 20);
        when(stockTradeRepository.findByUserIdAndPortfolioId(10L, 20L, pageable))
                .thenReturn(new PageImpl<>(List.of(
                        trade(31L, 1001L),
                        trade(32L, 1001L),
                        trade(33L, 1002L))));
        when(instrumentRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            assertIterableEquals(List.of(1001L, 1002L), StreamSupport.stream(ids.spliterator(), false).toList());
            return List.of(
                    instrument(1001L, "AAPL.US", "AAPL", "Apple", "Apple Inc."),
                    instrument(1002L, "MSFT.US", "MSFT", "Microsoft", "Microsoft Corp."));
        });

        Page<TradeView> result = service.listTrades(10L, 20L, null, null, pageable);

        assertEquals(3, result.getContent().size());
        assertEquals("AAPL", result.getContent().get(0).ticker());
        assertEquals("Apple", result.getContent().get(0).nameZh());
        assertEquals("MSFT.US", result.getContent().get(2).symbolKey());
        verify(instrumentRepository, times(1)).findAllById(any());
    }

    private StockTradeEntity trade(Long id, Long instrumentId) {
        StockTradeEntity trade = new StockTradeEntity();
        trade.setId(id);
        trade.setUserId(10L);
        trade.setPortfolioId(20L);
        trade.setInstrumentId(instrumentId);
        trade.setTradeDate(LocalDate.parse("2026-02-01"));
        trade.setSettlementDate(LocalDate.parse("2026-02-03"));
        trade.setSide(TradeSide.BUY.name());
        trade.setQuantity(new BigDecimal("10"));
        trade.setPrice(new BigDecimal("123.45"));
        trade.setCurrency("USD");
        trade.setGrossAmount(new BigDecimal("1234.50"));
        trade.setFee(BigDecimal.ONE);
        trade.setTax(BigDecimal.ZERO);
        trade.setNetAmount(new BigDecimal("-1235.50"));
        trade.setSource(TradeSource.MANUAL.name());
        trade.setRowHash("hash-" + id);
        return trade;
    }

    private InstrumentEntity instrument(Long id, String symbolKey, String ticker, String nameZh, String nameEn) {
        InstrumentEntity instrument = new InstrumentEntity();
        instrument.setId(id);
        instrument.setSymbolKey(symbolKey);
        instrument.setTicker(ticker);
        instrument.setNameZh(nameZh);
        instrument.setNameEn(nameEn);
        instrument.setCurrency("USD");
        instrument.setStatus("ACTIVE");
        instrument.setAssetType("STOCK");
        return instrument;
    }
}
