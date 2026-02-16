package tw.bk.appai.skill;

import tw.bk.appcommon.enums.UserRole;

public record ChatSkillSpec(
        String name,
        String version,
        UserRole requiredRole,
        long timeoutMs,
        boolean enabled,
        String inputSchema,
        String outputSchema) {
}

