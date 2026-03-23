package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.hook.ContextInjectionHook;
import com.tlq.rectagent.hook.HookConfiguration;
import com.tlq.rectagent.hook.ProfileInferenceHook;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 用户查询意图识别智能体
 * 负责分析用户输入，识别查询意图
 */
@Slf4j
@Component
public class IntentRecognitionAgent {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Value("${rectagent.prompts.intent-recognition}")
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
                    ChatModel chatModel = chatModelFactory.getChatModel();

                    List<com.alibaba.cloud.ai.graph.agent.hook.Hook> allHooks = new java.util.ArrayList<>();
                    allHooks.add(contextInjectionHook);
                    allHooks.addAll(hookConfiguration.getFrameworkHooks());
                    allHooks.add(profileInferenceHook);

                    agent = ReactAgent.builder()
                            .name("intent_recognition_agent")
                            .chatOptions(ChatOptions.builder().build())
                            .model(chatModel)
                            .systemPrompt(systemPrompt)
                            .instruction("请分析以下用户输入，识别查询意图和实体：\n{input}")
                            .outputKey("user_intent")
                            .includeContents(false)
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

    /**
     * 识别用户查询意图
     * @param userInput 用户输入
     * @return 意图识别结果
     */
    public String recognizeIntent(String userInput) {
        ReactAgent agent = getAgent();
        try {
            String result = agent.call(userInput).getText();
            int inputLen = userInput != null ? userInput.length() : 0;
            int resultLen = result != null ? result.length() : 0;
            log.info("意图识别成功: 输入长度={}, 输出长度={}", inputLen, resultLen);
            log.debug("意图识别详情: 输入={}, 输出={}", userInput, result);
            return result;
        } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
            log.error("意图识别失败: {}", e.getMessage(), e);
            return "意图识别失败: " + e.getMessage();
        }
    }
}