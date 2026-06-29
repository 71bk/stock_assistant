package tw.bk.appapi.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
import tw.bk.appai.model.ConversationView;
import tw.bk.appai.service.AiConversationService;
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
import tw.bk.appapi.web.CurrentUser;
import tw.bk.appapi.web.IdParser;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;

@RestController
@RequestMapping("/ai/conversations")
@Tag(name = "AI Conversations", description = "Chat conversation APIs")
@Slf4j
public class AiConversationController {
    private final AiConversationService conversationService;
    private final CurrentUserProvider currentUserProvider;
    private final Executor aiSseExecutor;
    private final BufferedSseSessionStore bufferedSseSessionStore;
    private final ConversationStreamingUseCase streamingUseCase;

    public AiConversationController(AiConversationService conversationService,
            GroqChatClient groqChatClient,
            CurrentUserProvider currentUserProvider,
            CurrentUserRoleProvider currentUserRoleProvider,
            @Qualifier("aiSseExecutor") Executor aiSseExecutor,
            BufferedSseSessionStore bufferedSseSessionStore,
            ChatSkillExecutor chatSkillExecutor) {
        this.conversationService = conversationService;
        this.currentUserProvider = currentUserProvider;
        this.aiSseExecutor = aiSseExecutor;
        this.bufferedSseSessionStore = bufferedSseSessionStore;
        this.streamingUseCase = new ConversationStreamingUseCase(
                conversationService,
                groqChatClient,
                currentUserRoleProvider,
                chatSkillExecutor);
    }

    @PostMapping
    @Operation(summary = "Create conversation")
    public Result<ConversationSummaryResponse> createConversation(@RequestBody CreateConversationRequest request) {
        Long userId = CurrentUser.require(currentUserProvider);
        ConversationView conversation = conversationService.createConversation(userId,
                request != null ? request.getTitle() : null);
        return Result.ok(ConversationSummaryResponse.from(conversation));
    }

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Update conversation title")
    public Result<ConversationSummaryResponse> updateConversation(@PathVariable String conversationId,
            @RequestBody UpdateConversationRequest request) {
        Long userId = CurrentUser.require(currentUserProvider);
        Long id = IdParser.parseId(conversationId);
        ConversationView conversation = conversationService.updateTitle(userId, id,
                request != null ? request.getTitle() : null);
        return Result.ok(ConversationSummaryResponse.from(conversation));
    }

    @GetMapping
    @Operation(summary = "List conversations")
    public Result<List<ConversationSummaryResponse>> listConversations() {
        Long userId = CurrentUser.require(currentUserProvider);
        List<ConversationSummaryResponse> items = conversationService.listConversations(userId).stream()
                .map(ConversationSummaryResponse::from)
                .toList();
        return Result.ok(items);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get conversation detail")
    public Result<ConversationDetailResponse> getConversation(@PathVariable String conversationId,
            @RequestParam(required = false) Integer limit) {
        Long userId = CurrentUser.require(currentUserProvider);
        Long id = IdParser.parseId(conversationId);
        ConversationView conversation = conversationService.getConversation(userId, id);
        List<ConversationMessageResponse> messages = conversationService.getRecentMessages(userId, id, limit).stream()
                .map(ConversationMessageResponse::from)
                .toList();
        return Result.ok(ConversationDetailResponse.of(conversation, messages));
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Delete conversation (soft delete)")
    public Result<Void> deleteConversation(@PathVariable String conversationId) {
        Long userId = CurrentUser.require(currentUserProvider);
        Long id = IdParser.parseId(conversationId);
        conversationService.softDeleteConversation(userId, id);
        return Result.ok();
    }

    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send chat message (SSE)")
    public SseEmitter sendMessage(@PathVariable String conversationId,
            @RequestBody ChatMessageRequest request,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        Long userId = CurrentUser.require(currentUserProvider);
        Long id = IdParser.parseId(conversationId);
        String content = request != null ? request.getContent() : null;
        String clientMessageId = request != null ? request.getClientMessageId() : null;
        String requestId = "c-" + UUID.randomUUID();
        String effectiveClientMessageId = resolveClientMessageId(clientMessageId, requestId);
        String assistantClientMessageId = "assistant:" + effectiveClientMessageId;
        String sessionKey = buildSessionKey(userId, id, effectiveClientMessageId);
        BufferedSseSession session = bufferedSseSessionStore.getOrCreate(sessionKey);
        session.attachEmitter(emitter, lastEventId);

        session.startIfNeeded(() -> CompletableFuture.runAsync(
                () -> streamingUseCase.stream(
                        session, userId, id, requestId, content, effectiveClientMessageId, assistantClientMessageId),
                aiSseExecutor));

        return emitter;
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
}
