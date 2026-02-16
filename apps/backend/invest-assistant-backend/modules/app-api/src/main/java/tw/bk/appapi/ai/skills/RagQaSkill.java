package tw.bk.appapi.ai.skills;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import tw.bk.appai.skill.ChatSkill;
import tw.bk.appai.skill.ChatSkillContext;
import tw.bk.appai.skill.ChatSkillErrorCode;
import tw.bk.appai.skill.ChatSkillResult;
import tw.bk.appai.skill.ChatSkillSpec;
import tw.bk.appcommon.enums.UserRole;
import tw.bk.apprag.client.AiWorkerChunk;
import tw.bk.apprag.client.AiWorkerQueryResponse;
import tw.bk.apprag.client.AiWorkerRagClient;

@Service
@Order(30)
public class RagQaSkill implements ChatSkill {
    private final AiWorkerRagClient ragClient;

    @Value("${app.ai.chat.skills.rag-qa.enabled:true}")
    private boolean skillEnabled;

    @Value("${app.ai.chat.skills.rag-qa.timeout-ms:1200}")
    private long skillTimeoutMs;

    @Value("${app.ai.chat.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${app.ai.chat.rag.top-k:3}")
    private int ragTopK;

    @Value("${app.ai.chat.rag.source-type:}")
    private String ragSourceType;

    @Value("${app.ai.chat.rag.max-content-chars:500}")
    private int ragMaxContentChars;

    @Value("${app.ai.chat.rag.timeout-ms:1200}")
    private long ragTimeoutMs;

    public RagQaSkill(AiWorkerRagClient ragClient) {
        this.ragClient = ragClient;
    }

    @Override
    public ChatSkillSpec spec() {
        return new ChatSkillSpec(
                "rag-qa-skill",
                "1.0.0",
                UserRole.USER,
                skillTimeoutMs,
                skillEnabled,
                "{\"type\":\"object\",\"required\":[\"userId\",\"content\"]}",
                "{\"type\":\"object\",\"description\":\"rag_context block\"}");
    }

    @Override
    public boolean supports(ChatSkillContext context) {
        if (!skillEnabled || !ragEnabled || context == null) {
            return false;
        }
        return context.userId() != null && context.content() != null && !context.content().isBlank();
    }

    @Override
    public ChatSkillResult execute(ChatSkillContext context) {
        long started = System.currentTimeMillis();
        if (context.userId() == null) {
            return ChatSkillResult.error(spec().name(), ChatSkillErrorCode.SKILL_BAD_INPUT, "userId is required",
                    elapsedMs(started));
        }
        String built = buildRagContext(context.userId(), context.content());
        if (built == null || built.isBlank()) {
            return ChatSkillResult.miss(spec().name(), elapsedMs(started));
        }
        return ChatSkillResult.hit(spec().name(), built, elapsedMs(started));
    }

    private String buildRagContext(Long userId, String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        int topK = Math.min(Math.max(ragTopK, 1), 8);
        int maxContentChars = Math.max(ragMaxContentChars, 100);
        String sourceType = (ragSourceType == null || ragSourceType.isBlank()) ? null : ragSourceType.trim();
        try {
            Duration timeout = resolveRagTimeout();
            AiWorkerQueryResponse response = ragClient.query(userId, content, topK, sourceType, timeout);
            List<AiWorkerChunk> chunks = response != null ? response.getChunks() : null;
            if (chunks == null || chunks.isEmpty()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("rag_context:\n");
            int appended = 0;
            for (AiWorkerChunk chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                String chunkContent = sanitizeRagContent(chunk.getContent(), maxContentChars);
                if (chunkContent.isBlank()) {
                    continue;
                }
                appended++;
                sb.append("- content: ").append(chunkContent).append('\n');
                if (chunk.getTitle() != null && !chunk.getTitle().isBlank()) {
                    sb.append("  title: ").append(sanitize(chunk.getTitle())).append('\n');
                }
                if (chunk.getSourceType() != null && !chunk.getSourceType().isBlank()) {
                    sb.append("  source_type: ").append(chunk.getSourceType()).append('\n');
                }
                if (chunk.getSourceId() != null && !chunk.getSourceId().isBlank()) {
                    sb.append("  source_id: ").append(chunk.getSourceId()).append('\n');
                }
                if (chunk.getChunkIndex() != null) {
                    sb.append("  chunk_index: ").append(chunk.getChunkIndex()).append('\n');
                }
                if (chunk.getDistance() != null) {
                    sb.append("  distance: ").append(chunk.getDistance()).append('\n');
                }
            }

            if (appended == 0) {
                return null;
            }
            sb.append("rag_context_total_chunks: ").append(appended).append('\n');
            return sb.toString().trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private Duration resolveRagTimeout() {
        long timeout = Math.min(Math.max(ragTimeoutMs, 200), 10000);
        return Duration.ofMillis(timeout);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String sanitizeRagContent(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars).trim() + "...";
    }

    private long elapsedMs(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}

