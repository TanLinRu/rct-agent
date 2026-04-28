package com.tlq.rectagent.agent;

import com.tlq.rectagent.config.MockChatModel;
import com.tlq.rectagent.model.router.AgentModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MessageOptimizationAgent {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Autowired
    private AgentModelRouter agentModelRouter;

    private static final String AGENT_NAME = "message_optimization_agent";
    private static final Set<String> REQUIRED_CAPABILITIES = Set.of("optimization");

    private static final String SYSTEM_PROMPT = """
            你是一位专业的消息优化专家，擅长处理和压缩消息记录。
            请对输入的消息进行分析，提取关键信息，并生成压缩后的摘要。
            """;

    private final Map<String, List<MessageRecord>> memoryStore = new ConcurrentHashMap<>();

    private record MessageRecord(
            String id,
            String content,
            String context,
            long timestamp
    ) {}

    public String compressMessage(String message) {
        ChatModel chatModel = getChatModel();
        ChatClient client = ChatClient.builder(chatModel).build();

        String prompt = String.format("请对以下消息进行压缩，提取关键信息，生成简洁的摘要：\n%s", message);
        try {
            String result = client.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();

            int inputLen = message != null ? message.length() : 0;
            int resultLen = result != null ? result.length() : 0;
            log.info("消息压缩成功: 原始长度={}, 压缩后长度={}, 压缩率={}%",
                    inputLen, resultLen,
                    inputLen > 0 ? (resultLen * 100 / inputLen) : 0);
            log.debug("消息压缩详情: 原始={}", message);
            return result;
        } catch (Exception e) {
            log.error("消息压缩失败: {}", e.getMessage(), e);
            return "消息压缩失败: " + e.getMessage();
        }
    }

    private ChatModel getChatModel() {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("MessageOptimizationAgent: OpenAI API key not configured, using mock model");
            return new MockChatModel();
        }

        ChatModel chatModel = agentModelRouter.getChatModel(AGENT_NAME, REQUIRED_CAPABILITIES);
        if (chatModel != null) {
            return chatModel;
        }

        log.warn("MessageOptimizationAgent: AgentModelRouter returned null, using mock model");
        return new MockChatModel();
    }

    public void saveMessageToMemory(String userId, String message, String context) {
        String namespace = "messages:" + userId;
        List<MessageRecord> records = memoryStore.computeIfAbsent(namespace, k -> new ArrayList<>());

        MessageRecord record = new MessageRecord(
                "msg_" + System.currentTimeMillis(),
                message,
                context,
                System.currentTimeMillis()
        );
        records.add(record);
    }

    public Optional<MessageRecord> getMessageFromMemory(String userId, String messageId) {
        String namespace = "messages:" + userId;
        List<MessageRecord> records = memoryStore.get(namespace);
        if (records == null) {
            return Optional.empty();
        }
        return records.stream()
                .filter(r -> r.id().equals(messageId))
                .findFirst();
    }

    public List<MessageRecord> getMessagesByUser(String userId) {
        String namespace = "messages:" + userId;
        return new ArrayList<>(memoryStore.getOrDefault(namespace, List.of()));
    }
}
