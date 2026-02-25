package tw.bk.appbootstrap.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.AiReportView;
import tw.bk.appai.service.AiReportService;
import tw.bk.appapi.ai.AiController;
import tw.bk.appapi.ai.vo.AiReportSummaryResponse;
import tw.bk.appapi.auth.AuthController;
import tw.bk.appapi.files.FilesController;
import tw.bk.appapi.files.vo.FileUrlResponse;
import tw.bk.appapi.ocr.OcrController;
import tw.bk.appapi.ocr.vo.OcrJobResponse;
import tw.bk.appapi.portfolio.PortfolioController;
import tw.bk.appapi.portfolio.vo.PortfolioValuationResponse;
import tw.bk.appapi.rag.RagController;
import tw.bk.appapi.rag.dto.RagQueryRequest;
import tw.bk.appapi.rag.vo.RagQueryResponse;
import tw.bk.appapi.security.SimpleRateLimiter;
import tw.bk.appapi.stocks.StockController;
import tw.bk.appapi.stocks.vo.MarketResponse;
import tw.bk.appauth.config.AuthProperties;
import tw.bk.appauth.service.AdminAuthService;
import tw.bk.appauth.service.AuthCookieService;
import tw.bk.appauth.service.AuthService;
import tw.bk.appauth.service.UserService;
import tw.bk.appauth.service.UserSettingsService;
import tw.bk.appcommon.enums.AiReportType;
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appfiles.model.FileView;
import tw.bk.appfiles.service.FileService;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.appocr.service.OcrService;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apprag.client.AiWorkerChunk;
import tw.bk.apprag.client.AiWorkerQueryResponse;
import tw.bk.apprag.client.AiWorkerRagClient;
import tw.bk.apprag.service.RagDocumentService;
import tw.bk.appstocks.service.InstrumentService;
import tw.bk.appstocks.service.StockQuoteService;
import tw.bk.appstocks.service.StockTickerService;

class BackendApiSmokeBaselineTest {

    @Test
    void auth_googleLogin_shouldRedirectToOauthEntry() {
        AuthController controller = new AuthController(
                mock(AuthService.class),
                mock(AuthCookieService.class),
                mock(AdminAuthService.class),
                mock(UserService.class),
                mock(UserSettingsService.class),
                mock(CurrentUserProvider.class),
                new AuthProperties(),
                mock(SimpleRateLimiter.class));

        ResponseEntity<Void> response = controller.googleLogin();

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertNotNull(response.getHeaders().getLocation());
        assertEquals("/api/oauth2/authorization/google", response.getHeaders().getLocation().toString());
    }

    @Test
    void files_getFileUrl_shouldReturnLocalContentPathForLocalProvider() {
        FileService fileService = mock(FileService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        FilesController controller = new FilesController(fileService, currentUserProvider);

        FileView file = new FileView(
                11L,
                "local",
                null,
                "demo.pdf",
                "sha",
                128L,
                "application/pdf",
                OffsetDateTime.parse("2026-02-16T00:00:00Z"));
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(fileService.getFileView(7L, 11L)).thenReturn(file);
        when(fileService.resolveProvider(file)).thenReturn(FileProvider.LOCAL);

        Result<FileUrlResponse> result = controller.getFileUrl("11");

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("/api/files/11/content", result.getData().getUrl());
    }

    @Test
    void ocr_retry_shouldReturnJobPayload() {
        OcrService ocrService = mock(OcrService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        OcrController controller = new OcrController(ocrService, currentUserProvider, new ObjectMapper());

        OcrJobView jobView = new OcrJobView(21L, 31L, OcrJobStatus.QUEUED, 0, null);
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(5L));
        when(ocrService.retryJob(5L, 21L, false)).thenReturn(jobView);

        Result<OcrJobResponse> result = controller.retry("21", null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("21", result.getData().getJobId());
        verify(ocrService).retryJob(5L, 21L, false);
    }

    @Test
    void rag_query_shouldReturnChunkList() {
        AiWorkerRagClient ragClient = mock(AiWorkerRagClient.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        RagController controller = new RagController(
                ragClient,
                mock(FileService.class),
                currentUserProvider,
                mock(RagDocumentService.class));

        AiWorkerChunk chunk = new AiWorkerChunk();
        chunk.setContent("TSMC monthly revenue improved.");
        chunk.setTitle("Monthly note");
        chunk.setSourceType("note");
        chunk.setSourceId("doc-1");
        chunk.setChunkIndex(0);

        AiWorkerQueryResponse queryResponse = new AiWorkerQueryResponse();
        queryResponse.setChunks(List.of(chunk));

        when(currentUserProvider.getUserId()).thenReturn(Optional.of(9L));
        when(ragClient.query(9L, "TSMC", 3, "note")).thenReturn(queryResponse);

        Result<RagQueryResponse> result = controller.query(new RagQueryRequest("TSMC", 3, "note"));

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getChunks().size());
        assertEquals("Monthly note", result.getData().getChunks().get(0).getTitle());
    }

    @Test
    void ai_listReports_shouldReturnSummaryPage() {
        AiReportService aiReportService = mock(AiReportService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        AiController controller = new AiController(
                aiReportService,
                mock(GroqChatClient.class),
                currentUserProvider,
                new ObjectMapper());

        AiReportView report = new AiReportView(
                41L,
                AiReportType.INSTRUMENT,
                null,
                88L,
                "{\"score\":80}",
                "summary",
                OffsetDateTime.parse("2026-02-16T01:00:00Z"));
        Page<AiReportView> page = new PageImpl<>(List.of(report));
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(aiReportService.listReports(eq(7L), any())).thenReturn(page);

        Result<PageResponse<AiReportSummaryResponse>> result = controller.listReports(1, 20);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getItems().size());
        assertEquals("41", result.getData().getItems().get(0).getReportId());
    }

    @Test
    void stocks_getMarkets_shouldReturnTwAndUs() {
        StockController controller = new StockController(
                mock(InstrumentService.class),
                mock(StockQuoteService.class),
                mock(StockTickerService.class));

        Result<List<MarketResponse>> result = controller.getMarkets();

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
    }

    @Test
    void portfolio_listValuations_shouldReturnMappedValues() {
        PortfolioService portfolioService = mock(PortfolioService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        PortfolioController controller = new PortfolioController(
                portfolioService,
                currentUserProvider,
                mock(StockQuoteService.class));

        PortfolioValuationView valuation = new PortfolioValuationView(
                LocalDate.parse("2026-02-14"),
                new BigDecimal("123456.78"),
                new BigDecimal("10000"),
                new BigDecimal("113456.78"),
                "TWD");
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(3L));
        when(portfolioService.listValuations(
                3L,
                77L,
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-14"))).thenReturn(List.of(valuation));

        Result<List<PortfolioValuationResponse>> result = controller.listValuations(
                "77",
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-14"));

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().size());
        assertEquals("TWD", result.getData().get(0).getCurrency());
    }
}
