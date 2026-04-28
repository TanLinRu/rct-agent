package com.tlq.rectagent.config;

import com.tlq.rectagent.skill.DocumentLearningSkill;
import com.tlq.rectagent.skill.fetcher.CompositeDocumentFetcher;
import com.tlq.rectagent.skill.fetcher.HtmlFetcher;
import com.tlq.rectagent.skill.fetcher.PdfFetcher;
import com.tlq.rectagent.skill.fetcher.OfficeFetcher;
import com.tlq.rectagent.skill.parser.CompositeDocumentParser;
import com.tlq.rectagent.skill.parser.HtmlParser;
import com.tlq.rectagent.skill.parser.PdfParser;
import com.tlq.rectagent.skill.parser.OfficeParser;
import com.tlq.rectagent.skill.validator.DocumentValidator;
import com.tlq.rectagent.skill.validator.DefaultDocumentValidator;
import com.tlq.rectagent.skill.learner.ReActLearner;
import com.tlq.rectagent.skill.learner.LMLearner;
import com.tlq.rectagent.skill.executor.ReActExecutor;
import com.tlq.rectagent.skill.executor.RequirementExecutor;
import com.tlq.rectagent.skill.depositor.DocumentDepositor;
import com.tlq.rectagent.skill.depositor.DefaultDocumentDepositor;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkillConfig {

    private final ChatModelFactory chatModelFactory;

    public SkillConfig(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    @Bean
    public CompositeDocumentFetcher documentFetcher() {
        return new CompositeDocumentFetcher(
            new HtmlFetcher(),
            new PdfFetcher(),
            new OfficeFetcher()
        );
    }

    @Bean
    public CompositeDocumentParser documentParser() {
        return new CompositeDocumentParser(
            new HtmlParser(),
            new PdfParser(),
            new OfficeParser()
        );
    }

    @Bean
    public LMLearner lmLearner() {
        ReactAgent learnerAgent = ReactAgent.builder()
            .name("lm_learner")
            .model(chatModelFactory.getChatModel())
            .systemPrompt("你是一个专业的知识学习助手，擅长从文档中提取核心信息并总结。")
            .build();
        return new ReActLearner(learnerAgent);
    }

    @Bean
    public RequirementExecutor requirementExecutor() {
        ReactAgent executorAgent = ReactAgent.builder()
            .name("requirement_executor")
            .model(chatModelFactory.getChatModel())
            .systemPrompt("你是一个专业的需求实现助手，擅长基于学习到的知识实现用户的需求。")
            .build();
        return new ReActExecutor(executorAgent);
    }

    @Bean
    public DocumentValidator documentValidator() {
        return new DefaultDocumentValidator();
    }

    @Bean
    public DocumentDepositor documentDepositor() {
        return new DefaultDocumentDepositor();
    }

    @Bean
    public DocumentLearningSkill documentLearningSkill(
            CompositeDocumentFetcher documentFetcher,
            CompositeDocumentParser documentParser,
            DocumentValidator documentValidator,
            LMLearner lmLearner,
            RequirementExecutor requirementExecutor,
            DocumentDepositor documentDepositor) {
        return new DocumentLearningSkill(
            documentFetcher,
            documentParser,
            documentValidator,
            lmLearner,
            requirementExecutor,
            documentDepositor
        );
    }
}
