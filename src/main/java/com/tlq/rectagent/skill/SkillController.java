package com.tlq.rectagent.skill;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skill")
public class SkillController {
    private final SkillManager skillManager;

    @Autowired
    public SkillController(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @PostMapping("/document-learning")
    public ResponseEntity<String> processDocumentLearning(
            @RequestBody DocumentLearningRequest request) {
        try {
            DocumentLearningSkill skill = skillManager.getSkill("documentLearning", DocumentLearningSkill.class);
            String result = skill.processRequest(
                    request.getDocumentUrls(),
                    request.getUserRequirement()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("处理失败：" + e.getMessage());
        }
    }
}

class DocumentLearningRequest {
    private List<String> documentUrls;
    private String userRequirement;

    public List<String> getDocumentUrls() {
        return documentUrls;
    }

    public void setDocumentUrls(List<String> documentUrls) {
        this.documentUrls = documentUrls;
    }

    public String getUserRequirement() {
        return userRequirement;
    }

    public void setUserRequirement(String userRequirement) {
        this.userRequirement = userRequirement;
    }
}