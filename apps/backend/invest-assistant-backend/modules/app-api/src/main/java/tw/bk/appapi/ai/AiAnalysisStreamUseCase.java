package tw.bk.appapi.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.AiAnalysisInput;
import tw.bk.appai.model.AiReportContext;
import tw.bk.appai.model.AiReportView;
import tw.bk.appai.service.AiReportService;
import tw.bk.appapi.ai.dto.AiAnalysisRequest;
import tw.bk.appapi.web.IdParser;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

/**
 * AI 分析的 SSE 串流流程：準備上下文 → meta → 串流 Groq delta → 儲存報告 → done/error。
 *
 * <p>從 {@code AiController} 抽出，讓 controller 只負責建立 SSE 連線並丟到 async 執行；
 * 串流、報告儲存與錯誤事件都收斂在此。{@link #stream} 為同步阻塞流程，由呼叫端決定執行緒。
 */
@Slf4j
class AiAnalysisStreamUseCase {

    private final AiReportService aiReportService;
    private final GroqChatClient groqChatClient;
    private final AnalysisPromptProvider promptProvider;

    AiAnalysisStreamUseCase(AiReportService aiReportService,
            GroqChatClient groqChatClient,
            AnalysisPromptProvider promptProvider) {
        this.aiReportService = aiReportService;
        this.groqChatClient = groqChatClient;
        this.promptProvider = promptProvider;
    }

    /**
     * 執行一次分析串流。id 解析錯誤與上下文準備錯誤都會以 SSE error 事件回報（而非 HTTP 例外）。
     */
    void stream(SseEmitter emitter, String requestId, Long userId, AiAnalysisRequest request) {
        AiReportContext context;

        // Step 1: 準備上下文
        try {
            AiAnalysisInput input = AiAnalysisInput.builder()
                    .userId(userId)
                    .portfolioId(IdParser.parseIdOrNull(request.getPortfolioId()))
                    .instrumentId(IdParser.parseIdOrNull(request.getInstrumentId()))
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
        List<Map<String, String>> messages = promptProvider.buildMessages(context);
        StringBuilder buffer = new StringBuilder();
        AtomicBoolean failed = new AtomicBoolean(false);

        try {
            groqChatClient.streamChat(messages, context.getUserId(), "analysis")
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
                    .onErrorResume(ex -> Flux.empty())
                    .doOnComplete(() -> {
                        if (failed.get()) {
                            return;
                        }
                        // Step 3: 儲存報告並送 done
                        try {
                            AiReportView report = aiReportService.saveReport(context, buffer.toString());
                            Map<String, Object> done = new LinkedHashMap<>();
                            done.put("reportId", report.id() != null ? report.id().toString() : null);
                            emitter.send(SseEmitter.event().name("done").data(done));
                            log.info("SSE stream completed: requestId={}, reportId={}",
                                    requestId, report.id());
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
    }

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

    private void sendDelta(SseEmitter emitter, String text) throws Exception {
        emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
    }

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
}
