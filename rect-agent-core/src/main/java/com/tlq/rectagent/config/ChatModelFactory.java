package com.tlq.rectagent.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatModelFactory {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    private final ChatModel chatModel;

    @Autowired
    public ChatModelFactory(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = System.getenv("OPENAI_BASE_URL");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("OpenAI API key is not configured. ChatModelFactory will run in mock mode. "
                    + "Set spring.ai.openai.api-key or OPENAI_API_KEY environment variable to enable real API calls.");
        } else {
            log.info("ChatModelFactory initialized with API: baseUrl={}, model will be provided by Spring AI", baseUrl);
        }
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
