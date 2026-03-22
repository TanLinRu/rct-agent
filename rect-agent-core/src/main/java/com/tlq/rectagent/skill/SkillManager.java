package com.tlq.rectagent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SkillManager {

    private final Map<String, Object> skills = new ConcurrentHashMap<>();

    public <T> void registerSkill(String skillName, T skill) {
        skills.put(skillName, skill);
        log.info("Registered skill: {}", skillName);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSkill(String skillName, Class<T> skillType) {
        Object skill = skills.get(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("Skill not found: " + skillName);
        }
        if (!skillType.isInstance(skill)) {
            throw new IllegalArgumentException("Skill type mismatch: " + skillName + 
                    ", expected: " + skillType.getName() + 
                    ", actual: " + skill.getClass().getName());
        }
        return (T) skill;
    }

    public boolean hasSkill(String skillName) {
        return skills.containsKey(skillName);
    }

    public <T> void unregisterSkill(String skillName, Class<T> skillType) {
        if (hasSkill(skillName)) {
            skills.remove(skillName);
            log.info("Unregistered skill: {}", skillName);
        }
    }

    public void clearSkills() {
        skills.clear();
        log.info("Cleared all skills");
    }
}
