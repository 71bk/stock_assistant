package tw.bk.appapi.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appapi.portfolio.vo.PortfolioValuationResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.appstocks.service.StockQuoteService;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StockQuoteService stockQuoteService;

    private PortfolioController controller;

    @BeforeEach
    void setUp() {
        controller = new PortfolioController(portfolioService, currentUserProvider, stockQuoteService);
    }

    @Test
    void listValuations_shouldMapServiceResult() {
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = LocalDate.parse("2026-01-02");
        PortfolioValuationView day1 = valuation(
                7L,
                LocalDate.parse("2026-01-01"),
                "TWD",
                "100000",
                "50000",
                "50000");
        PortfolioValuationView day2 = valuation(
                7L,
                LocalDate.parse("2026-01-02"),
                "TWD",
                "102000",
                "50000",
                "52000");

        when(currentUserProvider.getUserId()).thenReturn(Optional.of(99L));
        when(portfolioService.listValuations(99L, 7L, from, to)).thenReturn(List.of(day1, day2));

        Result<List<PortfolioValuationResponse>> result = controller.listValuations("7", from, to);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
        assertEquals(LocalDate.parse("2026-01-01"), result.getData().get(0).getDate());
        assertEquals(new BigDecimal("102000"), result.getData().get(1).getTotalValue());
        assertEquals("TWD", result.getData().get(1).getCurrency());
        verify(portfolioService).listValuations(99L, 7L, from, to);
    }

    @Test
    void listValuations_shouldPassNullRangeToService() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(5L));
        when(portfolioService.listValuations(5L, 11L, null, null)).thenReturn(List.of());

        Result<List<PortfolioValuationResponse>> result = controller.listValuations("11", null, null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(0, result.getData().size());
        verify(portfolioService).listValuations(5L, 11L, null, null);
    }

    private PortfolioValuationView valuation(
            Long portfolioId,
            LocalDate date,
            String currency,
            String totalValue,
            String cashValue,
            String positionsValue) {
        return new PortfolioValuationView(
                date,
                new BigDecimal(totalValue),
                new BigDecimal(cashValue),
                new BigDecimal(positionsValue),
                currency);
    }
}
