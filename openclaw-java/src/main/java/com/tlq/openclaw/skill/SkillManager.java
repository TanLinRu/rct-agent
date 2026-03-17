package com.tlq.openclaw.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Component
public class SkillManager {
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    
    public Skill createSkill(String name, String type, String description) {
        String skillId = UUID.randomUUID().toString();
        Skill skill;
        
        switch (type.toLowerCase()) {
            case "weather":
                skill = new WeatherSkill(skillId, name, description);
                break;
            default:
                throw new IllegalArgumentException("Unsupported skill type: " + type);
        }
        
        skills.put(skillId, skill);
        log.info("Created skill: {} (type: {})", name, type);
        return skill;
    }
    
    public Skill getSkill(String skillId) {
        return skills.get(skillId);
    }
    
    public void removeSkill(String skillId) {
        Skill skill = skills.remove(skillId);
        if (skill != null) {
            log.info("Removed skill: {}", skill.getName());
        }
    }
    
    public Map<String, Skill> getAllSkills() {
        return skills;
    }
    
    public Object executeSkill(String skillId, Map<String, Object> parameters) {
        Skill skill = skills.get(skillId);
        if (skill == null) {
            log.warn("Skill not found: {}", skillId);
            return "Skill not found: " + skillId;
        }
        return skill.execute(parameters);
    }
}