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
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.ConversationEntity;
import tw.bk.apppersistence.entity.ConversationMessageEntity;
import tw.bk.apppersistence.repository.ConversationMessageRepository;
import tw.bk.apppersistence.repository.ConversationRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConversationService {
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a financial analysis assistant. Be concise and factual.";
    private static final int MAX_TITLE_LENGTH = 30;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    @Value("${app.ai.chat.history-limit:20}")
    private int historyLimit;

    @Value("${app.ai.chat.token-budget:6000}")
    private int tokenBudget;

    @Transactional
    public ConversationEntity createConversation(Long userId, String title) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }

        ConversationEntity conversation = new ConversationEntity();
        conversation.setUserId(userId);
        conversation.setTitle(normalizeTitle(title));
        ConversationEntity saved = conversationRepository.save(conversation);

        ConversationMessageEntity systemMessage = new ConversationMessageEntity();
        systemMessage.setConversationId(saved.getId());
        systemMessage.setRole(ROLE_SYSTEM);
        systemMessage.setContent(DEFAULT_SYSTEM_PROMPT);
        messageRepository.save(systemMessage);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ConversationEntity> listConversations(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public ConversationEntity getConversation(Long userId, Long conversationId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Conversation not found"));
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageEntity> getRecentMessages(Long userId, Long conversationId, Integer limit) {
        getConversation(userId, conversationId);
        int size = limit == null ? historyLimit : Math.min(Math.max(limit, 1), 200);
        List<ConversationMessageEntity> recentDesc = messageRepository
                .findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, size));
        Collections.reverse(recentDesc);
        return recentDesc;
    }

    @Transactional
    public ConversationMessageEntity appendUserMessage(Long userId, Long conversationId, String content,
            String clientMessageId) {
        ConversationEntity conversation = getConversation(userId, conversationId);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Message content is required");
        }

        if (clientMessageId != null && !clientMessageId.isBlank()) {
            ConversationMessageEntity existing = messageRepository
                    .findByConversationIdAndClientMessageId(conversationId, clientMessageId)
                    .orElse(null);
            if (existing != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "Duplicate message");
            }
        }

        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setConversationId(conversationId);
        message.setRole(ROLE_USER);
        message.setContent(content.trim());
        message.setClientMessageId(clientMessageId);
        ConversationMessageEntity saved;
        try {
            saved = messageRepository.save(message);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "Duplicate message");
        }

        String updatedTitle = deriveTitle(conversation.getTitle(), content);
        if (updatedTitle != null && !updatedTitle.equals(conversation.getTitle())) {
            conversationRepository.updateTitleById(conversationId, updatedTitle);
        } else {
            conversationRepository.touchById(conversationId);
        }
        return saved;
    }

    @Transactional
    public ConversationMessageEntity appendAssistantMessage(Long userId, Long conversationId, String content,
            String status) {
        getConversation(userId, conversationId);
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setConversationId(conversationId);
        message.setRole(ROLE_ASSISTANT);
        message.setContent(content == null ? "" : content);
        message.setStatus(status);
        ConversationMessageEntity saved = messageRepository.save(message);
        conversationRepository.touchById(conversationId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> buildContextMessages(Long userId, Long conversationId, String newUserContent,
            Long excludeMessageId) {
        getConversation(userId, conversationId);
        ConversationMessageEntity systemMessage = messageRepository
                .findFirstByConversationIdAndRoleOrderByIdAsc(conversationId, ROLE_SYSTEM)
                .orElse(null);

        List<ConversationMessageEntity> recentDesc = messageRepository
                .findByConversationIdAndRoleNotOrderByIdDesc(conversationId, ROLE_SYSTEM,
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
        messages.add(Map.of("role", ROLE_SYSTEM, "content", systemPrompt));

        for (ConversationMessageEntity msg : selected) {
            messages.add(Map.of("role", msg.getRole().toLowerCase(Locale.ROOT), "content", msg.getContent()));
        }
        messages.add(Map.of("role", ROLE_USER, "content", newUserContent));
        return messages;
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
}
