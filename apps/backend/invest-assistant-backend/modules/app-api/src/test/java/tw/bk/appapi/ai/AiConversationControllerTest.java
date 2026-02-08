package tw.bk.appapi.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import tw.bk.appai.model.InstrumentCandidate;
import tw.bk.appai.model.QuoteCandidate;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.service.AiInstrumentToolService;
import tw.bk.appai.service.AiQuoteToolService;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.apppersistence.entity.ConversationMessageEntity;

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
        ReflectionTestUtils.setField(controller, "quoteSearchKeywords", "價格,股價,多少,現價,收盤,漲跌,報價,quote,price");
        ReflectionTestUtils.setField(controller, "pronounLookbackLimit", 5);
    }

    @Test
    void buildInstrumentContext_shouldUseLastMentionedCacheWhenPronounMessageHasNoCandidates() {
        InstrumentCandidate candidate = candidate("TW:XTAI:2330");
        QuoteCandidate quote = quote("TW:XTAI:2330");
        cacheManager.getCache("conversationLastMentioned").put("1:2", "TW:XTAI:2330");

        when(instrumentToolService.searchCandidates("那現在這隻股票價格多少", 10)).thenReturn(List.of());
        when(instrumentToolService.searchCandidates("TW:XTAI:2330", 1)).thenReturn(List.of(candidate));
        when(quoteToolService.getQuote("TW:XTAI:2330")).thenReturn(quote);

        String context = ReflectionTestUtils.invokeMethod(
                controller,
                "buildInstrumentContext",
                1L,
                2L,
                3L,
                "那現在這隻股票價格多少");

        assertNotNull(context);
        assertTrue(context.contains("TW:XTAI:2330"));
        assertTrue(context.contains("quote:"));
        assertTrue(context.contains("price: 1000"));
        verify(conversationService, never()).getRecentMessages(1L, 2L, 7);
    }

    @Test
    void buildInstrumentContext_shouldLookBackRecentMessagesWhenCacheMiss() {
        InstrumentCandidate candidate = candidate("TW:XTAI:2330");
        QuoteCandidate quote = quote("TW:XTAI:2330");
        ConversationMessageEntity previous = new ConversationMessageEntity();
        previous.setId(2L);
        previous.setRole("user");
        previous.setContent("台積電現在多少");
        ConversationMessageEntity current = new ConversationMessageEntity();
        current.setId(3L);
        current.setRole("user");
        current.setContent("那現在這隻股票價格多少");

        when(instrumentToolService.searchCandidates("那現在這隻股票價格多少", 10)).thenReturn(List.of());
        when(conversationService.getRecentMessages(1L, 2L, 7)).thenReturn(List.of(previous, current));
        when(instrumentToolService.searchCandidates("台積電現在多少", 10)).thenReturn(List.of(candidate));
        when(quoteToolService.getQuote("TW:XTAI:2330")).thenReturn(quote);

        String context = ReflectionTestUtils.invokeMethod(
                controller,
                "buildInstrumentContext",
                1L,
                2L,
                3L,
                "那現在這隻股票價格多少");

        assertNotNull(context);
        assertTrue(context.contains("TW:XTAI:2330"));
        assertEquals(
                "TW:XTAI:2330",
                cacheManager.getCache("conversationLastMentioned").get("1:2", String.class));
        verify(conversationService).getRecentMessages(1L, 2L, 7);
    }

    private InstrumentCandidate candidate(String symbolKey) {
        return new InstrumentCandidate(symbolKey, "2330", "台積電", "TW", "XTAI", "STOCK");
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
