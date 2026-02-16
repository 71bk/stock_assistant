package tw.bk.appai.skill;

public record ChatSkillResult(
        String skillName,
        ChatSkillStatus status,
        String contextPayload,
        ChatSkillErrorCode errorCode,
        String errorMessage,
        long elapsedMs) {

    public static ChatSkillResult hit(String skillName, String contextPayload, long elapsedMs) {
        return new ChatSkillResult(skillName, ChatSkillStatus.HIT, contextPayload, null, null, elapsedMs);
    }

    public static ChatSkillResult miss(String skillName, long elapsedMs) {
        return new ChatSkillResult(skillName, ChatSkillStatus.MISS, null, null, null, elapsedMs);
    }

    public static ChatSkillResult forbidden(String skillName, long elapsedMs) {
        return new ChatSkillResult(skillName, ChatSkillStatus.FORBIDDEN, null, ChatSkillErrorCode.SKILL_FORBIDDEN,
                "Skill forbidden", elapsedMs);
    }

    public static ChatSkillResult timeout(String skillName, long elapsedMs) {
        return new ChatSkillResult(skillName, ChatSkillStatus.TIMEOUT, null, ChatSkillErrorCode.SKILL_TIMEOUT,
                "Skill timeout", elapsedMs);
    }

    public static ChatSkillResult error(String skillName, ChatSkillErrorCode code, String message, long elapsedMs) {
        return new ChatSkillResult(skillName, ChatSkillStatus.ERROR, null, code, message, elapsedMs);
    }
}

