package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.tlq.rectagent.config.ChatModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Configuration
public class HookConfiguration {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Value("${rectagent.hook.call-limit:10}")
    private int callLimit;

    @Value("${rectagent.hook.max-tokens-before-summary:4000}")
    private int maxTokensBeforeSummary;

    @Value("${rectagent.hook.messages-to-keep:20}")
    private int messagesToKeep;

    private SummarizationHook summarizationHook;

    private ModelCallLimitHook modelCallLimitHook;

    @PostConstruct
    public void init() {
        ChatModel chatModel = chatModelFactory.getChatModel();

        summarizationHook = SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(maxTokensBeforeSummary)
                .messagesToKeep(messagesToKeep)
                .build();
        log.info("SummarizationHook initialized: maxTokens={}, messagesToKeep={}",
                maxTokensBeforeSummary, messagesToKeep);

        modelCallLimitHook = ModelCallLimitHook.builder()
                .runLimit(callLimit)
                .build();
        log.info("ModelCallLimitHook initialized: runLimit={}", callLimit);
    }

    public List<com.alibaba.cloud.ai.graph.agent.hook.Hook> getFrameworkHooks() {
        return List.of(summarizationHook, modelCallLimitHook);
    }

    public SummarizationHook getSummarizationHook() {
        return summarizationHook;
    }

    public ModelCallLimitHook getModelCallLimitHook() {
        return modelCallLimitHook;
    }
}
