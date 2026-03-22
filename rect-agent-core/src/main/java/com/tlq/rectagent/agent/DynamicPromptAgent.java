package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 动态提示词智能体
 * 负责根据上下文生成优化的提示词
 */
@Slf4j
@Component
public class DynamicPromptAgent {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Value("${rectagent.prompts.dynamic-prompt}")
    private String systemPrompt;

    private ReactAgent agent;

    public ReactAgent getAgent() {
        if (agent == null) {
            synchronized (this) {
                if (agent == null) {
                    ChatModel chatModel = chatModelFactory.getChatModel();

                    agent = ReactAgent.builder()
                            .name("dynamic_prompt_agent")
                            .chatOptions(ChatOptions.builder().build())
                            .model(chatModel)
                            .systemPrompt(systemPrompt)
                            .instruction("请根据以下意图信息生成优化的提示词。")
                            .outputKey("generated_prompt")
                            .saver(new MemorySaver())
                            .interceptors(Arrays.asList(new ModelProcessInterceptor(), new ToolMonitoringInterceptor()))
                            .build();
                }
            }
        }
        return agent;
    }

    /**
     * 生成动态提示词
     * @param intent 用户意图
     * @param context 上下文信息
     * @return 优化后的提示词
     */
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
