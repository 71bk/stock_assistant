package tw.bk.appai.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.UserRole;

@Service
@Slf4j
public class ChatSkillExecutor {
    private final ChatSkillRegistry registry;
    private final Executor skillExecutor;

    public ChatSkillExecutor(ChatSkillRegistry registry, @Qualifier("aiExecutor") Executor skillExecutor) {
        this.registry = registry;
        this.skillExecutor = skillExecutor;
    }

    public ChatSkillBatchResult executeAll(ChatSkillContext context) {
        List<ChatSkillResult> results = new ArrayList<>();
        List<String> sections = new ArrayList<>();

        for (ChatSkill skill : registry.skills()) {
            ChatSkillSpec spec = skill.spec();
            String skillName = safeSkillName(spec, skill);

            if (spec == null || !spec.enabled()) {
                results.add(ChatSkillResult.miss(skillName, 0L));
                continue;
            }
            if (!hasRequiredRole(context.role(), spec.requiredRole())) {
                results.add(ChatSkillResult.forbidden(skillName, 0L));
                continue;
            }
            if (!skill.supports(context)) {
                results.add(ChatSkillResult.miss(skillName, 0L));
                continue;
            }

            long started = System.nanoTime();
            ChatSkillResult result = executeWithTimeout(skill, context, spec.timeoutMs(), skillName, started);
            results.add(result);

            if (result.status() == ChatSkillStatus.HIT
                    && result.contextPayload() != null
                    && !result.contextPayload().isBlank()) {
                sections.add(result.contextPayload().trim());
            }
        }

        String merged = sections.isEmpty() ? null : String.join("\n\n", sections);
        return new ChatSkillBatchResult(merged, List.copyOf(results));
    }

    private ChatSkillResult executeWithTimeout(
            ChatSkill skill,
            ChatSkillContext context,
            long timeoutMs,
            String skillName,
            long started) {
        long effectiveTimeout = Math.max(timeoutMs, 100L);
        CompletableFuture<ChatSkillResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                ChatSkillResult result = skill.execute(context);
                if (result == null) {
                    return ChatSkillResult.miss(skillName, elapsedMs(started));
                }
                return result;
            } catch (Exception ex) {
                log.warn("Skill execution failed: skill={}, reason={}", skillName, ex.getMessage());
                return ChatSkillResult.error(skillName, ChatSkillErrorCode.SKILL_UPSTREAM_ERROR, ex.getMessage(),
                        elapsedMs(started));
            }
        }, skillExecutor);

        try {
            ChatSkillResult result = future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
            if (result.elapsedMs() <= 0) {
                return new ChatSkillResult(
                        result.skillName(),
                        result.status(),
                        result.contextPayload(),
                        result.errorCode(),
                        result.errorMessage(),
                        elapsedMs(started));
            }
            return result;
        } catch (TimeoutException ex) {
            future.cancel(true);
            return ChatSkillResult.timeout(skillName, elapsedMs(started));
        } catch (Exception ex) {
            future.cancel(true);
            return ChatSkillResult.error(skillName, ChatSkillErrorCode.SKILL_UPSTREAM_ERROR, ex.getMessage(),
                    elapsedMs(started));
        }
    }

    private boolean hasRequiredRole(UserRole actualRole, UserRole requiredRole) {
        if (requiredRole == null) {
            return true;
        }
        UserRole effective = actualRole == null ? UserRole.USER : actualRole;
        if (requiredRole == UserRole.USER) {
            return true;
        }
        return effective == UserRole.ADMIN;
    }

    private String safeSkillName(ChatSkillSpec spec, ChatSkill skill) {
        if (spec != null && spec.name() != null && !spec.name().isBlank()) {
            return spec.name().trim();
        }
        return skill.getClass().getSimpleName();
    }

    private long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}

