package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
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

    private ReactAgent agent;

    public ReactAgent getAgent() {
        if (agent == null) {
            synchronized (this) {
                if (agent == null) {
                    DashScopeChatModel chatModel = chatModelFactory.getChatModel();

                    // 创建动态提示词智能体
                    agent = ReactAgent.builder()
                            .name("dynamic_prompt_agent")
                            .chatOptions(ChatOptions.builder().build())
                            .model(chatModel)
                            .systemPrompt("你是一位专业的提示词工程师，擅长根据上下文生成优化的提示词。请根据用户的意图和上下文信息，生成一个针对性强、效果好的提示词。")
                            .instruction("用户意图：{user_intent}\n请根据用户意图生成一个优化的提示词。")
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
            return agent.call(prompt).getText();
        } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
            log.error("提示词生成失败: {}", e.getMessage(), e);
            return "提示词生成失败: " + e.getMessage();
        }
    }
}
