package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 消息优化智能体
 * 负责处理消息记录和压缩存储
 */
@Component
public class MessageOptimizationAgent {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    private final MemoryStore memoryStore;

    public MessageOptimizationAgent() {
        this.memoryStore = new MemoryStore();
    }

    public ReactAgent createAgent() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();

        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .build();

        // 创建消息优化智能体
        return ReactAgent.builder()
                .name("message_optimization_agent")
                .chatOptions(ChatOptions.builder().build())
                .model(chatModel)
                .systemPrompt("你是一位专业的消息优化专家，擅长处理和压缩消息记录。请对输入的消息进行分析，提取关键信息，并生成压缩后的摘要。")
                .saver(new MemorySaver())
                .interceptors(Arrays.asList(new ModelProcessInterceptor(), new ToolMonitoringInterceptor()))
                .build();
    }

    /**
     * 压缩消息
     * @param message 原始消息
     * @return 压缩后的消息
     */
    public String compressMessage(String message) {
        ReactAgent agent = createAgent();
        String prompt = String.format("请对以下消息进行压缩，提取关键信息，生成简洁的摘要：\n%s", message);
        try {
            return agent.call(prompt).getText();
        } catch (GraphRunnerException e) {
            e.printStackTrace();
            return "消息压缩失败: " + e.getMessage();
        }
    }

    /**
     * 保存消息到长期记忆
     * @param userId 用户ID
     * @param message 消息内容
     * @param context 上下文信息
     */
    public void saveMessageToMemory(String userId, String message, String context) {
        List<String> namespace = List.of("messages", userId);
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("content", message);
        messageData.put("context", context);
        messageData.put("timestamp", System.currentTimeMillis());
        
        StoreItem item = StoreItem.of(namespace, "msg_" + System.currentTimeMillis(), messageData);
        memoryStore.putItem(item);
    }

    /**
     * 从长期记忆中获取消息
     * @param userId 用户ID
     * @param messageId 消息ID
     * @return 消息内容
     */
    public Optional<StoreItem> getMessageFromMemory(String userId, String messageId) {
        List<String> namespace = List.of("messages", userId);
        return memoryStore.getItem(namespace, messageId);
    }
}