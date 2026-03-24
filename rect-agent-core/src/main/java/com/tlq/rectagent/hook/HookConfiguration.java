package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.tlq.rectagent.config.ChatModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class HookConfiguration {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired(required = false)
    private IncrementalCompressionHook incrementalCompressionHook;

    @Value("${rectagent.hook.call-limit:10}")
    private int callLimit;

    @Value("${rectagent.hook.max-tokens-before-summary:4000}")
    private int maxTokensBeforeSummary;

    @Value("${rectagent.hook.messages-to-keep:20}")
    private int messagesToKeep;

    @Value("${rectagent.hook.compression.enabled:false}")
    private boolean compressionEnabled;

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

        if (compressionEnabled && incrementalCompressionHook != null) {
            log.info("IncrementalCompressionHook enabled: thresholdRatio={}, keepRecent={}",
                    incrementalCompressionHook.getThresholdRatio(),
                    incrementalCompressionHook.getKeepRecent());
        } else if (compressionEnabled) {
            log.warn("compression.enabled=true but IncrementalCompressionHook not found");
        }
    }

    public List<Hook> getFrameworkHooks() {
        List<Hook> hooks = new ArrayList<>();
        hooks.add(summarizationHook);
        hooks.add(modelCallLimitHook);

        if (compressionEnabled && incrementalCompressionHook != null) {
            hooks.add(incrementalCompressionHook);
            log.info("Added IncrementalCompressionHook to framework hooks");
        }

        return hooks;
    }

    public SummarizationHook getSummarizationHook() {
        return summarizationHook;
    }

    public ModelCallLimitHook getModelCallLimitHook() {
        return modelCallLimitHook;
    }

    public IncrementalCompressionHook getIncrementalCompressionHook() {
        return incrementalCompressionHook;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }
}
