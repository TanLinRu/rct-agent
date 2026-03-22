package com.tlq.rectagent.optimization;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.tlq.rectagent.config.MockChatModel;
import com.tlq.rectagent.tools.DataAnalysisTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 性能优化器
 * 负责提高系统性能，包括智能体实例缓存、批量处理等
 */
@Slf4j
@Component
public class PerformanceOptimizer {

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    private final Map<String, ReactAgent> agentCache;
    private final ExecutorService executorService;
    private final Map<String, String> promptCache;
    private final Map<String, String> analysisCache;

    public PerformanceOptimizer() {
        this.agentCache = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(10);
        this.promptCache = new ConcurrentHashMap<>();
        this.analysisCache = new ConcurrentHashMap<>();
    }

    /**
     * 获取智能体实例（缓存机制）
     * @param agentName 智能体名称
     * @param systemPrompt 系统提示词
     * @return 智能体实例
     */
    public ReactAgent getAgent(String agentName, String systemPrompt) {
        return agentCache.computeIfAbsent(agentName, k -> createAgent(agentName, systemPrompt));
    }

    /**
     * 创建智能体实例
     * @param agentName 智能体名称
     * @param systemPrompt 系统提示词
     * @return 智能体实例
     */
    private ReactAgent createAgent(String agentName, String systemPrompt) {
        ChatModel chatModel;
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("PerformanceOptimizer: DashScope API key not configured, using mock model");
            chatModel = new MockChatModel();
        } else {
            DashScopeApi dashScopeApi = DashScopeApi.builder()
                    .apiKey(apiKey)
                    .build();
            chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .build();
        }

        DataAnalysisTools dataAnalysisTools = new DataAnalysisTools();

        return ReactAgent.builder()
                .name(agentName)
                .chatOptions(ChatOptions.builder().build())
                .model(chatModel)
                .methodTools(dataAnalysisTools)
                .systemPrompt(systemPrompt)
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 缓存提示词
     * @param key 缓存键
     * @param prompt 提示词
     */
    public void cachePrompt(String key, String prompt) {
        promptCache.put(key, prompt);
    }

    /**
     * 获取缓存的提示词
     * @param key 缓存键
     * @return 提示词
     */
    public String getCachedPrompt(String key) {
        return promptCache.get(key);
    }

    /**
     * 缓存分析结果
     * @param key 缓存键
     * @param result 分析结果
     */
    public void cacheAnalysisResult(String key, String result) {
        analysisCache.put(key, result);
    }

    /**
     * 获取缓存的分析结果
     * @param key 缓存键
     * @return 分析结果
     */
    public String getCachedAnalysisResult(String key) {
        return analysisCache.get(key);
    }

    /**
     * 执行异步任务
     * @param task 任务
     * @param <T> 任务返回类型
     * @return  Future
     */
    public <T> CompletableFuture<T> executeAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    /**
     * 批量执行任务
     * @param tasks 任务列表
     * @param <T> 任务返回类型
     * @return 任务结果列表
     */
    public <T> CompletableFuture<Void> executeBatchAsync(java.util.List<Callable<T>> tasks) {
        return CompletableFuture.allOf(
                tasks.stream()
                        .map(this::executeAsync)
                        .toArray(CompletableFuture[]::new)
        );
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        agentCache.clear();
        promptCache.clear();
        analysisCache.clear();
    }

    /**
     * 关闭资源
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}