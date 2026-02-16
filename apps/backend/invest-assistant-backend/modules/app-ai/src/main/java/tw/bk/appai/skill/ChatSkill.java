package tw.bk.appai.skill;

public interface ChatSkill {
    ChatSkillSpec spec();

    boolean supports(ChatSkillContext context);

    ChatSkillResult execute(ChatSkillContext context);
}

