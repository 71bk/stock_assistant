package tw.bk.appai.skill;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChatSkillRegistry {
    private final List<ChatSkill> skills;

    public ChatSkillRegistry(List<ChatSkill> skills) {
        this.skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public List<ChatSkill> skills() {
        return skills;
    }
}

