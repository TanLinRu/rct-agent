package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.tlq.rectagent.config.ChatModelFactory;
import org.springframework.ai.chat.model.ChatModel;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 用户查询意图识别智能体
 * 负责分析用户输入，识别查询意图
 */
@Slf4j
@Component
public class IntentRecognitionAgent {

    @Autowired
    private ChatModelFactory chatModelFactory;

    private ReactAgent agent;

    public ReactAgent getAgent() {
        if (agent == null) {
            synchronized (this) {
                if (agent == null) {
                    ChatModel chatModel = chatModelFactory.getChatModel();

                    // 创建意图识别智能体
                    agent = ReactAgent.builder()
                            .name("intent_recognition_agent")
                            .chatOptions(ChatOptions.builder().build())
                            .model(chatModel)
                            .systemPrompt("你是一位专业的意图识别专家，擅长分析用户的查询意图。请仔细分析用户的输入，识别出用户的具体意图，并返回结构化的意图信息。输出格式：{\"intent\": \"...\", \"entities\": [...], \"confidence\": 0.95}")
                            .outputKey("user_intent")
                            .saver(new MemorySaver())
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