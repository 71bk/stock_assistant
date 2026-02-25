package tw.bk.appai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tw.bk.appai.model.ConversationMessageView;
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
        ReflectionTestUtils.setField(service, "softDeleteRetentionDays", 30);
    }

    @Test
    void buildContextMessagesWithTool_shouldInjectToolPolicyForDeterministicQuoteUsage() {
        stubActiveConversation();
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

    @Test
    void appendUserMessage_shouldReturnExistingMessageForDuplicateClientMessageId() {
        stubActiveConversation();
        ConversationMessageEntity existing = new ConversationMessageEntity();
        existing.setId(99L);
        existing.setConversationId(2L);
        existing.setRole("user");
        existing.setContent("hello");

        when(messageRepository.findByConversationIdAndClientMessageId(2L, "client-1"))
                .thenReturn(Optional.of(existing));

        ConversationMessageView view = service.appendUserMessage(1L, 2L, "new message", "client-1");

        assertEquals(99L, view.id());
        assertEquals("hello", view.content());
        verify(messageRepository, never()).save(any(ConversationMessageEntity.class));
    }

    @Test
    void buildContextMessages_shouldKeepNewestMessagesWithinTokenBudget() {
        stubActiveConversation();
        ReflectionTestUtils.setField(service, "tokenBudget", 8);

        ConversationMessageEntity system = new ConversationMessageEntity();
        system.setRole("system");
        system.setContent("system prompt");

        ConversationMessageEntity latest = new ConversationMessageEntity();
        latest.setId(30L);
        latest.setConversationId(2L);
        latest.setRole("assistant");
        latest.setContent("CCCCCCCCCCCC");

        ConversationMessageEntity middle = new ConversationMessageEntity();
        middle.setId(20L);
        middle.setConversationId(2L);
        middle.setRole("user");
        middle.setContent("BBBBBBBBBBBB");

        ConversationMessageEntity oldest = new ConversationMessageEntity();
        oldest.setId(10L);
        oldest.setConversationId(2L);
        oldest.setRole("assistant");
        oldest.setContent("AAAAAAAAAAAA");

        when(messageRepository.findFirstByConversationIdAndRoleOrderByIdAsc(2L, "system"))
                .thenReturn(Optional.of(system));
        when(messageRepository.findByConversationIdAndRoleNotOrderByIdDesc(eq(2L), eq("system"), any()))
                .thenReturn(List.of(latest, middle, oldest));

        List<Map<String, String>> messages = service.buildContextMessages(1L, 2L, "DDDDDDDD", null);

        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertEquals("BBBBBBBBBBBB", messages.get(1).get("content"));
        assertEquals("CCCCCCCCCCCC", messages.get(2).get("content"));
        assertEquals("DDDDDDDD", messages.get(3).get("content"));
    }

    @Test
    void softDeleteConversation_shouldSetDeletedAtAndPurgeAfterAt() {
        stubActiveConversation();
        service.softDeleteConversation(1L, 2L);

        ArgumentCaptor<ConversationEntity> captor = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(captor.capture());
        ConversationEntity saved = captor.getValue();

        assertNotNull(saved.getDeletedAt());
        assertNotNull(saved.getPurgeAfterAt());
        assertEquals(saved.getDeletedAt().plusDays(30), saved.getPurgeAfterAt());
    }

    @Test
    void hardDeleteConversationAsAdmin_shouldDeleteConversation() {
        ConversationEntity target = new ConversationEntity();
        target.setId(8L);
        when(conversationRepository.findById(8L)).thenReturn(Optional.of(target));

        service.hardDeleteConversationAsAdmin(8L);

        verify(conversationRepository).delete(target);
    }

    @Test
    void purgeExpiredConversations_shouldDeleteExpiredBatch() {
        ConversationEntity first = new ConversationEntity();
        first.setId(10L);
        first.setDeletedAt(OffsetDateTime.now().minusDays(10));
        first.setPurgeAfterAt(OffsetDateTime.now().minusDays(1));

        ConversationEntity second = new ConversationEntity();
        second.setId(11L);
        second.setDeletedAt(OffsetDateTime.now().minusDays(7));
        second.setPurgeAfterAt(OffsetDateTime.now().minusHours(2));

        when(conversationRepository.findByDeletedAtIsNotNullAndPurgeAfterAtLessThanEqualOrderByPurgeAfterAtAsc(
                any(OffsetDateTime.class),
                any(Pageable.class))).thenReturn(List.of(first, second));

        int purged = service.purgeExpiredConversations(200);

        assertEquals(2, purged);
        verify(conversationRepository).deleteAllInBatch(List.of(first, second));
    }

    private void stubActiveConversation() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(2L);
        conversation.setUserId(1L);
        when(conversationRepository.findByIdAndUserIdAndDeletedAtIsNull(2L, 1L)).thenReturn(Optional.of(conversation));
    }
}
