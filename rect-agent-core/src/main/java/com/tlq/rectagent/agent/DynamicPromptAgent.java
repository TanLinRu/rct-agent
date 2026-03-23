package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.tlq.rectagent.hook.ContextInjectionHook;
import com.tlq.rectagent.hook.HookConfiguration;
import com.tlq.rectagent.hook.ProfileInferenceHook;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import com.tlq.rectagent.model.router.AgentModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class DynamicPromptAgent {

    public static final String AGENT_NAME = "dynamic_prompt_agent";
    public static final Set<String> REQUIRED_CAPABILITIES = Set.of("prompt");

    @Autowired
    private AgentModelRouter agentModelRouter;

    @Value("${rectagent.prompts.dynamic-prompt}")
    private String systemPrompt;

    @Autowired
    private HookConfiguration hookConfiguration;

    @Autowired
    private ContextInjectionHook contextInjectionHook;

    @Autowired
    private ProfileInferenceHook profileInferenceHook;

    private ReactAgent agent;

    public ReactAgent getAgent() {
        if (agent == null) {
            synchronized (this) {
                if (agent == null) {
                    ChatModel chatModel = agentModelRouter.getChatModel(AGENT_NAME, REQUIRED_CAPABILITIES);
                    if (chatModel == null) {
                        throw new IllegalStateException("No available ChatModel for " + AGENT_NAME);
                    }

                    List<com.alibaba.cloud.ai.graph.agent.hook.Hook> allHooks = new ArrayList<>();
                    allHooks.add(contextInjectionHook);
                    allHooks.addAll(hookConfiguration.getFrameworkHooks());
                    allHooks.add(profileInferenceHook);

                    agent = ReactAgent.builder()
                            .name(AGENT_NAME)
                            .chatOptions(ChatOptions.builder().build())
                            .model(chatModel)
                            .systemPrompt(systemPrompt)
                            .instruction("请根据以下意图信息生成优化的提示词：\n{user_intent}")
                            .outputKey("generated_prompt")
                            .includeContents(true)
                            .returnReasoningContents(false)
                            .saver(new MemorySaver())
                            .hooks(allHooks)
                            .interceptors(Arrays.asList(new ModelProcessInterceptor(), new ToolMonitoringInterceptor()))
                            .build();
                }
            }
        }
        return agent;
    }

    public String generatePrompt(String intent, String context) {
        ReactAgent agent = getAgent();
        String prompt = String.format("用户意图：%s\n上下文信息：%s\n请生成一个优化的提示词。", intent, context);
        try {
            String result = agent.call(prompt).getText();
            log.info("提示词生成成功: 意图长度={}, 上下文长度={}, 输出长度={}",
                    intent != null ? intent.length() : 0,
                    context != null ? context.length() : 0,
                    result != null ? result.length() : 0);
            log.debug("提示词生成详情: 意图={}, 上下文={}, 输出={}", intent, context, result);
            return result;
        } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
            log.error("提示词生成失败: {}", e.getMessage(), e);
            return "提示词生成失败: " + e.getMessage();
        }
    }
}
