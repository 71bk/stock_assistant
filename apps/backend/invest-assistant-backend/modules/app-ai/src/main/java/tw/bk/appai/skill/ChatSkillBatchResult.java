package tw.bk.appai.skill;

import java.util.List;

public record ChatSkillBatchResult(
        String mergedContext,
        List<ChatSkillResult> results) {
}

