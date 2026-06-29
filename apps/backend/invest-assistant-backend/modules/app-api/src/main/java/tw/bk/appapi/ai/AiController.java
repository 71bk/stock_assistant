package tw.bk.appapi.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.AiReportView;
import tw.bk.appai.service.AiReportService;
import tw.bk.appapi.ai.dto.AiAnalysisRequest;
import tw.bk.appapi.ai.vo.AiReportResponse;
import tw.bk.appapi.ai.vo.AiReportSummaryResponse;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appapi.web.CurrentUser;
import tw.bk.appapi.web.IdParser;
import tw.bk.appapi.web.PageableFactory;

/**
 * AI 分析 API 控制器。
 * 提供 SSE 串流分析、報告列表、報告詳情等端點。
 */
@RestController
@RequestMapping("/ai")
@Tag(name = "AI", description = "AI analysis APIs")
@Slf4j
public class AiController {
    private static final int MAX_PAGE_SIZE = 100;

    private final AiReportService aiReportService;
    private final CurrentUserProvider currentUserProvider;
    private final AiAnalysisStreamUseCase analysisStreamUseCase;

    public AiController(AiReportService aiReportService,
            GroqChatClient groqChatClient,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper) {
        this.aiReportService = aiReportService;
        this.currentUserProvider = currentUserProvider;
        this.analysisStreamUseCase = new AiAnalysisStreamUseCase(
                aiReportService,
                groqChatClient,
                new AnalysisPromptProvider(objectMapper));
    }

    // ============================================================
    // SSE Streaming Analysis
    // ============================================================

    /**
     * SSE 串流 AI 分析。Controller 僅建立 SSE 連線並交給 use case 在 async 執行緒處理；
     * 串流、報告儲存與事件輸出皆由 {@link AiAnalysisStreamUseCase} 負責。
     */
    @PostMapping(value = "/analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI analysis stream (SSE)")
    public SseEmitter streamAnalysis(@Valid @RequestBody AiAnalysisRequest request) {
        // timeout=0 表示無限等待，由 Groq 回應決定結束時機
        SseEmitter emitter = new SseEmitter(0L);
        String requestId = "r-" + UUID.randomUUID();

        // ⚠️ 重要：在進入 async 之前先抓取 userId
        // 因為 SecurityContext 是 thread-local，不會傳遞到 async 執行緒
        final Long userId = CurrentUser.require(currentUserProvider);

        // 使用 async 執行，避免阻塞 servlet 執行緒
        CompletableFuture.runAsync(() -> analysisStreamUseCase.stream(emitter, requestId, userId, request));
        return emitter;
    }

    // ============================================================
    // Report CRUD
    // ============================================================

    /**
     * 列出 AI 報告（分頁）。
     */
    @GetMapping("/reports")
    @Operation(summary = "List AI reports")
    public Result<PageResponse<AiReportSummaryResponse>> listReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = CurrentUser.require(currentUserProvider);
        PageableFactory.Paged pageInfo = PageableFactory.of(
                page, size, MAX_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AiReportView> reports = aiReportService.listReports(userId, pageInfo.pageable());
        List<AiReportSummaryResponse> items = reports.getContent().stream()
                .map(AiReportSummaryResponse::from)
                .toList();
        return Result.ok(PageResponse.ok(items, pageInfo.page(), pageInfo.size(), reports.getTotalElements()));
    }

    /**
     * 取得單一 AI 報告詳情。
     */
    @GetMapping("/reports/{reportId}")
    @Operation(summary = "Get AI report detail")
    public Result<AiReportResponse> getReport(@PathVariable String reportId) {
        Long userId = CurrentUser.require(currentUserProvider);
        AiReportView report = aiReportService.getReport(userId, IdParser.parseId(reportId));
        return Result.ok(AiReportResponse.from(report));
    }
}
