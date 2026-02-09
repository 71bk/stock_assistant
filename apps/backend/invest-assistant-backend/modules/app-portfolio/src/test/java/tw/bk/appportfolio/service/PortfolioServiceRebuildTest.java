package tw.bk.appportfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.PortfolioValuationRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;
import tw.bk.appportfolio.model.PortfolioPositionsRebuildResult;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceRebuildTest {

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
    void rebuildPositions_shouldMergeTradeAndPositionTargets() {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(20L);
        portfolio.setUserId(10L);
        when(portfolioRepository.findById(20L)).thenReturn(Optional.of(portfolio));
        when(stockTradeRepository.findDistinctInstrumentIdsByUserIdAndPortfolioId(10L, 20L))
                .thenReturn(List.of(1L, 2L));
        when(userPositionRepository.findDistinctInstrumentIdsByPortfolioId(20L))
                .thenReturn(List.of(2L, 3L));
        when(stockTradeRepository.findByUserIdAndPortfolioIdAndInstrumentIdOrderByTradeDateAscIdAsc(
                anyLong(),
                anyLong(),
                anyLong())).thenReturn(List.of());

        PortfolioPositionsRebuildResult result = service.rebuildPositions(20L, null);

        assertEquals(20L, result.portfolioId());
        assertEquals(10L, result.userId());
        assertEquals(3, result.targetInstrumentCount());
        assertEquals(3, result.rebuiltInstrumentCount());
        assertEquals(0, result.failedInstrumentCount());
        assertIterableEquals(List.of(), result.failedInstrumentIds());

        verify(userPositionRepository, times(1)).deleteByPortfolioIdAndInstrumentId(20L, 1L);
        verify(userPositionRepository, times(1)).deleteByPortfolioIdAndInstrumentId(20L, 2L);
        verify(userPositionRepository, times(1)).deleteByPortfolioIdAndInstrumentId(20L, 3L);
    }

    @Test
    void rebuildPositions_shouldCollectFailures() {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(20L);
        portfolio.setUserId(10L);
        when(portfolioRepository.findById(20L)).thenReturn(Optional.of(portfolio));
        when(stockTradeRepository.findByUserIdAndPortfolioIdAndInstrumentIdOrderByTradeDateAscIdAsc(10L, 20L, 7L))
                .thenThrow(new IllegalStateException("boom"));

        PortfolioPositionsRebuildResult result = service.rebuildPositions(20L, 7L);

        assertEquals(1, result.targetInstrumentCount());
        assertEquals(0, result.rebuiltInstrumentCount());
        assertEquals(1, result.failedInstrumentCount());
        assertIterableEquals(List.of(7L), result.failedInstrumentIds());
    }
}
