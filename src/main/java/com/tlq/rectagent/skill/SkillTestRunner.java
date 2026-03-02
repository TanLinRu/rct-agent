package com.tlq.rectagent.skill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class SkillTestRunner {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SkillTestRunner.class, args);
        
        // 测试SkillManager是否能正常获取
        SkillManager skillManager = context.getBean(SkillManager.class);
        System.out.println("SkillManager initialized: " + (skillManager != null));
        
        // 测试DocumentLearningSkill是否已注册
        boolean hasDocumentLearningSkill = skillManager.hasSkill("documentLearning");
        System.out.println("DocumentLearningSkill registered: " + hasDocumentLearningSkill);
        
        if (hasDocumentLearningSkill) {
            DocumentLearningSkill skill = skillManager.getSkill("documentLearning", DocumentLearningSkill.class);
            System.out.println("DocumentLearningSkill instance: " + (skill != null));
        }
        
        context.close();
    }
}