package com.tlq.rectagent.skill;

import com.tlq.rectagent.skill.fetcher.DocumentFetcher;
import com.tlq.rectagent.skill.parser.DocumentParser;
import com.tlq.rectagent.skill.validator.DocumentValidator;
import com.tlq.rectagent.skill.learner.LMLearner;
import com.tlq.rectagent.skill.executor.RequirementExecutor;
import com.tlq.rectagent.skill.depositor.DocumentDepositor;

import java.util.ArrayList;
import java.util.List;

public class DocumentLearningSkill {
    private final DocumentFetcher documentFetcher;
    private final DocumentParser documentParser;
    private final DocumentValidator documentValidator;
    private final LMLearner lmLearner;
    private final RequirementExecutor requirementExecutor;
    private final DocumentDepositor documentDepositor;

    public DocumentLearningSkill(DocumentFetcher documentFetcher, 
                                 DocumentParser documentParser, 
                                 DocumentValidator documentValidator,
                                 LMLearner lmLearner, 
                                 RequirementExecutor requirementExecutor,
                                 DocumentDepositor documentDepositor) {
        this.documentFetcher = documentFetcher;
        this.documentParser = documentParser;
        this.documentValidator = documentValidator;
        this.lmLearner = lmLearner;
        this.requirementExecutor = requirementExecutor;
        this.documentDepositor = documentDepositor;
    }

    public String processRequest(List<String> documentUrls, String userRequirement) {
        try {
            // 1. 获取并解析文档
            List<String> documentContents = new ArrayList<>();
            for (String url : documentUrls) {
                if (!documentFetcher.supports(url)) {
                    throw new UnsupportedOperationException("不支持的文档URL格式：" + url);
                }
                byte[] content = documentFetcher.fetch(url);
                String parsedContent = documentParser.parse(content, url);
                // 2. 确认文档有效性
                if (!documentValidator.validate(parsedContent, url)) {
                    throw new IllegalArgumentException("文档验证失败：" + url);
                }
                documentContents.add(parsedContent);
            }

            // 3. 让LLM学习文档内容
            String knowledge = lmLearner.learn(documentContents);

            // 4. 基于学习到的知识实现需求
            String result = requirementExecutor.execute(knowledge, userRequirement);

            // 5. 沉淀学习结果和实现方案
            documentDepositor.deposit(knowledge, result, userRequirement);

            return result;
        } catch (Exception e) {
            // 错误处理
            return "处理过程中发生错误：" + e.getMessage();
        }
    }
}