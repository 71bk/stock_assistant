package tw.bk.appapi.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.ConversationMessageView;
import tw.bk.appai.model.ConversationView;
import tw.bk.appai.model.InstrumentCandidate;
import tw.bk.appai.model.QuoteCandidate;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.service.AiInstrumentToolService;
import tw.bk.appai.service.AiQuoteToolService;
import tw.bk.appai.skill.ChatSkillBatchResult;
import tw.bk.appai.skill.ChatSkillContext;
import tw.bk.appai.skill.ChatSkillExecutor;
import tw.bk.appapi.ai.dto.ChatMessageRequest;
import tw.bk.appapi.ai.dto.CreateConversationRequest;
import tw.bk.appapi.ai.dto.UpdateConversationRequest;
import tw.bk.appapi.ai.vo.ConversationDetailResponse;
import tw.bk.appapi.ai.vo.ConversationMessageResponse;
import tw.bk.appapi.ai.vo.ConversationSummaryResponse;
import tw.bk.appapi.sse.BufferedSseSession;
import tw.bk.appapi.sse.BufferedSseSessionStore;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.UserRole;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appportfolio.model.PortfolioChatContext;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.appportfolio.service.QuoteProvider;
import tw.bk.apprag.client.AiWorkerChunk;
import tw.bk.apprag.client.AiWorkerQueryResponse;
import tw.bk.apprag.client.AiWorkerRagClient;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.service.StockQuoteService;

@RestController
@RequestMapping("/ai/conversations")
@Tag(name = "AI Conversations", description = "Chat conversation APIs")
@Slf4j
public class AiConversationController {
    private static final String LAST_MENTIONED_CACHE_NAME = "conversationLastMentioned";
    private static final Pattern SYMBOL_KEY_PATTERN = Pattern
            .compile("\\b([A-Za-z]{2}:[A-Za-z0-9]{4}:[A-Za-z0-9.\\-]{1,16})\\b");
    private static final Pattern URL_SYMBOL_KEY_PATTERN = Pattern
            .compile("(?i)(?:symbolKey|symbol_key)=([A-Za-z]{2}:[A-Za-z0-9]{4}:[A-Za-z0-9.\\-]{1,16})");
    private static final List<String> PRONOUN_MARKERS = List.of(
            "這隻", "這檔", "這支", "那隻", "那檔", "那支", "它", "該股",
            "他", "她", "這個", "那個");

