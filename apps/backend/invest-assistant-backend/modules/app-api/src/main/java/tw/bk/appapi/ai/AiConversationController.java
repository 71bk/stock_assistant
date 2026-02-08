package tw.bk.appapi.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.InstrumentCandidate;
import tw.bk.appai.model.QuoteCandidate;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.service.AiInstrumentToolService;
import tw.bk.appai.service.AiQuoteToolService;
import tw.bk.appapi.ai.dto.ChatMessageRequest;
import tw.bk.appapi.ai.dto.CreateConversationRequest;
import tw.bk.appapi.ai.dto.UpdateConversationRequest;
import tw.bk.appapi.ai.vo.ConversationDetailResponse;
import tw.bk.appapi.ai.vo.ConversationMessageResponse;
import tw.bk.appapi.ai.vo.ConversationSummaryResponse;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.apppersistence.entity.ConversationEntity;
import tw.bk.apppersistence.entity.ConversationMessageEntity;

@RestController
@RequestMapping("/ai/conversations")
@Tag(name = "AI Conversations", description = "Chat conversation APIs")
@Slf4j
public class AiConversationController {

    private final AiConversationService conversationService;
    private final GroqChatClient groqChatClient;
    private final CurrentUserProvider currentUserProvider;
    private final Executor aiExecutor;
    private final AiInstrumentToolService instrumentToolService;
    private final AiQuoteToolService quoteToolService;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.instrument-search.enabled:true}")
    private boolean instrumentSearchEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.instrument-search.limit:10}")
    private int instrumentSearchLimit;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.quote-search.enabled:true}")
    private boolean quoteSearchEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.quote-search.keywords:價格,股價,多少,現價,收盤,漲跌,報價,quote,price}")
    private String quoteSearchKeywords;

    public AiConversationController(AiConversationService conversationService,
            GroqChatClient groqChatClient,
            CurrentUserProvider currentUserProvider,
            @Qualifier("aiExecutor") Executor aiExecutor,
            AiInstrumentToolService instrumentToolService,
            AiQuoteToolService quoteToolService) {
        this.conversationService = conversationService;
        this.groqChatClient = groqChatClient;
        this.currentUserProvider = currentUserProvider;
        this.aiExecutor = aiExecutor;
        this.instrumentToolService = instrumentToolService;
        this.quoteToolService = quoteToolService;
    }

    @PostMapping
    @Operation(summary = "Create conversation")
    public Result<ConversationSummaryResponse> createConversation(@RequestBody CreateConversationRequest request) {
        Long userId = requireUserId();
        ConversationEntity conversation = conversationService.createConversation(userId,
                request != null ? request.getTitle() : null);
        return Result.ok(ConversationSummaryResponse.from(conversation));
    }

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Update conversation title")
    public Result<ConversationSummaryResponse> updateConversation(@PathVariable String conversationId,
            @RequestBody UpdateConversationRequest request) {
        Long userId = requireUserId();
        Long id = parseId(conversationId);
        ConversationEntity conversation = conversationService.updateTitle(userId, id,
                request != null ? request.getTitle() : null);
        return Result.ok(ConversationSummaryResponse.from(conversation));
    }

    @GetMapping
    @Operation(summary = "List conversations")
    public Result<List<ConversationSummaryResponse>> listConversations() {
        Long userId = requireUserId();
        List<ConversationSummaryResponse> items = conversationService.listConversations(userId).stream()
                .map(ConversationSummaryResponse::from)
                .toList();
        return Result.ok(items);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get conversation detail")
    public Result<ConversationDetailResponse> getConversation(@PathVariable String conversationId,
            @RequestParam(required = false) Integer limit) {
        Long userId = requireUserId();
        Long id = parseId(conversationId);
        ConversationEntity conversation = conversationService.getConversation(userId, id);
        List<ConversationMessageResponse> messages = conversationService.getRecentMessages(userId, id, limit).stream()
                .map(ConversationMessageResponse::from)
                .toList();
        return Result.ok(ConversationDetailResponse.of(conversation, messages));
    }

    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send chat message (SSE)")
    public SseEmitter sendMessage(@PathVariable String conversationId, @RequestBody ChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        Long userId = requireUserId();
        Long id = parseId(conversationId);
        String content = request != null ? request.getContent() : null;
        String clientMessageId = request != null ? request.getClientMessageId() : null;
        String requestId = "c-" + UUID.randomUUID();

        CompletableFuture.runAsync(() -> {
            try {
                ConversationMessageEntity userMessage = conversationService.appendUserMessage(
                        userId, id, content, clientMessageId);
                sendMeta(emitter, requestId, id, userMessage.getId());

                String toolContext = buildInstrumentContext(content);
                List<Map<String, String>> messages = toolContext == null
                        ? conversationService.buildContextMessages(userId, id, content, userMessage.getId())
                        : conversationService.buildContextMessagesWithTool(userId, id, content, userMessage.getId(),
                                toolContext);
                StringBuilder buffer = new StringBuilder();
                AtomicBoolean failed = new AtomicBoolean(false);

                groqChatClient.streamChat(messages, userId)
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
                            try {
                                ConversationMessageEntity assistant = conversationService.appendAssistantMessage(
                                        userId, id, buffer.toString(), ConversationMessageStatus.COMPLETED);
                                sendDone(emitter, assistant.getId());
                            } catch (Exception ex) {
                                log.error("Failed to save assistant message", ex);
                            } finally {
                                emitter.complete();
                            }
                        })
                        .blockLast();
            } catch (BusinessException ex) {
                sendError(emitter, ex.getErrorCode(), ex.getMessage());
            } catch (Exception ex) {
                log.error("Chat streaming failed", ex);
                sendError(emitter, ErrorCode.INTERNAL_ERROR, "Chat streaming failed");
            }
        }, aiExecutor);

        return emitter;
    }

    private void sendMeta(SseEmitter emitter, String requestId, Long conversationId, Long userMessageId)
            throws Exception {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", requestId);
        meta.put("conversationId", conversationId != null ? conversationId.toString() : null);
        meta.put("userMessageId", userMessageId != null ? userMessageId.toString() : null);
        emitter.send(SseEmitter.event().name("meta").data(meta));
    }

    private void sendDelta(SseEmitter emitter, String text) throws Exception {
        emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
    }

    private void sendDone(SseEmitter emitter, Long assistantMessageId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("assistantMessageId", assistantMessageId != null ? assistantMessageId.toString() : null);
            emitter.send(SseEmitter.event().name("done").data(payload));
        } catch (Exception ex) {
            log.debug("Failed to send done event: {}", ex.getMessage());
        } finally {
            emitter.complete();
        }
    }

    private void sendError(SseEmitter emitter, ErrorCode code, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", code.getCode());
            payload.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(payload));
        } catch (Exception ignored) {
            // ignore
        } finally {
            emitter.complete();
        }
    }

    private String buildInstrumentContext(String content) {
        if (!instrumentSearchEnabled) {
            return null;
        }
        if (content == null || content.isBlank()) {
            return null;
        }
        int limit = Math.min(Math.max(instrumentSearchLimit, 1), 20);
        List<InstrumentCandidate> candidates = instrumentToolService
                .searchCandidates(content, limit);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("instrument_candidates:\n");
        for (InstrumentCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            sb.append("- ");
            if (candidate.ticker() != null) {
                sb.append(candidate.ticker());
            }
            if (candidate.name() != null && !candidate.name().isBlank()) {
                sb.append(" ").append(candidate.name());
            }
            if (candidate.symbolKey() != null && !candidate.symbolKey().isBlank()) {
                sb.append(" (").append(candidate.symbolKey()).append(")");
            }
            if (candidate.assetType() != null && !candidate.assetType().isBlank()) {
                sb.append(" type=").append(candidate.assetType());
            }
            sb.append('\n');
        }

        if (quoteSearchEnabled && shouldFetchQuote(content)) {
            InstrumentCandidate first = candidates.get(0);
            QuoteCandidate quote = null;
            try {
                quote = quoteToolService.getQuote(first.symbolKey());
            } catch (Exception ex) {
                log.debug("Quote tool failed: {}", ex.getMessage());
            }
            if (quote != null) {
                sb.append("quote:\n");
                appendQuote(sb, quote);
            }
        }
        return sb.toString().trim();
    }

    private void appendQuote(StringBuilder sb, QuoteCandidate quote) {
        sb.append("- symbol_key: ").append(quote.symbolKey()).append('\n');
        if (quote.ticker() != null) {
            sb.append("  ticker: ").append(quote.ticker()).append('\n');
        }
        if (quote.price() != null) {
            sb.append("  price: ").append(quote.price()).append('\n');
        }
        if (quote.change() != null) {
            sb.append("  change: ").append(quote.change()).append('\n');
        }
        if (quote.changePercent() != null) {
            sb.append("  change_pct: ").append(quote.changePercent()).append('\n');
        }
        if (quote.open() != null) {
            sb.append("  open: ").append(quote.open()).append('\n');
        }
        if (quote.high() != null) {
            sb.append("  high: ").append(quote.high()).append('\n');
        }
        if (quote.low() != null) {
            sb.append("  low: ").append(quote.low()).append('\n');
        }
        if (quote.previousClose() != null) {
            sb.append("  previous_close: ").append(quote.previousClose()).append('\n');
        }
        if (quote.volume() != null) {
            sb.append("  volume: ").append(quote.volume()).append('\n');
        }
        if (quote.timestamp() != null) {
            sb.append("  ts_utc: ").append(quote.timestamp()).append('\n');
        }
    }

    private boolean shouldFetchQuote(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lowered = content.toLowerCase();
        String[] keywords = quoteSearchKeywords.split(",");
        for (String keyword : keywords) {
            String trimmed = keyword.trim().toLowerCase();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (lowered.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private Long requireUserId() {
        return currentUserProvider.getUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized"));
    }

    private Long parseId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid ID format");
        }
    }
}
