package tw.bk.appai.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appai.model.ConversationMessageView;
import tw.bk.appai.model.ConversationView;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ConversationRole;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.ConversationEntity;
import tw.bk.apppersistence.entity.ConversationMessageEntity;
import tw.bk.apppersistence.repository.ConversationMessageRepository;
import tw.bk.apppersistence.repository.ConversationRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConversationService {
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a financial analysis assistant. Be concise and factual.";
    private static final String TOOL_ENFORCEMENT_PROMPT = """
            Tool-use policy:
            - If Tool Results contains a `quote:` section, you must use that quote data in your answer.
            - When `tool_quote_available: true`, do not claim you cannot provide real-time/latest price.
            - If `tool_quote_available: false`, explain quote is currently unavailable and include `tool_quote_error` if present.
            - If Tool Results contains `portfolio_context:`, use those holdings and valuation numbers directly.
            - When using portfolio numbers, include `as_of_date` and `valuation_source` to indicate freshness.
            - If Tool Results contains `rag_context:`, ground your answer on those snippets and avoid fabricating citations.
            """;
    private static final int MAX_TITLE_LENGTH = 30;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    @Value("${app.ai.chat.history-limit:20}")
    private int historyLimit;

    @Value("${app.ai.chat.token-budget:6000}")
    private int tokenBudget;

    @Value("${app.ai.chat.prompt-version:v1}")
    private String promptVersion;

    @Transactional
    public ConversationView createConversation(Long userId, String title) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }

        ConversationEntity conversation = new ConversationEntity();
        conversation.setUserId(userId);
        conversation.setTitle(normalizeTitle(title));
        conversation.setPromptVersion(resolvePromptVersion());
        conversation.setPromptSnapshot(DEFAULT_SYSTEM_PROMPT);
        ConversationEntity saved = conversationRepository.save(conversation);

        ConversationMessageEntity systemMessage = new ConversationMessageEntity();
        systemMessage.setConversationId(saved.getId());
        systemMessage.setRole(ConversationRole.SYSTEM.value());
        systemMessage.setContent(DEFAULT_SYSTEM_PROMPT);
        messageRepository.save(systemMessage);

        return toConversationView(saved);
    }

    @Transactional(readOnly = true)
    public List<ConversationView> listConversations(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toConversationView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationView getConversation(Long userId, Long conversationId) {
        return toConversationView(getConversationEntity(userId, conversationId));
    }

    private ConversationEntity getConversationEntity(Long userId, Long conversationId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Conversation not found"));
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageView> getRecentMessages(Long userId, Long conversationId, Integer limit) {
        getConversationEntity(userId, conversationId);
        int size = limit == null ? historyLimit : Math.min(Math.max(limit, 1), 200);
        List<ConversationMessageEntity> recentDesc = messageRepository
                .findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, size));
        Collections.reverse(recentDesc);
        return recentDesc.stream()
                .map(this::toConversationMessageView)
                .toList();
    }

    @Transactional
    public ConversationMessageView appendUserMessage(Long userId, Long conversationId, String content,
            String clientMessageId) {
        ConversationEntity conversation = getConversationEntity(userId, conversationId);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Message content is required");
        }

        String normalizedClientMessageId = normalizeClientMessageId(clientMessageId);
        if (normalizedClientMessageId != null) {
            ConversationMessageEntity existing = messageRepository
                    .findByConversationIdAndClientMessageId(conversationId, normalizedClientMessageId)
                    .orElse(null);
            if (existing != null) {
                return toConversationMessageView(existing);
            }
        }

        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setConversationId(conversationId);
        message.setRole(ConversationRole.USER.value());
        message.setContent(content.trim());
        message.setClientMessageId(normalizedClientMessageId);
        ConversationMessageEntity saved;
        try {
            saved = messageRepository.save(message);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedClientMessageId != null) {
                ConversationMessageEntity existing = messageRepository
                        .findByConversationIdAndClientMessageId(conversationId, normalizedClientMessageId)
                        .orElse(null);
                if (existing != null) {
                    return toConversationMessageView(existing);
                }
            }
            throw new BusinessException(ErrorCode.CONFLICT, "Duplicate message");
        }

        String updatedTitle = deriveTitle(conversation.getTitle(), content);
        if (updatedTitle != null && !updatedTitle.equals(conversation.getTitle())) {
            conversationRepository.updateTitleById(conversationId, updatedTitle);
        } else {
            conversationRepository.touchById(conversationId);
        }
        return toConversationMessageView(saved);
    }

    @Transactional
    public ConversationMessageView appendAssistantMessage(Long userId, Long conversationId, String content,
            ConversationMessageStatus status) {
        return appendOrGetAssistantMessage(userId, conversationId, content, status, null);
    }

    @Transactional
    public ConversationMessageView appendOrGetAssistantMessage(Long userId, Long conversationId, String content,
            ConversationMessageStatus status, String clientMessageId) {
        getConversationEntity(userId, conversationId);
        String normalizedClientMessageId = normalizeClientMessageId(clientMessageId);
        if (normalizedClientMessageId != null) {
            ConversationMessageEntity existing = messageRepository
                    .findByConversationIdAndClientMessageId(conversationId, normalizedClientMessageId)
                    .orElse(null);
            if (existing != null) {
                return toConversationMessageView(existing);
            }
        }

        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setConversationId(conversationId);
        message.setRole(ConversationRole.ASSISTANT.value());
        message.setContent(content == null ? "" : content);
        message.setStatus(status == null ? null : status.name());
        message.setClientMessageId(normalizedClientMessageId);
        ConversationMessageEntity saved;
        try {
            saved = messageRepository.save(message);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedClientMessageId != null) {
                ConversationMessageEntity existing = messageRepository
                        .findByConversationIdAndClientMessageId(conversationId, normalizedClientMessageId)
                        .orElse(null);
                if (existing != null) {
                    return toConversationMessageView(existing);
                }
            }
            throw new BusinessException(ErrorCode.CONFLICT, "Duplicate message");
        }
        conversationRepository.touchById(conversationId);
        return toConversationMessageView(saved);
    }

    @Transactional
    public ConversationMessageView updateAssistantMessage(Long userId, Long conversationId, Long messageId,
            String content, ConversationMessageStatus status) {
        getConversationEntity(userId, conversationId);
        ConversationMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Message not found"));
        if (!conversationId.equals(message.getConversationId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Message not found");
        }
        ConversationRole role = message.getRoleEnum();
        if (role != ConversationRole.ASSISTANT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Message is not assistant");
        }
        message.setContent(content == null ? "" : content);
        message.setStatus(status == null ? null : status.name());
        ConversationMessageEntity saved = messageRepository.save(message);
        conversationRepository.touchById(conversationId);
        return toConversationMessageView(saved);
    }

    @Transactional
    public ConversationView updateTitle(Long userId, Long conversationId, String title) {
        ConversationEntity conversation = getConversationEntity(userId, conversationId);
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Title is required");
        }
        String normalized = title.trim();
        conversationRepository.updateTitleById(conversationId, normalized);
        return toConversationView(conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElse(conversation));
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> buildContextMessages(Long userId, Long conversationId, String newUserContent,
            Long excludeMessageId) {
        getConversationEntity(userId, conversationId);
        ConversationMessageEntity systemMessage = messageRepository
                .findFirstByConversationIdAndRoleOrderByIdAsc(conversationId, ConversationRole.SYSTEM.value())
                .orElse(null);

        List<ConversationMessageEntity> recentDesc = messageRepository
                .findByConversationIdAndRoleNotOrderByIdDesc(conversationId, ConversationRole.SYSTEM.value(),
                        PageRequest.of(0, historyLimit));

        int budget = Math.max(0, tokenBudget - estimateTokens(newUserContent));
        int used = 0;
        List<ConversationMessageEntity> selected = new ArrayList<>();

        for (ConversationMessageEntity msg : recentDesc) {
            if (excludeMessageId != null && excludeMessageId.equals(msg.getId())) {
                continue;
            }
            int tokens = estimateTokens(msg.getContent());
            if (used + tokens > budget && !selected.isEmpty()) {
                break;
            }
            selected.add(msg);
            used += tokens;
        }
        Collections.reverse(selected);

        List<Map<String, String>> messages = new ArrayList<>();
        String systemPrompt = systemMessage != null ? systemMessage.getContent() : DEFAULT_SYSTEM_PROMPT;
        messages.add(Map.of("role", ConversationRole.SYSTEM.value(), "content", systemPrompt));

        for (ConversationMessageEntity msg : selected) {
            String role = msg.getRole();
            ConversationRole normalized = ConversationRole.from(role);
            messages.add(Map.of("role",
                    normalized != null ? normalized.value() : role.toLowerCase(Locale.ROOT),
                    "content", msg.getContent()));
        }
        messages.add(Map.of("role", ConversationRole.USER.value(), "content", newUserContent));
        return messages;
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> buildContextMessagesWithTool(Long userId, Long conversationId,
            String newUserContent, Long excludeMessageId, String toolContext) {
        List<Map<String, String>> messages = buildContextMessages(userId, conversationId, newUserContent,
                excludeMessageId);
        if (toolContext == null || toolContext.isBlank() || messages.isEmpty()) {
            return messages;
        }
        List<Map<String, String>> merged = new ArrayList<>();
        Map<String, String> system = messages.get(0);
        String systemContent = system.get("content");
        String enhanced = (systemContent == null ? "" : systemContent)
                + "\n\n--- Tool Policy ---\n"
                + TOOL_ENFORCEMENT_PROMPT
                + "\n\n--- Tool Results ---\n"
                + toolContext.trim();
        merged.add(Map.of("role", ConversationRole.SYSTEM.value(), "content", enhanced));
        for (int i = 1; i < messages.size(); i++) {
            merged.add(messages.get(i));
        }
        return merged;
    }

    private ConversationView toConversationView(ConversationEntity entity) {
        return new ConversationView(
                entity.getId(),
                entity.getTitle(),
                entity.getPromptVersion(),
                entity.getPromptSnapshot(),
                entity.getSummary(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String resolvePromptVersion() {
        if (promptVersion == null || promptVersion.isBlank()) {
            return "v1";
        }
        return promptVersion.trim();
    }

    private ConversationMessageView toConversationMessageView(ConversationMessageEntity entity) {
        return new ConversationMessageView(
                entity.getId(),
                entity.getRoleEnum(),
                entity.getContent(),
                entity.getStatusEnum(),
                entity.getCreatedAt());
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, content.length() / 4);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }

    private String deriveTitle(String currentTitle, String content) {
        if (content == null || content.isBlank()) {
            return currentTitle;
        }
        if (currentTitle != null && !currentTitle.isBlank() && !"New Chat".equalsIgnoreCase(currentTitle)) {
            return currentTitle;
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            return trimmed.substring(0, MAX_TITLE_LENGTH);
        }
        return trimmed;
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return null;
        }
        return clientMessageId.trim();
    }
}
