package tw.bk.appapi.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.ConversationMessageView;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.skill.ChatSkillBatchResult;
import tw.bk.appai.skill.ChatSkillExecutor;
import tw.bk.appapi.ai.security.CurrentUserRoleProvider;
import tw.bk.appapi.ai.dto.ChatMessageRequest;
import tw.bk.appapi.sse.BufferedSseSession;
import tw.bk.appapi.sse.BufferedSseSessionStore;
import tw.bk.appcommon.enums.ConversationMessageStatus;
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
    private CurrentUserRoleProvider currentUserRoleProvider;
    @Mock
    private Executor aiExecutor;
    @Mock
    private BufferedSseSessionStore bufferedSseSessionStore;
    @Mock
    private BufferedSseSession bufferedSseSession;
    @Mock
    private ChatSkillExecutor chatSkillExecutor;

    private AiConversationController controller;

    @BeforeEach
    void setUp() {
        controller = new AiConversationController(
                conversationService,
                groqChatClient,
                currentUserProvider,
                currentUserRoleProvider,
                aiExecutor,
                bufferedSseSessionStore,
                chatSkillExecutor);
    }

    @Test
    void deleteConversation_shouldCallSoftDeleteService() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(1L));

        var result = controller.deleteConversation("2");

        assertTrue(result.isSuccess());
        verify(conversationService).softDeleteConversation(1L, 2L);
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

        when(chatSkillExecutor.executeAll(any())).thenReturn(new ChatSkillBatchResult(null, List.of()));
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

        when(chatSkillExecutor.executeAll(any())).thenReturn(new ChatSkillBatchResult(null, List.of()));
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

    @Test
    void sendMessage_shouldUseToolContextMessagesWhenSkillHits() {
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
                "ok",
                ConversationMessageStatus.COMPLETED,
                null);

        List<Map<String, String>> toolMessages = List.of(Map.of("role", "system", "content", "tool"));

        when(chatSkillExecutor.executeAll(any())).thenReturn(new ChatSkillBatchResult("quote:\n- a", List.of()));
        when(conversationService.appendUserMessage(1L, 2L, "hello", "cid-3")).thenReturn(userMessage);
        when(conversationService.appendOrGetAssistantMessage(
                1L,
                2L,
                "",
                ConversationMessageStatus.PENDING,
                "assistant:cid-3")).thenReturn(pendingAssistant);
        when(conversationService.buildContextMessagesWithTool(1L, 2L, "hello", 11L, "quote:\n- a"))
                .thenReturn(toolMessages);
        when(groqChatClient.streamChat(toolMessages, 1L)).thenReturn(Flux.just("ok"));
        when(conversationService.updateAssistantMessage(1L, 2L, 22L, "ok", ConversationMessageStatus.COMPLETED))
                .thenReturn(completedAssistant);

        SseEmitter emitter = controller.sendMessage(
                "2",
                ChatMessageRequest.builder().content("hello").clientMessageId("cid-3").build(),
                null);

        assertNotNull(emitter);
        verify(conversationService).buildContextMessagesWithTool(1L, 2L, "hello", 11L, "quote:\n- a");
        verify(conversationService, never()).buildContextMessages(1L, 2L, "hello", 11L);
    }
}
