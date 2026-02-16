package tw.bk.appapi.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.ConversationMessageView;
import tw.bk.appai.model.InstrumentCandidate;
import tw.bk.appai.model.QuoteCandidate;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.service.AiInstrumentToolService;
import tw.bk.appai.service.AiQuoteToolService;
import tw.bk.appapi.ai.dto.ChatMessageRequest;
import tw.bk.appapi.sse.BufferedSseSession;
import tw.bk.appapi.sse.BufferedSseSessionStore;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ConversationRole;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appportfolio.model.PortfolioChatContext;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apprag.client.AiWorkerChunk;
import tw.bk.apprag.client.AiWorkerQueryResponse;
import tw.bk.apprag.client.AiWorkerRagClient;
import tw.bk.appstocks.service.StockQuoteService;

@ExtendWith(MockitoExtension.class)
class AiConversationControllerTest {

    @Mock
    private AiConversationService conversationService;
    @Mock
    private GroqChatClient groqChatClient;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private Executor aiExecutor;
    @Mock
    private AiInstrumentToolService instrumentToolService;
    @Mock
    private AiQuoteToolService quoteToolService;
    @Mock
    private PortfolioService portfolioService;
    @Mock
    private StockQuoteService stockQuoteService;
    @Mock
    private AiWorkerRagClient ragClient;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private BufferedSseSessionStore bufferedSseSessionStore;
    @Mock
    private BufferedSseSession bufferedSseSession;

