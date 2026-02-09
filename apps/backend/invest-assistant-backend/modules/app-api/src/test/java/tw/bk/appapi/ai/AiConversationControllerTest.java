package tw.bk.appapi.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.util.ReflectionTestUtils;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.ConversationMessageView;
import tw.bk.appai.model.InstrumentCandidate;
import tw.bk.appai.model.QuoteCandidate;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.service.AiInstrumentToolService;
import tw.bk.appai.service.AiQuoteToolService;
import tw.bk.appcommon.enums.ConversationRole;
import tw.bk.appcommon.security.CurrentUserProvider;

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
                cacheManager);
        ReflectionTestUtils.setField(controller, "instrumentSearchEnabled", true);
        ReflectionTestUtils.setField(controller, "instrumentSearchLimit", 10);
        ReflectionTestUtils.setField(controller, "quoteSearchEnabled", true);
        ReflectionTestUtils.setField(controller, "quoteSearchKeywordsEn", "quote,price");
        ReflectionTestUtils.setField(controller, "pronounLookbackLimit", 5);
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
