package tw.bk.appapi.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import tw.bk.appai.model.AiAnalysisInput;
import tw.bk.appai.model.AiReportContext;
import tw.bk.appai.service.AiReportService;
import tw.bk.appapi.ai.dto.AiAnalysisRequest;
import tw.bk.appapi.ai.vo.AiReportResponse;
import tw.bk.appapi.ai.vo.AiReportSummaryResponse;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.apppersistence.entity.AiReportEntity;

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
    private static final String SYSTEM_PROMPT = "You are a financial analysis assistant. Be concise and factual.";

    private final AiReportService aiReportService;
    private final GroqChatClient groqChatClient;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public AiController(AiReportService aiReportService,
            GroqChatClient groqChatClient,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper) {
        this.aiReportService = aiReportService;
        this.groqChatClient = groqChatClient;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // SSE Streaming Analysis
    // ============================================================

    /**
     * SSE 串流 AI 分析。
     * 
     * 流程：
     * 1. 準備上下文 (prepareContext)
     * 2. 送出 meta 事件
     * 3. 訂閱 Groq stream，每個 chunk 送 delta 事件
     * 4. 完成後儲存報告，送 done 事件
     * 
     * 錯誤處理：發送 error 事件後關閉連線
     */
    @PostMapping(value = "/analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI analysis stream (SSE)")
    public SseEmitter streamAnalysis(@Valid @RequestBody AiAnalysisRequest request) {
        // timeout=0 表示無限等待，由 Groq 回應決定結束時機
        SseEmitter emitter = new SseEmitter(0L);
        String requestId = "r-" + UUID.randomUUID();

        // ⚠️ 重要：在進入 async 之前先抓取 userId
        // 因為 SecurityContext 是 thread-local，不會傳遞到 async 執行緒
        final Long userId = requireUserId();

        // 使用 async 執行，避免阻塞 servlet 執行緒
        CompletableFuture.runAsync(() -> {
            AiReportContext context;

            // Step 1: 準備上下文
            try {
                AiAnalysisInput input = AiAnalysisInput.builder()
                        .userId(userId)
                        .portfolioId(parseIdOrNull(request.getPortfolioId()))
                        .instrumentId(parseIdOrNull(request.getInstrumentId()))
                        .reportType(request.getReportType())
                        .prompt(request.getPrompt())
                        .build();
                context = aiReportService.prepareContext(input);
                sendMeta(emitter, requestId, context);
                log.debug("SSE stream started: requestId={}", requestId);
            } catch (BusinessException ex) {
                sendError(emitter, ex.getErrorCode(), ex.getMessage());
                return;
            } catch (Exception ex) {
                log.error("Failed to prepare context", ex);
                sendError(emitter, ErrorCode.INTERNAL_ERROR, "Internal server error");
                return;
            }

            // Step 2: 串流 Groq 回應
            List<Map<String, String>> messages = buildMessages(context);
            StringBuilder buffer = new StringBuilder();
            AtomicBoolean failed = new AtomicBoolean(false);

            try {
                groqChatClient.streamChat(messages, context.getUserId())
                        .doOnNext(delta -> {
                            buffer.append(delta);
                            try {
                                sendDelta(emitter, delta);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        })
                        .doOnError(ex -> {
                            failed.set(true);
                            log.error("Groq stream error", ex);
                            if (ex instanceof BusinessException be) {
                                sendError(emitter, be.getErrorCode(), be.getMessage());
                            } else {
                                sendError(emitter, ErrorCode.INTERNAL_ERROR,
                                        "Groq streaming failed: " + ex.getMessage());
                            }
                        })
                        .onErrorResume(ex -> reactor.core.publisher.Flux.empty())
                        .doOnComplete(() -> {
                            if (failed.get()) {
                                return;
                            }
                            // Step 3: 儲存報告並送 done
                            try {
                                AiReportEntity report = aiReportService.saveReport(context, buffer.toString());
                                Map<String, Object> done = new LinkedHashMap<>();
                                done.put("reportId", report.getId() != null ? report.getId().toString() : null);
                                emitter.send(SseEmitter.event().name("done").data(done));
                                log.info("SSE stream completed: requestId={}, reportId={}",
                                        requestId, report.getId());
                            } catch (Exception ex) {
                                log.error("Failed to save report or send done", ex);
                            } finally {
                                emitter.complete();
                            }
                        })
                        .blockLast();
            } catch (BusinessException ex) {
                sendError(emitter, ex.getErrorCode(), ex.getMessage());
            } catch (Exception ex) {
                log.error("Groq stream failed", ex);
                sendError(emitter, ErrorCode.INTERNAL_ERROR, "Groq streaming failed");
            }
        });
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
        Long userId = requireUserId();
        PageableInfo pageInfo = buildPageable(page, size);
        Page<AiReportEntity> reports = aiReportService.listReports(userId, pageInfo.pageable);
        List<AiReportSummaryResponse> items = reports.getContent().stream()
                .map(AiReportSummaryResponse::from)
                .toList();
        return Result.ok(PageResponse.ok(items, pageInfo.page, pageInfo.size, reports.getTotalElements()));
    }

    /**
     * 取得單一 AI 報告詳情。
     */
    @GetMapping("/reports/{reportId}")
    @Operation(summary = "Get AI report detail")
    public Result<AiReportResponse> getReport(@PathVariable String reportId) {
        Long userId = requireUserId();
        AiReportEntity report = aiReportService.getReport(userId, parseId(reportId));
        return Result.ok(AiReportResponse.from(report));
    }

    // ============================================================
    // SSE Helper Methods
    // ============================================================

    /**
     * 送出 meta 事件，包含 requestId 和相關 ID。
     */
    private void sendMeta(SseEmitter emitter, String requestId, AiReportContext context) throws Exception {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", requestId);
        if (context.getInstrumentId() != null) {
            meta.put("instrumentId", context.getInstrumentId().toString());
        }
        if (context.getPortfolioId() != null) {
            meta.put("portfolioId", context.getPortfolioId().toString());
        }
        if (context.getReportType() != null && !context.getReportType().isBlank()) {
            meta.put("reportType", context.getReportType());
        }
        emitter.send(SseEmitter.event().name("meta").data(meta));
    }

    /**
     * 送出 delta 事件，包含一段 LLM 回覆文字。
     */
    private void sendDelta(SseEmitter emitter, String text) throws Exception {
        emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
    }

    /**
     * 送出 error 事件並關閉連線。
     */
    private void sendError(SseEmitter emitter, ErrorCode code, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", code.getCode());
            payload.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(payload));
        } catch (Exception ignored) {
            // 忽略送出失敗（連線可能已斷開）
        } finally {
            emitter.complete();
        }
    }

    // ============================================================
    // LLM Message Builder
    // ============================================================

    /**
     * 建立 LLM 訊息列表（system + user）。
     */
    private List<Map<String, String>> buildMessages(AiReportContext context) {
        String prompt = resolvePrompt(context);
        String summaryJson = toJson(context.getSummary());

        StringBuilder userContent = new StringBuilder();
        if (context.getReportType() != null) {
            userContent.append("report_type: ").append(context.getReportType());
        }
        if (context.getInstrumentDisplay() != null && !context.getInstrumentDisplay().isBlank()) {
            userContent.append("\n").append("instrument: ").append(context.getInstrumentDisplay());
        }
        if (context.getPortfolioName() != null && !context.getPortfolioName().isBlank()) {
            userContent.append("\n").append("portfolio: ").append(context.getPortfolioName());
        }
        if (context.getPortfolioCurrency() != null && !context.getPortfolioCurrency().isBlank()) {
            userContent.append("\n").append("base_currency: ").append(context.getPortfolioCurrency());
        }
        if (summaryJson != null && !summaryJson.isBlank()) {
            userContent.append("\n").append("summary: ").append(summaryJson);
        }
        userContent.append("\n").append("request: ").append(prompt);

        return List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userContent.toString()));
    }

    /**
     * 解析使用者 prompt，若為空則根據 reportType 提供預設。
     */
    private String resolvePrompt(AiReportContext context) {
        String prompt = context.getPrompt();
        if (prompt != null && !prompt.isBlank()) {
            return prompt.trim();
        }
        String reportType = context.getReportType();
        if ("PORTFOLIO".equals(reportType)) {
            return "Summarize the portfolio and highlight key risks.";
        }
        if ("INSTRUMENT".equals(reportType)) {
            return "Summarize the instrument and highlight key risks.";
        }
        return "Provide a concise market summary.";
    }

    /**
     * 將 Map 轉為 JSON 字串。
     */
    private String toJson(Map<String, Object> value) {
        try {
            Map<String, Object> safe = value == null ? Map.of() : value;
            return objectMapper.writeValueAsString(safe);
        } catch (Exception ex) {
            return "{}";
        }
    }

    // ============================================================
    // Common Helpers
    // ============================================================

    /**
     * 取得當前使用者 ID，若未登入則拋出異常。
     */
    private Long requireUserId() {
        return currentUserProvider.getUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized"));
    }

    /**
     * 解析 ID 字串為 Long，失敗則拋出驗證錯誤。
     */
    private Long parseId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid ID format");
        }
    }

    /**
     * 解析 ID 字串為 Long，空值時回傳 null。
     */
    private Long parseIdOrNull(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return null;
        }
        return parseId(idStr);
    }

    /**
     * 封裝分頁資訊，確保回傳的 page/size 與實際查詢一致。
     */
    private record PageableInfo(Pageable pageable, int page, int size) {
    }

    /**
     * 建立分頁物件，處理邊界值。
     */
    private PageableInfo buildPageable(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PageableInfo(pageable, safePage, safeSize);
    }
}
