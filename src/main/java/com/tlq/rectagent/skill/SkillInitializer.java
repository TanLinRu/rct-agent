package com.tlq.rectagent.skill;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SkillInitializer implements ApplicationRunner {
    private final SkillManager skillManager;
    private final DocumentLearningSkill documentLearningSkill;

    @Autowired
    public SkillInitializer(SkillManager skillManager, DocumentLearningSkill documentLearningSkill) {
        this.skillManager = skillManager;
        this.documentLearningSkill = documentLearningSkill;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 注册文档学习skill
        skillManager.registerSkill("documentLearning", documentLearningSkill);
        System.out.println("DocumentLearningSkill registered successfully");
    }
}