package tw.bk.appai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tw.bk.apppersistence.entity.ConversationEntity;
import tw.bk.apppersistence.entity.ConversationMessageEntity;
import tw.bk.apppersistence.repository.ConversationMessageRepository;
import tw.bk.apppersistence.repository.ConversationRepository;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMessageRepository messageRepository;

    private AiConversationService service;

    @BeforeEach
    void setUp() {
        service = new AiConversationService(conversationRepository, messageRepository);
        ReflectionTestUtils.setField(service, "historyLimit", 20);
        ReflectionTestUtils.setField(service, "tokenBudget", 6000);

        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(2L);
        conversation.setUserId(1L);
        when(conversationRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(conversation));
    }

    @Test
    void buildContextMessagesWithTool_shouldInjectToolPolicyForDeterministicQuoteUsage() {
        ConversationMessageEntity system = new ConversationMessageEntity();
        system.setRole("system");
        system.setContent("You are a financial analysis assistant. Be concise and factual.");
        when(messageRepository.findFirstByConversationIdAndRoleOrderByIdAsc(2L, "system"))
                .thenReturn(Optional.of(system));
        when(messageRepository.findByConversationIdAndRoleNotOrderByIdDesc(eq(2L), eq("system"), any()))
                .thenReturn(List.of());

        String toolContext = """
                instrument_candidates:
                - 2330 台積電 (TW:XTAI:2330) type=STOCK
                quote:
                - symbol_key: TW:XTAI:2330
                  price: 1000
                tool_quote_available: true
                """;

        List<Map<String, String>> messages = service.buildContextMessagesWithTool(
                1L, 2L, "台積電現在多少", null, toolContext);

        assertEquals(2, messages.size());
        String enhancedSystem = messages.get(0).get("content");
        assertTrue(enhancedSystem.contains("--- Tool Policy ---"));
        assertTrue(enhancedSystem.contains("do not claim you cannot provide real-time/latest price"));
        assertTrue(enhancedSystem.contains("tool_quote_available: true"));
    }
}
