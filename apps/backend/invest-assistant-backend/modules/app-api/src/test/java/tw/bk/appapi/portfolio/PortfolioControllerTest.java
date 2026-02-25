package tw.bk.appapi.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appapi.portfolio.vo.PortfolioValuationResponse;
import tw.bk.appapi.portfolio.vo.TradeResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.appportfolio.model.TradeView;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.appstocks.service.StockQuoteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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

    @Test
    void listTrades_shouldReturnSanitizedPageInfo() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(5L));
        when(portfolioService.listTrades(eq(5L), eq(11L), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tradeView(31L))));

        Result<PageResponse<TradeResponse>> result = controller.listTrades("11", null, null, -3, 999, "tradeDate,desc");

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getPage());
        assertEquals(100, result.getData().getSize());
        assertEquals(1, result.getData().getItems().size());
        assertEquals("31", result.getData().getItems().get(0).getTradeId());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(portfolioService).listTrades(eq(5L), eq(11L), eq(null), eq(null), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("tradeDate").getDirection());
    }

    @Test
    void listTrades_shouldClampMinimumSizeToOne() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(8L));
        when(portfolioService.listTrades(eq(8L), eq(12L), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(Page.empty());

        Result<PageResponse<TradeResponse>> result = controller.listTrades("12", null, null, 0, 0, null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getPage());
        assertEquals(1, result.getData().getSize());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(portfolioService).listTrades(eq(8L), eq(12L), eq(null), eq(null), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(1, pageable.getPageSize());
    }

    @Test
    void listTrades_shouldRejectBlankSortField() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(8L));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> controller.listTrades("12", null, null, 1, 20, ",desc"));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(portfolioService, never()).listTrades(any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void listTrades_shouldRejectUnsupportedSortField() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(8L));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> controller.listTrades("12", null, null, 1, 20, "dropTable,desc"));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(portfolioService, never()).listTrades(any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void listTrades_shouldRejectUnsupportedSortDirection() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(8L));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> controller.listTrades("12", null, null, 1, 20, "tradeDate,down"));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(portfolioService, never()).listTrades(any(), any(), any(), any(), any(Pageable.class));
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

    private TradeView tradeView(Long tradeId) {
        return new TradeView(
                tradeId,
                1001L,
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-03"),
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("123.45"),
                "USD",
                new BigDecimal("1234.5"),
                new BigDecimal("1.0"),
                new BigDecimal("0.0"),
                new BigDecimal("1235.5"),
                TradeSource.MANUAL,
                null);
    }
}
