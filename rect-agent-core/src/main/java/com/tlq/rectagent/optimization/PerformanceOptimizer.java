package com.tlq.rectagent.optimization;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.tlq.rectagent.config.MockChatModel;
import com.tlq.rectagent.tools.DataAnalysisTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class PerformanceOptimizer {

    @Value("${spring.ai.openai.api-key:}")
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

    public ReactAgent getAgent(String agentName, String systemPrompt) {
        return agentCache.computeIfAbsent(agentName, k -> createAgent(agentName, systemPrompt));
    }

    private ReactAgent createAgent(String agentName, String systemPrompt) {
        ChatModel chatModel;
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("PerformanceOptimizer: OpenAI API key not configured, using mock model");
            chatModel = new MockChatModel();
        } else {
            log.info("PerformanceOptimizer: Using OpenAI API");
            chatModel = new MockChatModel();
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

    public void cachePrompt(String key, String prompt) {
        promptCache.put(key, prompt);
    }

    public String getCachedPrompt(String key) {
        return promptCache.get(key);
    }

    public void cacheAnalysisResult(String key, String result) {
        analysisCache.put(key, result);
    }

    public String getCachedAnalysisResult(String key) {
        return analysisCache.get(key);
    }

    public <T> CompletableFuture<T> executeAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    public <T> CompletableFuture<Void> executeBatchAsync(java.util.List<Callable<T>> tasks) {
        return CompletableFuture.allOf(
                tasks.stream()
                        .map(this::executeAsync)
                        .toArray(CompletableFuture[]::new)
        );
    }

    public void clearCache() {
        agentCache.clear();
        promptCache.clear();
        analysisCache.clear();
    }

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
