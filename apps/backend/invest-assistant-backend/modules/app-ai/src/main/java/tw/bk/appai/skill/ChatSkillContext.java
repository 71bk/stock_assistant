package tw.bk.appai.skill;

import tw.bk.appcommon.enums.UserRole;

public record ChatSkillContext(
        Long userId,
        Long conversationId,
        Long currentUserMessageId,
        String content,
        UserRole role) {
}

