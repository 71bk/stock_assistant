package tw.bk.appportfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.PortfolioValuationEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.PortfolioValuationRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceValuationTest {

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
    void listValuations_shouldUseDefaultRangeWhenFromToAreNull() {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(20L);
        portfolio.setUserId(10L);

        when(portfolioRepository.findByIdAndUserId(20L, 10L)).thenReturn(Optional.of(portfolio));
        when(clockProvider.now()).thenReturn(Instant.parse("2026-02-09T00:00:00Z"));
        when(portfolioValuationRepository.findByPortfolioIdAndAsOfDateBetweenOrderByAsOfDateAsc(
                20L,
                LocalDate.parse("2026-01-10"),
                LocalDate.parse("2026-02-09"))).thenReturn(List.of(new PortfolioValuationEntity()));

        service.listValuations(10L, 20L, null, null);

        verify(portfolioValuationRepository).findByPortfolioIdAndAsOfDateBetweenOrderByAsOfDateAsc(
                20L,
                LocalDate.parse("2026-01-10"),
                LocalDate.parse("2026-02-09"));
    }

    @Test
    void listValuations_shouldThrowValidationWhenFromAfterTo() {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId(20L);
        portfolio.setUserId(10L);
        when(portfolioRepository.findByIdAndUserId(20L, 10L)).thenReturn(Optional.of(portfolio));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.listValuations(
                        10L,
                        20L,
                        LocalDate.parse("2026-02-10"),
                        LocalDate.parse("2026-02-09")));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }
}
