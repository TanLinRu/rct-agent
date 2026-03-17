package com.tlq.rectagent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatModelFactory {

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    private DashScopeApi dashScopeApi;
    private DashScopeChatModel chatModel;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("AI_DASHSCOPE_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("DashScope API key is not configured. Set spring.ai.dashscope.api-key or AI_DASHSCOPE_API_KEY environment variable.");
        }

        this.dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();

        this.chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .build();

        log.info("ChatModelFactory initialized successfully");
    }

    public DashScopeApi getDashScopeApi() {
        return dashScopeApi;
    }

    public DashScopeChatModel getChatModel() {
        return chatModel;
    }

    @PreDestroy
    public void shutdown() {
        log.info("ChatModelFactory shutting down");
    }
}
