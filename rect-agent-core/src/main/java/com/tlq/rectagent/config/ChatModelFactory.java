package com.tlq.rectagent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatModelFactory {

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    private DashScopeApi dashScopeApi;
    private ChatModel chatModel;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("DashScope API key is not configured. ChatModelFactory will run in mock mode. "
                    + "Set spring.ai.dashscope.api-key or DASHSCOPE_API_KEY environment variable to enable real API calls.");
            this.chatModel = new MockChatModel();
            return;
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

    public ChatModel getChatModel() {
        return chatModel;
    }

    public boolean isMockMode() {
        return chatModel instanceof MockChatModel;
    }

    @PreDestroy
    public void shutdown() {
        log.info("ChatModelFactory shutting down");
    }
}
