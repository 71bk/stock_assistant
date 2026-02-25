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
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.skill.ChatSkillBatchResult;
import tw.bk.appai.skill.ChatSkillContext;
import tw.bk.appai.skill.ChatSkillExecutor;
import tw.bk.appapi.ai.security.CurrentUserRoleProvider;
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
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;

@RestController
@RequestMapping("/ai/conversations")
@Tag(name = "AI Conversations", description = "Chat conversation APIs")
@Slf4j
public class AiConversationController {
    private final AiConversationService conversationService;
    private final GroqChatClient groqChatClient;
    private final CurrentUserProvider currentUserProvider;
    private final CurrentUserRoleProvider currentUserRoleProvider;
    private final Executor aiExecutor;
    private final BufferedSseSessionStore bufferedSseSessionStore;
    private final ChatSkillExecutor chatSkillExecutor;

    public AiConversationController(AiConversationService conversationService,
            GroqChatClient groqChatClient,
            CurrentUserProvider currentUserProvider,
            CurrentUserRoleProvider currentUserRoleProvider,
            @Qualifier("aiExecutor") Executor aiExecutor,
            BufferedSseSessionStore bufferedSseSessionStore,
            ChatSkillExecutor chatSkillExecutor) {
        this.conversationService = conversationService;
        this.groqChatClient = groqChatClient;
        this.currentUserProvider = currentUserProvider;
        this.currentUserRoleProvider = currentUserRoleProvider;
        this.aiExecutor = aiExecutor;
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

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Delete conversation (soft delete)")
    public Result<Void> deleteConversation(@PathVariable String conversationId) {
        Long userId = requireUserId();
        Long id = parseId(conversationId);
        conversationService.softDeleteConversation(userId, id);
        return Result.ok();
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

                ChatSkillContext skillContext = new ChatSkillContext(
                        userId,
                        id,
                        userMessage.id(),
                        content,
                        currentUserRoleProvider.getCurrentRole());
                ChatSkillBatchResult skillBatchResult = chatSkillExecutor.executeAll(skillContext);
                logSkillBatch(skillBatchResult);
                String toolContext = skillBatchResult != null ? skillBatchResult.mergedContext() : null;
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

    private String resolveClientMessageId(String clientMessageId, String fallbackRequestId) {
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            return clientMessageId.trim();
        }
        return fallbackRequestId;
    }

    private String buildSessionKey(Long userId, Long conversationId, String clientMessageId) {
        return userId + ":" + conversationId + ":" + clientMessageId;
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