    private ConcurrentMapCacheManager cacheManager;
    private AiConversationController controller;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager("conversationLastMentioned");
        controller = new AiConversationController(
                conversationService,
                groqChatClient,
                currentUserProvider,
                aiExecutor,
                instrumentToolService,
                quoteToolService,
                cacheManager,
                portfolioService,
                stockQuoteService,
                ragClient,
                meterRegistry,
                bufferedSseSessionStore);
        ReflectionTestUtils.setField(controller, "instrumentSearchEnabled", true);
        ReflectionTestUtils.setField(controller, "instrumentSearchLimit", 10);
        ReflectionTestUtils.setField(controller, "quoteSearchEnabled", true);
        ReflectionTestUtils.setField(controller, "quoteSearchKeywordsEn", "quote,price");
        ReflectionTestUtils.setField(controller, "pronounLookbackLimit", 5);
        ReflectionTestUtils.setField(controller, "portfolioContextEnabled", true);
        ReflectionTestUtils.setField(controller, "portfolioContextMaxPortfolios", 3);
        ReflectionTestUtils.setField(controller, "ragEnabled", false);
        ReflectionTestUtils.setField(controller, "ragTopK", 3);
        ReflectionTestUtils.setField(controller, "ragSourceType", "");
        ReflectionTestUtils.setField(controller, "ragMaxContentChars", 500);
        ReflectionTestUtils.setField(controller, "ragTimeoutMs", 1200L);
        ReflectionTestUtils.setField(controller, "ragMetricsEnabled", false);
    }

    @Test
    void buildInstrumentContext_shouldUseLastMentionedCacheWhenQuoteIntentHasNoCandidates() {
        InstrumentCandidate candidate = candidate("TW:XTAI:2330");
        QuoteCandidate quote = quote("TW:XTAI:2330");
        cacheManager.getCache("conversationLastMentioned").put("1:2", "TW:XTAI:2330");

        when(instrumentToolService.searchCandidates("show me quote", 10)).thenReturn(List.of());
        when(instrumentToolService.searchCandidates("TW:XTAI:2330", 1)).thenReturn(List.of(candidate));
        when(quoteToolService.getQuote("TW:XTAI:2330")).thenReturn(quote);

        String context = ReflectionTestUtils.invokeMethod(
                controller,
                "buildInstrumentContext",
                1L,
                2L,
                3L,
                "show me quote");

        assertNotNull(context);
        assertTrue(context.contains("TW:XTAI:2330"));
        assertTrue(context.contains("quote:"));
        assertTrue(context.contains("price: 1000"));
        assertTrue(context.contains("tool_quote_available: true"));
        verify(conversationService, never()).getRecentMessages(1L, 2L, 7);
    }

    @Test
    void resolveCandidatesFromConversation_shouldLookBackRecentMessagesWhenCacheMiss() {
        InstrumentCandidate candidate = candidate("TW:XTAI:2330");
        ConversationMessageView previous = new ConversationMessageView(
                2L,
                ConversationRole.USER,
                "check 2330",
                null,
                null);
        ConversationMessageView current = new ConversationMessageView(
                3L,
                ConversationRole.USER,
                "what about it",
                null,
                null);

        when(conversationService.getRecentMessages(1L, 2L, 7)).thenReturn(List.of(previous, current));
        when(instrumentToolService.searchCandidates("check 2330", 10)).thenReturn(List.of(candidate));

        @SuppressWarnings("unchecked")
        List<InstrumentCandidate> candidates = ReflectionTestUtils.invokeMethod(
                controller,
                "resolveCandidatesFromConversation",
                1L,
                2L,
                3L,
                10);

        assertNotNull(candidates);
        assertEquals(1, candidates.size());
        assertEquals("TW:XTAI:2330", candidates.get(0).symbolKey());
        assertEquals(
                "TW:XTAI:2330",
                cacheManager.getCache("conversationLastMentioned").get("1:2", String.class));
        verify(conversationService).getRecentMessages(1L, 2L, 7);
    }

    @Test
    void buildInstrumentContext_shouldExposeQuoteUnavailableSignalOnToolFailure() {
        InstrumentCandidate candidate = candidate("TW:XTAI:2330");
        when(instrumentToolService.searchCandidates("2330 quote", 10)).thenReturn(List.of(candidate));
        doThrow(new RuntimeException("vendor timeout")).when(quoteToolService).getQuote("TW:XTAI:2330");

        String context = ReflectionTestUtils.invokeMethod(
                controller,
                "buildInstrumentContext",
                1L,
                2L,
                3L,
                "2330 quote");

        assertNotNull(context);
        assertTrue(context.contains("TW:XTAI:2330"));
        assertTrue(context.contains("tool_quote_available: false"));
        assertTrue(context.contains("tool_quote_error: vendor timeout"));
    }

    @Test
    void buildPortfolioContext_shouldIncludeSnapshotValues() {
        when(portfolioService.listChatContexts(eq(1L), any())).thenReturn(List.of(
                new PortfolioChatContext(
                        10L,
                        "Main",
                        "TWD",
                        2L,
                        new java.math.BigDecimal("200.50"),
                        new java.math.BigDecimal("-10.25"),
                        new java.math.BigDecimal("210.75"),
                        LocalDate.of(2026, 2, 10),
                        true)));

        String context = ReflectionTestUtils.invokeMethod(controller, "buildPortfolioContext", 1L);

        assertNotNull(context);
        assertTrue(context.contains("portfolio_context:"));
        assertTrue(context.contains("portfolio_id: 10"));
        assertTrue(context.contains("holdings_count: 2"));
        assertTrue(context.contains("total_value: 200.50"));
        assertTrue(context.contains("valuation_source: snapshot"));
    }

    @Test
    void buildRagContext_shouldIncludeChunksWhenEnabled() {
        ReflectionTestUtils.setField(controller, "ragEnabled", true);

        AiWorkerChunk chunk = new AiWorkerChunk();
        chunk.setContent("TSMC capex guidance remains positive for next year.");
        chunk.setTitle("Research Note");
        chunk.setSourceType("note");
        chunk.setSourceId("doc-1");
        chunk.setChunkIndex(0);
        chunk.setDistance(0.12);

        AiWorkerQueryResponse response = new AiWorkerQueryResponse();
        response.setChunks(List.of(chunk));
        when(ragClient.query(eq(1L), eq("TSMC outlook"), eq(3), eq(null), any(Duration.class)))
                .thenReturn(response);

        String context = ReflectionTestUtils.invokeMethod(controller, "buildRagContext", 1L, "TSMC outlook");

        assertNotNull(context);
        assertTrue(context.contains("rag_context:"));
        assertTrue(context.contains("content: TSMC capex guidance remains positive for next year."));
        assertTrue(context.contains("title: Research Note"));
        assertTrue(context.contains("rag_context_total_chunks: 1"));
    }

    @Test
    void buildDirectQuoteReply_shouldReturnNullWhenDirectReplyDisabled() {
        String reply = ReflectionTestUtils.invokeMethod(
                controller,
                "buildDirectQuoteReply",
                1L,
                2L,
                3L,
                "show quote");

        assertNull(reply);
    }

    @Test
    void sendMessage_shouldMarkAssistantCompletedOnStreamSuccess() {
        when(bufferedSseSessionStore.getOrCreate(anyString())).thenReturn(bufferedSseSession);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(bufferedSseSession).startIfNeeded(any(Runnable.class));
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(1L));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(aiExecutor).execute(any(Runnable.class));

        ConversationMessageView userMessage = new ConversationMessageView(
                11L,
                ConversationRole.USER,
                "hello",
                null,
                null);
        ConversationMessageView pendingAssistant = new ConversationMessageView(
                22L,
                ConversationRole.ASSISTANT,
                "",
                ConversationMessageStatus.PENDING,
                null);
        ConversationMessageView completedAssistant = new ConversationMessageView(
                22L,
                ConversationRole.ASSISTANT,
                "hello world",
                ConversationMessageStatus.COMPLETED,
                null);
        List<Map<String, String>> contextMessages = List.of(
                Map.of("role", "user", "content", "hello"));

        when(conversationService.appendUserMessage(1L, 2L, "hello", "cid-1")).thenReturn(userMessage);
        when(conversationService.appendOrGetAssistantMessage(
                1L,
                2L,
                "",
                ConversationMessageStatus.PENDING,
                "assistant:cid-1")).thenReturn(pendingAssistant);
        when(conversationService.buildContextMessages(1L, 2L, "hello", 11L)).thenReturn(contextMessages);
        when(groqChatClient.streamChat(contextMessages, 1L)).thenReturn(Flux.just("hello", " world"));
        when(conversationService.updateAssistantMessage(
                1L,
                2L,
                22L,
                "hello world",
                ConversationMessageStatus.COMPLETED)).thenReturn(completedAssistant);

        SseEmitter emitter = controller.sendMessage(
                "2",
                ChatMessageRequest.builder()
                        .content("hello")
                        .clientMessageId("cid-1")
                        .build(),
                null);

        assertNotNull(emitter);
        verify(conversationService).appendOrGetAssistantMessage(
                1L,
                2L,
                "",
                ConversationMessageStatus.PENDING,
                "assistant:cid-1");
        verify(conversationService).updateAssistantMessage(
                1L,
                2L,
                22L,
                "hello world",
                ConversationMessageStatus.COMPLETED);
        verify(conversationService, never()).updateAssistantMessage(
                eq(1L),
                eq(2L),
                eq(22L),
                anyString(),
                eq(ConversationMessageStatus.FAILED));
    }

    @Test
    void sendMessage_shouldMarkAssistantFailedOnStreamError() {
        when(bufferedSseSessionStore.getOrCreate(anyString())).thenReturn(bufferedSseSession);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(bufferedSseSession).startIfNeeded(any(Runnable.class));
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(1L));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(aiExecutor).execute(any(Runnable.class));

        ConversationMessageView userMessage = new ConversationMessageView(
                11L,
                ConversationRole.USER,
                "hello",
                null,
                null);
        ConversationMessageView pendingAssistant = new ConversationMessageView(
                22L,
                ConversationRole.ASSISTANT,
                "",
                ConversationMessageStatus.PENDING,
                null);
        ConversationMessageView failedAssistant = new ConversationMessageView(
                22L,
                ConversationRole.ASSISTANT,
                "partial",
                ConversationMessageStatus.FAILED,
                null);
        List<Map<String, String>> contextMessages = List.of(
                Map.of("role", "user", "content", "hello"));

        when(conversationService.appendUserMessage(1L, 2L, "hello", "cid-2")).thenReturn(userMessage);
        when(conversationService.appendOrGetAssistantMessage(
                1L,
                2L,
                "",
                ConversationMessageStatus.PENDING,
                "assistant:cid-2")).thenReturn(pendingAssistant);
        when(conversationService.buildContextMessages(1L, 2L, "hello", 11L)).thenReturn(contextMessages);
        when(groqChatClient.streamChat(contextMessages, 1L))
                .thenReturn(Flux.just("partial").concatWith(Flux.error(new RuntimeException("boom"))));
        when(conversationService.updateAssistantMessage(
                1L,
                2L,
                22L,
                "partial",
                ConversationMessageStatus.FAILED)).thenReturn(failedAssistant);

        SseEmitter emitter = controller.sendMessage(
                "2",
                ChatMessageRequest.builder()
                        .content("hello")
                        .clientMessageId("cid-2")
                        .build(),
                null);

        assertNotNull(emitter);
        verify(conversationService).appendOrGetAssistantMessage(
                1L,
                2L,
                "",
                ConversationMessageStatus.PENDING,
                "assistant:cid-2");
        verify(conversationService).updateAssistantMessage(
                1L,
                2L,
                22L,
                "partial",
                ConversationMessageStatus.FAILED);
        verify(conversationService, never()).updateAssistantMessage(
                eq(1L),
                eq(2L),
                eq(22L),
                anyString(),
                eq(ConversationMessageStatus.COMPLETED));
    }

    private InstrumentCandidate candidate(String symbolKey) {
        return new InstrumentCandidate(symbolKey, "2330", "TSMC", "TW", "XTAI", "STOCK");
    }

    private QuoteCandidate quote(String symbolKey) {
        return new QuoteCandidate(
                symbolKey,
                "2330",
                "1000",
                "5",
                "0.5",
                "995",
                "1005",
                "990",
                "995",
                123456L,
                Instant.parse("2026-02-08T10:00:00Z"));
    }
}