    private final AiConversationService conversationService;
    private final GroqChatClient groqChatClient;
    private final CurrentUserProvider currentUserProvider;
    private final Executor aiExecutor;
    private final AiInstrumentToolService instrumentToolService;
    private final AiQuoteToolService quoteToolService;
    private final CacheManager cacheManager;
    private final PortfolioService portfolioService;
    private final StockQuoteService stockQuoteService;
    private final AiWorkerRagClient ragClient;
    private final MeterRegistry meterRegistry;
    private final BufferedSseSessionStore bufferedSseSessionStore;
    private final ChatSkillExecutor chatSkillExecutor;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.instrument-search.enabled:true}")
    private boolean instrumentSearchEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.instrument-search.limit:10}")
    private int instrumentSearchLimit;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.quote-search.enabled:true}")
    private boolean quoteSearchEnabled;

    // Chinese keywords defined in code to avoid properties encoding issues
    private static final String QUOTE_KEYWORDS_CHINESE = "價格,股價,多少,現價,收盤,漲跌,報價,最新,標價";

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.quote-search.keywords-en:quote,price}")
    private String quoteSearchKeywordsEn;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.pronoun-lookback.limit:5}")
    private int pronounLookbackLimit;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.portfolio-context.enabled:true}")
    private boolean portfolioContextEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.portfolio-context.max-portfolios:3}")
    private int portfolioContextMaxPortfolios;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.rag.enabled:false}")
    private boolean ragEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.rag.top-k:3}")
    private int ragTopK;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.rag.source-type:}")
    private String ragSourceType;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.rag.max-content-chars:500}")
    private int ragMaxContentChars;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.rag.timeout-ms:1200}")
    private long ragTimeoutMs;

    @org.springframework.beans.factory.annotation.Value("${app.ai.chat.rag.metrics.enabled:true}")
    private boolean ragMetricsEnabled;

    public AiConversationController(AiConversationService conversationService,
            GroqChatClient groqChatClient,
            CurrentUserProvider currentUserProvider,
            @Qualifier("aiExecutor") Executor aiExecutor,
            AiInstrumentToolService instrumentToolService,
            AiQuoteToolService quoteToolService,
            CacheManager cacheManager,
            PortfolioService portfolioService,
            StockQuoteService stockQuoteService,
            AiWorkerRagClient ragClient,
            MeterRegistry meterRegistry,
            BufferedSseSessionStore bufferedSseSessionStore,
            ChatSkillExecutor chatSkillExecutor) {
        this.conversationService = conversationService;
        this.groqChatClient = groqChatClient;
        this.currentUserProvider = currentUserProvider;
        this.aiExecutor = aiExecutor;
        this.instrumentToolService = instrumentToolService;
        this.quoteToolService = quoteToolService;
        this.cacheManager = cacheManager;
        this.portfolioService = portfolioService;
        this.stockQuoteService = stockQuoteService;
        this.ragClient = ragClient;
        this.meterRegistry = meterRegistry;
        this.bufferedSseSessionStore = bufferedSseSessionStore;
        this.chatSkillExecutor = chatSkillExecutor;
    }

    @PostMapping
    @Operation(summary = "Create conversation")
    public Result<ConversationSummaryResponse> createConversation(@RequestBody CreateConversationRequest request) {
        Long userId = requireUserId();
        ConversationView conversation = conversationService.createConversation(userId,
                request != null ? request.getTitle() : null);
        return Result.ok(ConversationSummaryResponse.from(conversation));
    }

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Update conversation title")
    public Result<ConversationSummaryResponse> updateConversation(@PathVariable String conversationId,
            @RequestBody UpdateConversationRequest request) {
        Long userId = requireUserId();
        Long id = parseId(conversationId);
        ConversationView conversation = conversationService.updateTitle(userId, id,
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
        ConversationView conversation = conversationService.getConversation(userId, id);
        List<ConversationMessageResponse> messages = conversationService.getRecentMessages(userId, id, limit).stream()
                .map(ConversationMessageResponse::from)
                .toList();
        return Result.ok(ConversationDetailResponse.of(conversation, messages));
    }

    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send chat message (SSE)")
    public SseEmitter sendMessage(@PathVariable String conversationId,
            @RequestBody ChatMessageRequest request,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        Long userId = requireUserId();
        Long id = parseId(conversationId);
        String content = request != null ? request.getContent() : null;
        String clientMessageId = request != null ? request.getClientMessageId() : null;
        String requestId = "c-" + UUID.randomUUID();
        String effectiveClientMessageId = resolveClientMessageId(clientMessageId, requestId);
        String assistantClientMessageId = "assistant:" + effectiveClientMessageId;
        String sessionKey = buildSessionKey(userId, id, effectiveClientMessageId);
        BufferedSseSession session = bufferedSseSessionStore.getOrCreate(sessionKey);
        session.attachEmitter(emitter, lastEventId);

        session.startIfNeeded(() -> CompletableFuture.runAsync(() -> {
            try {
                ConversationMessageView userMessage = conversationService.appendUserMessage(
                        userId, id, content, effectiveClientMessageId);
                ConversationMessageView assistantMessage = conversationService.appendOrGetAssistantMessage(
                        userId, id, "", ConversationMessageStatus.PENDING, assistantClientMessageId);
                sendMeta(session, requestId, id, userMessage.id(), assistantMessage.id());

                if (assistantMessage.status() == ConversationMessageStatus.COMPLETED
                        && assistantMessage.content() != null
                        && !assistantMessage.content().isBlank()) {
                    sendDelta(session, assistantMessage.content());
                    sendDone(session, assistantMessage.id());
                    return;
                }
                if (assistantMessage.status() == ConversationMessageStatus.FAILED) {
                    sendError(session, ErrorCode.INTERNAL_ERROR, "Assistant message failed");
                    return;
                }

                String directQuoteReply = buildDirectQuoteReply(userId, id, userMessage.id(), content);
                if (directQuoteReply != null) {
                    sendDelta(session, directQuoteReply);
                    ConversationMessageView assistant = conversationService.updateAssistantMessage(
                            userId, id, assistantMessage.id(), directQuoteReply, ConversationMessageStatus.COMPLETED);
                    sendDone(session, assistant.id());
                    return;
                }

                ChatSkillContext skillContext = new ChatSkillContext(
                        userId,
                        id,
                        userMessage.id(),
                        content,
                        resolveCurrentUserRole());
                ChatSkillBatchResult skillBatchResult = chatSkillExecutor.executeAll(skillContext);
                logSkillBatch(skillBatchResult);
                String toolContext = skillBatchResult.mergedContext();
                List<Map<String, String>> messages = toolContext == null
                        ? conversationService.buildContextMessages(userId, id, content, userMessage.id())
                        : conversationService.buildContextMessagesWithTool(userId, id, content, userMessage.id(),
                                toolContext);
                StringBuilder buffer = new StringBuilder();
                AtomicBoolean failed = new AtomicBoolean(false);

                groqChatClient.streamChat(messages, userId)
                        .doOnNext(delta -> {
                            buffer.append(delta);
                            sendDelta(session, delta);
                        })
                        .doOnError(ex -> {
                            failed.set(true);
                            try {
                                conversationService.updateAssistantMessage(
                                        userId,
                                        id,
                                        assistantMessage.id(),
                                        buffer.toString(),
                                        ConversationMessageStatus.FAILED);
                            } catch (Exception saveEx) {
                                log.error("Failed to mark assistant message as FAILED", saveEx);
                            }
                            if (ex instanceof BusinessException be) {
                                sendError(session, be.getErrorCode(), be.getMessage());
                            } else {
                                sendError(session, ErrorCode.INTERNAL_ERROR,
                                        "Groq streaming failed: " + ex.getMessage());
                            }
                        })
                        .onErrorResume(ex -> reactor.core.publisher.Flux.empty())
                        .doOnComplete(() -> {
                            if (failed.get()) {
                                return;
                            }
                            try {
                                ConversationMessageView assistant = conversationService.updateAssistantMessage(
                                        userId,
                                        id,
                                        assistantMessage.id(),
                                        buffer.toString(),
                                        ConversationMessageStatus.COMPLETED);
                                sendDone(session, assistant.id());
                            } catch (Exception ex) {
                                log.error("Failed to save assistant message", ex);
                            }
                        })
                        .blockLast();
            } catch (BusinessException ex) {
                sendError(session, ex.getErrorCode(), ex.getMessage());
            } catch (Exception ex) {
                log.error("Chat streaming failed", ex);
                sendError(session, ErrorCode.INTERNAL_ERROR, "Chat streaming failed");
            }
        }, aiExecutor));

        return emitter;
    }

    private void sendMeta(
            BufferedSseSession session,
            String requestId,
            Long conversationId,
            Long userMessageId,
            Long assistantMessageId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", requestId);
        meta.put("conversationId", conversationId != null ? conversationId.toString() : null);
        meta.put("userMessageId", userMessageId != null ? userMessageId.toString() : null);
        meta.put("assistantMessageId", assistantMessageId != null ? assistantMessageId.toString() : null);
        session.sendEvent("meta", meta);
    }

    private void sendDelta(BufferedSseSession session, String text) {
        session.sendEvent("delta", Map.of("text", text));
    }

    private void sendDone(BufferedSseSession session, Long assistantMessageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assistantMessageId", assistantMessageId != null ? assistantMessageId.toString() : null);
        session.sendEvent("done", payload);
        session.complete();
    }

    private void sendError(BufferedSseSession session, ErrorCode code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code.getCode());
        payload.put("message", message);
        session.sendEvent("error", payload);
        session.complete();
    }

    private String mergeToolContext(String... sections) {
        if (sections == null || sections.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String section : sections) {
            if (section == null || section.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(section.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String buildPortfolioContext(Long userId) {
        if (!portfolioContextEnabled) {
            return null;
        }

        int limit = Math.min(Math.max(portfolioContextMaxPortfolios, 1), 10);
        try {
            List<PortfolioChatContext> contexts = portfolioService.listChatContexts(userId, createQuoteProvider());
            if (contexts == null || contexts.isEmpty()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("portfolio_context:\n");
            int appended = 0;
            for (PortfolioChatContext context : contexts) {
                if (context == null || context.portfolioId() == null) {
                    continue;
                }
                if (appended >= limit) {
                    break;
                }
                appended++;
                sb.append("- portfolio_id: ").append(context.portfolioId()).append('\n');
                if (context.portfolioName() != null && !context.portfolioName().isBlank()) {
                    sb.append("  name: ").append(sanitizeToolValue(context.portfolioName())).append('\n');
                }
                if (context.baseCurrency() != null && !context.baseCurrency().isBlank()) {
                    sb.append("  base_currency: ").append(context.baseCurrency()).append('\n');
                }
                sb.append("  holdings_count: ").append(context.holdingsCount()).append('\n');
                if (context.totalValue() != null) {
                    sb.append("  total_value: ").append(context.totalValue().toPlainString()).append('\n');
                }
                if (context.cashValue() != null) {
                    sb.append("  cash_value: ").append(context.cashValue().toPlainString()).append('\n');
                }
                if (context.positionsValue() != null) {
                    sb.append("  positions_value: ").append(context.positionsValue().toPlainString()).append('\n');
                }
                if (context.asOfDate() != null) {
                    sb.append("  as_of_date: ").append(context.asOfDate()).append('\n');
                }
                sb.append("  valuation_source: ").append(context.snapshotBacked() ? "snapshot" : "realtime")
                        .append('\n');
            }

            if (appended == 0) {
                return null;
            }
            sb.append("portfolio_context_total_portfolios: ").append(contexts.size()).append('\n');
            if (contexts.size() > appended) {
                sb.append("portfolio_context_truncated: true\n");
            }
            return sb.toString().trim();
        } catch (Exception ex) {
            log.warn("Failed to build portfolio context: userId={}, reason={}", userId, ex.getMessage());
            return null;
        }
    }

    private String buildRagContext(Long userId, String content) {
        if (!ragEnabled) {
            return null;
        }
        if (content == null || content.isBlank()) {
            return null;
        }

        long startedNanos = System.nanoTime();
        String outcome = "miss";
        int chunkCount = 0;
        int topK = Math.min(Math.max(ragTopK, 1), 8);
        int maxContentChars = Math.max(ragMaxContentChars, 100);
        String sourceType = (ragSourceType == null || ragSourceType.isBlank()) ? null : ragSourceType.trim();
        try {
            Duration timeout = resolveRagTimeout();
            AiWorkerQueryResponse response = ragClient.query(userId, content, topK, sourceType, timeout);
            List<AiWorkerChunk> chunks = response != null ? response.getChunks() : null;
            if (chunks == null || chunks.isEmpty()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("rag_context:\n");
            int appended = 0;
            for (AiWorkerChunk chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                String chunkContent = sanitizeRagContent(chunk.getContent(), maxContentChars);
                if (chunkContent.isBlank()) {
                    continue;
                }
                appended++;
                sb.append("- content: ").append(chunkContent).append('\n');
                if (chunk.getTitle() != null && !chunk.getTitle().isBlank()) {
                    sb.append("  title: ").append(sanitizeToolValue(chunk.getTitle())).append('\n');
                }
                if (chunk.getSourceType() != null && !chunk.getSourceType().isBlank()) {
                    sb.append("  source_type: ").append(chunk.getSourceType()).append('\n');
                }
                if (chunk.getSourceId() != null && !chunk.getSourceId().isBlank()) {
                    sb.append("  source_id: ").append(chunk.getSourceId()).append('\n');
                }
                if (chunk.getChunkIndex() != null) {
                    sb.append("  chunk_index: ").append(chunk.getChunkIndex()).append('\n');
                }
                if (chunk.getDistance() != null) {
                    sb.append("  distance: ").append(chunk.getDistance()).append('\n');
                }
            }

            if (appended == 0) {
                return null;
            }
            outcome = "hit";
            chunkCount = appended;
            sb.append("rag_context_total_chunks: ").append(appended).append('\n');
            return sb.toString().trim();
        } catch (Exception ex) {
            outcome = resolveRagOutcome(ex);
            log.warn("RAG lookup failed: userId={}, outcome={}, reason={}", userId, outcome, ex.getMessage());
            return null;
        } finally {
            recordRagMetrics(outcome, chunkCount, System.nanoTime() - startedNanos);
        }
    }

    private Duration resolveRagTimeout() {
        long timeout = Math.min(Math.max(ragTimeoutMs, 200), 10000);
        return Duration.ofMillis(timeout);
    }

    private String resolveRagOutcome(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (current instanceof java.util.concurrent.TimeoutException
                    || (message != null && message.toLowerCase().contains("timeout"))) {
                return "timeout";
            }
            current = current.getCause();
        }
        return "error";
    }

    private void recordRagMetrics(String outcome, int chunkCount, long elapsedNanos) {
        if (!ragMetricsEnabled || meterRegistry == null) {
            return;
        }
        String safeOutcome = (outcome == null || outcome.isBlank()) ? "unknown" : outcome;
        meterRegistry.counter("app.ai.chat.rag.lookup.total", "outcome", safeOutcome).increment();
        Timer.builder("app.ai.chat.rag.lookup.latency")
                .description("RAG lookup latency for chat context injection")
                .tag("outcome", safeOutcome)
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
        if (chunkCount > 0) {
            meterRegistry.summary("app.ai.chat.rag.lookup.chunks")
                    .record(chunkCount);
        }
    }

    private String buildInstrumentContext(Long userId, Long conversationId, Long currentUserMessageId, String content) {
        if (!instrumentSearchEnabled) {
            return null;
        }
        if (content == null || content.isBlank()) {
            return null;
        }
        int limit = Math.min(Math.max(instrumentSearchLimit, 1), 20);
        boolean pronounQuery = containsPronoun(content);
        boolean fetchQuote = shouldFetchQuote(content);
        boolean quoteIntent = quoteSearchEnabled && fetchQuote;
        log.info("Quote intent debug: quoteSearchEnabled={}, shouldFetchQuote={}",
                quoteSearchEnabled, fetchQuote);
        List<InstrumentCandidate> candidates = resolveCandidates(content, limit);
        if ((candidates == null || candidates.isEmpty()) && pronounQuery) {
            candidates = resolveCandidatesFromConversation(userId, conversationId, currentUserMessageId, limit);
        } else if ((candidates == null || candidates.isEmpty()) && quoteIntent) {
            // For quote-intent follow-up questions without pronouns, only try
            // last-mentioned cache.
            candidates = loadLastMentionedSymbolKey(userId, conversationId);
        }
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        rememberLastMentionedSymbolKey(userId, conversationId, candidates.get(0));

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

        if (quoteIntent) {
            InstrumentCandidate first = candidates.get(0);
            QuoteCandidate quote = null;
            String quoteError = null;
            try {
                quote = quoteToolService.getQuote(first.symbolKey());
            } catch (Exception ex) {
                quoteError = ex.getMessage();
                log.warn("Quote tool failed: symbolKey={}, reason={}", first.symbolKey(), ex.getMessage());
            }
            if (quote != null) {
                sb.append("quote:\n");
                appendQuote(sb, quote);
                sb.append("tool_quote_available: true\n");
            } else {
                sb.append("tool_quote_available: false\n");
                if (quoteError == null || quoteError.isBlank()) {
                    quoteError = "QUOTE_NOT_AVAILABLE";
                }
                sb.append("tool_quote_error: ").append(sanitizeToolValue(quoteError)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String buildDirectQuoteReply(Long userId, Long conversationId, Long currentUserMessageId, String content) {
        // Disabled: All responses go through LLM for a more natural conversational
        // experience.
        // The quote data is provided via buildInstrumentContext() as tool context.
        return null;
    }

    private String formatDirectQuoteReply(InstrumentCandidate candidate, QuoteCandidate quote) {
        String display = candidate != null && candidate.name() != null && !candidate.name().isBlank()
                ? candidate.name().trim()
                : (candidate != null && candidate.ticker() != null && !candidate.ticker().isBlank()
                        ? candidate.ticker().trim()
                        : quote.symbolKey());
        String ticker = candidate != null ? candidate.ticker() : quote.ticker();
        StringBuilder sb = new StringBuilder();
        sb.append(display);
        if (ticker != null && !ticker.isBlank()) {
            sb.append("（").append(ticker.trim()).append("）");
        }
        sb.append(" 最新價 ").append(quote.price());
        if ((quote.change() != null && !quote.change().isBlank())
                || (quote.changePercent() != null && !quote.changePercent().isBlank())) {
            sb.append("，漲跌 ");
            if (quote.change() != null && !quote.change().isBlank()) {
                sb.append(quote.change());
            }
            if (quote.changePercent() != null && !quote.changePercent().isBlank()) {
                sb.append(" (").append(quote.changePercent()).append("%)");
            }
        }
        if (quote.timestamp() != null) {
            sb.append("，時間 ").append(quote.timestamp());
        }
        return sb.toString();
    }

    private List<InstrumentCandidate> resolveCandidates(String content, int limit) {
        List<InstrumentCandidate> candidates = instrumentToolService.searchCandidates(content, limit);
        if (candidates != null && !candidates.isEmpty()) {
            return candidates;
        }

        String symbolKey = extractSymbolKey(content);
        if (symbolKey == null) {
            return List.of();
        }
        return instrumentToolService.searchCandidates(symbolKey, 1);
    }

    private List<InstrumentCandidate> resolveCandidatesFromConversation(Long userId, Long conversationId,
            Long currentUserMessageId, int limit) {
        List<InstrumentCandidate> cached = loadLastMentionedSymbolKey(userId, conversationId);
        if (!cached.isEmpty()) {
            return cached;
        }

        int lookback = Math.min(Math.max(pronounLookbackLimit, 1), 10);
        List<ConversationMessageView> recentMessages = conversationService.getRecentMessages(userId, conversationId,
                lookback + 2);
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ConversationMessageView message = recentMessages.get(i);
            if (message == null) {
                continue;
            }
            if (currentUserMessageId != null && currentUserMessageId.equals(message.id())) {
                continue;
            }
            if (message.role() == null || !"user".equalsIgnoreCase(message.role().value())) {
                continue;
            }
            List<InstrumentCandidate> candidates = resolveCandidates(message.content(), limit);
            if (candidates != null && !candidates.isEmpty()) {
                rememberLastMentionedSymbolKey(userId, conversationId, candidates.get(0));
                return candidates;
            }
        }
        return List.of();
    }

    private boolean containsPronoun(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String compact = content.replace(" ", "");
        for (String marker : PRONOUN_MARKERS) {
            if (compact.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String extractSymbolKey(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher urlMatcher = URL_SYMBOL_KEY_PATTERN.matcher(content);
        if (urlMatcher.find()) {
            String key = urlMatcher.group(1);
            return key == null ? null : key.trim().toUpperCase();
        }

        Matcher matcher = SYMBOL_KEY_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String key = matcher.group(1);
        return key == null ? null : key.trim().toUpperCase();
    }

    private void rememberLastMentionedSymbolKey(Long userId, Long conversationId, InstrumentCandidate candidate) {
        if (candidate == null || candidate.symbolKey() == null || candidate.symbolKey().isBlank()) {
            return;
        }
        Cache cache = cacheManager.getCache(LAST_MENTIONED_CACHE_NAME);
        if (cache == null) {
            return;
        }
        cache.put(lastMentionedCacheKey(userId, conversationId), candidate.symbolKey().trim().toUpperCase());
    }

    private List<InstrumentCandidate> loadLastMentionedSymbolKey(Long userId, Long conversationId) {
        Cache cache = cacheManager.getCache(LAST_MENTIONED_CACHE_NAME);
        if (cache == null) {
            return List.of();
        }
        String symbolKey = cache.get(lastMentionedCacheKey(userId, conversationId), String.class);
        if (symbolKey == null || symbolKey.isBlank()) {
            return List.of();
        }
        List<InstrumentCandidate> candidates = instrumentToolService.searchCandidates(symbolKey, 1);
        if (candidates == null || candidates.isEmpty()) {
            cache.evict(lastMentionedCacheKey(userId, conversationId));
            return List.of();
        }
        return candidates;
    }

    private String lastMentionedCacheKey(Long userId, Long conversationId) {
        return userId + ":" + conversationId;
    }

    private String sanitizeToolValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String sanitizeRagContent(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars).trim() + "...";
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
        // Combine Chinese keywords (from code) and English keywords (from properties)
        String allKeywords = QUOTE_KEYWORDS_CHINESE + "," + quoteSearchKeywordsEn;
        String[] keywords = allKeywords.split(",");
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

    private QuoteProvider createQuoteProvider() {
        return this::resolveCurrentPrice;
    }

    private Optional<BigDecimal> resolveCurrentPrice(String symbolKey) {
        if (symbolKey == null || symbolKey.isBlank()) {
            return Optional.empty();
        }
        try {
            Quote quote = stockQuoteService.getQuote(symbolKey);
            return quote == null ? Optional.empty() : Optional.ofNullable(quote.getPrice());
        } catch (Exception ex) {
            log.debug("Portfolio quote lookup failed: symbolKey={}, reason={}", symbolKey, ex.getMessage());
            return Optional.empty();
        }
    }

    private String resolveClientMessageId(String clientMessageId, String fallbackRequestId) {
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            return clientMessageId.trim();
        }
        return fallbackRequestId;
    }

    private String buildSessionKey(Long userId, Long conversationId, String clientMessageId) {
        return userId + ":" + conversationId + ":" + clientMessageId;
    }

    private UserRole resolveCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return UserRole.USER;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority != null && "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())) {
                return UserRole.ADMIN;
            }
        }
        return UserRole.USER;
    }

    private void logSkillBatch(ChatSkillBatchResult batch) {
        if (batch == null || batch.results() == null || !log.isDebugEnabled()) {
            return;
        }
        String summary = batch.results().stream()
                .map(result -> result.skillName() + ":" + result.status())
                .collect(Collectors.joining(", "));
        log.debug("Chat skills executed: {}", summary);
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
