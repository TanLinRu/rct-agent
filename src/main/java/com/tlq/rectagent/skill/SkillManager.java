package com.tlq.rectagent.skill;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SkillManager {
    private final Map<String, Object> skills = new HashMap<>();

    public void registerSkill(String skillName, Object skill) {
        skills.put(skillName, skill);
    }

    public <T> T getSkill(String skillName, Class<T> skillType) {
        Object skill = skills.get(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("Skill not found: " + skillName);
        }
        if (!skillType.isInstance(skill)) {
            throw new IllegalArgumentException("Skill type mismatch: " + skillName);
        }
        return skillType.cast(skill);
    }

    public boolean hasSkill(String skillName) {
        return skills.containsKey(skillName);
    }
}