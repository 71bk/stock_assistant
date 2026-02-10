package tw.bk.appapi.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appapi.admin.dto.ValuationSnapshotRequest;
import tw.bk.appapi.admin.vo.ValuationSnapshotResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appportfolio.model.PortfolioValuationSnapshotResult;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.appportfolio.service.QuoteProvider;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.service.StockQuoteService;

@ExtendWith(MockitoExtension.class)
class AdminPortfolioControllerTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StockQuoteService stockQuoteService;

    private AdminProperties adminProperties;
    private AdminPortfolioController controller;

    @BeforeEach
    void setUp() {
        adminProperties = new AdminProperties();
        adminProperties.setApiKey("");
        controller = new AdminPortfolioController(
                portfolioService,
                adminProperties,
                currentUserProvider,
                stockQuoteService);
    }

    @Test
    void snapshotValuations_shouldMapServiceResult() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(1L));
        when(portfolioService.snapshotValuations(any(), any(), any(), any()))
                .thenReturn(new PortfolioValuationSnapshotResult(
                        LocalDate.parse("2026-02-09"),
                        2,
                        1,
                        1,
                        List.of(7L)));

        ValuationSnapshotRequest request = new ValuationSnapshotRequest();
        request.setUserId(99L);
        request.setPortfolioId(7L);
        request.setAsOfDate(LocalDate.parse("2026-02-09"));

        Result<ValuationSnapshotResponse> result = controller.snapshotValuations(null, request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(LocalDate.parse("2026-02-09"), result.getData().getAsOfDate());
        assertEquals(2, result.getData().getTotal());
        assertEquals(1, result.getData().getFailed());
        assertEquals(List.of(7L), result.getData().getFailedPortfolioIds());
        verify(portfolioService).snapshotValuations(
                eq(request.getUserId()),
                eq(request.getPortfolioId()),
                eq(request.getAsOfDate()),
                any());
    }

    @Test
    void snapshotValuations_quoteProviderShouldUseHistoricalCloseForPastDate() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(1L));
        when(portfolioService.snapshotValuations(any(), any(), any(), any()))
                .thenReturn(new PortfolioValuationSnapshotResult(
                        LocalDate.parse("2026-02-09"),
                        1,
                        1,
                        0,
                        List.of()));
        when(stockQuoteService.getQuote("TW:XTAI:2330"))
                .thenReturn(Quote.builder().price(new BigDecimal("540")).build());
        when(stockQuoteService.getCandles(
                "TW:XTAI:2330",
                "1d",
                LocalDate.parse("2026-01-18"),
                LocalDate.parse("2026-02-01"))).thenReturn(List.of(
                        Candle.builder()
                                .timestamp(LocalDateTime.parse("2026-01-31T00:00:00"))
                                .close(new BigDecimal("530"))
                                .build()));

        ValuationSnapshotRequest request = new ValuationSnapshotRequest();
        request.setAsOfDate(LocalDate.parse("2026-02-09"));
        controller.snapshotValuations(null, request);

        ArgumentCaptor<QuoteProvider> captor = ArgumentCaptor.forClass(QuoteProvider.class);
        verify(portfolioService).snapshotValuations(any(), any(), any(), captor.capture());
        QuoteProvider provider = captor.getValue();

        Optional<BigDecimal> historical = provider.getPrice(
                "TW:XTAI:2330",
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-09"));
        Optional<BigDecimal> current = provider.getPrice(
                "TW:XTAI:2330",
                LocalDate.parse("2026-02-09"),
                LocalDate.parse("2026-02-09"));

        assertEquals(new BigDecimal("530"), historical.orElse(null));
        assertEquals(new BigDecimal("540"), current.orElse(null));
    }
}
