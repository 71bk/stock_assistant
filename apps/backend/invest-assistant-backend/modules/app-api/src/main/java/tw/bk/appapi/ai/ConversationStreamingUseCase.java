package tw.bk.appapi.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import tw.bk.appai.client.GroqChatClient;
import tw.bk.appai.model.ConversationMessageView;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.skill.ChatSkillBatchResult;
import tw.bk.appai.skill.ChatSkillContext;
import tw.bk.appai.skill.ChatSkillExecutor;
import tw.bk.appapi.ai.security.CurrentUserRoleProvider;
import tw.bk.appapi.sse.BufferedSseSession;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

/**
 * 聊天訊息的串流回覆流程：寫入 user/assistant 訊息、執行 chat skill、串流 Groq delta，
 * 並在完成/失敗時更新 assistant 訊息狀態與輸出 SSE 事件。
 *
 * <p>從 {@code AiConversationController} 抽出，讓 controller 只負責建立 SSE 連線、
 * 冪等 session 與 async 排程；實際的對話/串流/補償邏輯收斂於此。
 * 事件透過 {@link BufferedSseSession} 輸出（支援斷線重連的緩衝）。
 */
@Slf4j
class ConversationStreamingUseCase {

    private final AiConversationService conversationService;
    private final GroqChatClient groqChatClient;
    private final CurrentUserRoleProvider currentUserRoleProvider;
    private final ChatSkillExecutor chatSkillExecutor;

    ConversationStreamingUseCase(AiConversationService conversationService,
            GroqChatClient groqChatClient,
            CurrentUserRoleProvider currentUserRoleProvider,
            ChatSkillExecutor chatSkillExecutor) {
        this.conversationService = conversationService;
        this.groqChatClient = groqChatClient;
        this.currentUserRoleProvider = currentUserRoleProvider;
        this.chatSkillExecutor = chatSkillExecutor;
    }

    /**
     * 串流一則 assistant 回覆。為同步阻塞流程，由呼叫端在 async 執行緒中執行。
     */
    void stream(BufferedSseSession session,
            Long userId,
            Long conversationId,
            String requestId,
            String content,
            String effectiveClientMessageId,
            String assistantClientMessageId) {
        AtomicReference<ConversationMessageView> assistantMessageRef = new AtomicReference<>();
        StringBuilder buffer = new StringBuilder();
        try {
            ConversationMessageView userMessage = conversationService.appendUserMessage(
                    userId, conversationId, content, effectiveClientMessageId);
            ConversationMessageView assistantMessage = conversationService.appendOrGetAssistantMessage(
                    userId, conversationId, "", ConversationMessageStatus.PENDING, assistantClientMessageId);
            assistantMessageRef.set(assistantMessage);
            sendMeta(session, requestId, conversationId, userMessage.id(), assistantMessage.id());

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

            ChatSkillContext skillContext = new ChatSkillContext(
                    userId,
                    conversationId,
                    userMessage.id(),
                    content,
                    currentUserRoleProvider.getCurrentRole());
            ChatSkillBatchResult skillBatchResult = chatSkillExecutor.executeAll(skillContext);
            logSkillBatch(skillBatchResult);
            String toolContext = skillBatchResult != null ? skillBatchResult.mergedContext() : null;
            List<Map<String, String>> messages = toolContext == null
                    ? conversationService.buildContextMessages(userId, conversationId, content, userMessage.id())
                    : conversationService.buildContextMessagesWithTool(userId, conversationId, content,
                            userMessage.id(), toolContext);
            AtomicBoolean failed = new AtomicBoolean(false);

            groqChatClient.streamChat(messages, userId, "chat")
                    .doOnNext(delta -> {
                        buffer.append(delta);
                        sendDelta(session, delta);
                    })
                    .doOnError(ex -> {
                        failed.set(true);
                        try {
                            ConversationMessageView failedAssistant = conversationService.updateAssistantMessage(
                                    userId,
                                    conversationId,
                                    assistantMessage.id(),
                                    buffer.toString(),
                                    ConversationMessageStatus.FAILED);
                            assistantMessageRef.set(failedAssistant);
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
                    .onErrorResume(ex -> Flux.empty())
                    .doOnComplete(() -> {
                        if (failed.get()) {
                            return;
                        }
                        try {
                            ConversationMessageView assistant = conversationService.updateAssistantMessage(
                                    userId,
                                    conversationId,
                                    assistantMessage.id(),
                                    buffer.toString(),
                                    ConversationMessageStatus.COMPLETED);
                            assistantMessageRef.set(assistant);
                            sendDone(session, assistant.id());
                        } catch (Exception ex) {
                            log.error("Failed to save assistant message", ex);
                        }
                    })
                    .blockLast();
        } catch (BusinessException ex) {
            markAssistantMessageFailed(userId, conversationId, assistantMessageRef.get(), buffer.toString());
            sendError(session, ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Chat streaming failed", ex);
            markAssistantMessageFailed(userId, conversationId, assistantMessageRef.get(), buffer.toString());
            sendError(session, ErrorCode.INTERNAL_ERROR, "Chat streaming failed");
        }
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

    private void markAssistantMessageFailed(
            Long userId,
            Long conversationId,
            ConversationMessageView assistantMessage,
            String content) {
        if (assistantMessage == null
                || assistantMessage.status() == ConversationMessageStatus.COMPLETED
                || assistantMessage.status() == ConversationMessageStatus.FAILED) {
            return;
        }
        try {
            conversationService.updateAssistantMessage(
                    userId,
                    conversationId,
                    assistantMessage.id(),
                    content,
                    ConversationMessageStatus.FAILED);
        } catch (Exception saveEx) {
            log.error("Failed to mark assistant message as FAILED", saveEx);
        }
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
}
