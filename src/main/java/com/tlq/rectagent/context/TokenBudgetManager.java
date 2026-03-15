package com.tlq.rectagent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlq.rectagent.data.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBudgetManager {

    @Value("${rectagent.token.max-input:8000}")
    private int maxInputTokens;

    @Value("${rectagent.token.max-output:4000}")
    private int maxOutputTokens;

    @Value("${rectagent.token.window-keep:2}")
    private int windowKeep;

    private final ObjectMapper objectMapper;

    public static final String ROLE_SYSTEM = "SYSTEM";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";
    public static final String ROLE_TOOL = "TOOL";

    public CompressedContext compressContext(List<ChatMessage> allMessages, String systemPrompt) {
        log.info("开始Token预算压缩: 总消息数={}, 最大输入={}", allMessages.size(), maxInputTokens);

        List<ChatMessage> systemMsgs = allMessages.stream()
                .filter(m -> ROLE_SYSTEM.equals(m.getRole()))
                .collect(Collectors.toList());

        List<ChatMessage> userMsgs = allMessages.stream()
                .filter(m -> ROLE_USER.equals(m.getRole()))
                .collect(Collectors.toList());

        List<ChatMessage> assistantMsgs = allMessages.stream()
                .filter(m -> ROLE_ASSISTANT.equals(m.getRole()))
                .collect(Collectors.toList());

        List<ChatMessage> toolMsgs = allMessages.stream()
                .filter(m -> ROLE_TOOL.equals(m.getRole()))
                .collect(Collectors.toList());

        List<ChatMessage> result = new ArrayList<>();

        result.addAll(systemMsgs);

        int currentTokens = estimateTokens(systemPrompt);
        currentTokens += systemMsgs.stream().mapToInt(m -> estimateTokens(m.getContentRaw())).sum();

        int keepUserCount = Math.min(windowKeep, userMsgs.size());
        for (int i = userMsgs.size() - keepUserCount; i < userMsgs.size(); i++) {
            ChatMessage msg = userMsgs.get(i);
            int msgTokens = estimateTokens(msg.getContentRaw());
            if (currentTokens + msgTokens > maxInputTokens) {
                break;
            }
            result.add(msg);
            currentTokens += msgTokens;
        }

        int keepAssistantCount = Math.min(windowKeep, assistantMsgs.size());
        for (int i = assistantMsgs.size() - keepAssistantCount; i < assistantMsgs.size(); i++) {
            ChatMessage msg = assistantMsgs.get(i);
            int msgTokens = estimateTokens(msg.getContentRaw());
            if (currentTokens + msgTokens > maxInputTokens) {
                break;
            }
            result.add(msg);
            currentTokens += msgTokens;
        }

        result.addAll(toolMsgs);

        List<ChatMessage> middleUserMsgs = userMsgs.stream()
                .filter(m -> !result.contains(m))
                .collect(Collectors.toList());
        List<ChatMessage> middleAssistantMsgs = assistantMsgs.stream()
                .filter(m -> !result.contains(m))
                .collect(Collectors.toList());

        if (!middleUserMsgs.isEmpty() || !middleAssistantMsgs.isEmpty()) {
            String summary = generateSemanticSummary(middleUserMsgs, middleAssistantMsgs);
            result.add(0, createSummaryMessage(summary));
            log.info("添加语义摘要: {}字符", summary.length());
        }

        result.sort(Comparator.comparingInt(ChatMessage::getTurnIndex));

        CompressedContext compressed = new CompressedContext();
        compressed.setMessages(result);
        compressed.setOriginalCount(allMessages.size());
        compressed.setCompressedCount(result.size());
        compressed.setCompressionRate(1.0 * result.size() / allMessages.size());
        compressed.setEstimatedTokens(currentTokens);

        log.info("Token预算压缩完成: 原始={}, 压缩后={}, 压缩率={}%, 预估Token={}",
                compressed.getOriginalCount(),
                compressed.getCompressedCount(),
                String.format("%.1f", compressed.getCompressionRate() * 100),
                currentTokens);

        return compressed;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4;
    }

    private String generateSemanticSummary(List<ChatMessage> userMsgs, List<ChatMessage> assistantMsgs) {
        StringBuilder summary = new StringBuilder();
        summary.append("[历史对话摘要] ");

        if (!userMsgs.isEmpty()) {
            summary.append("用户共提问").append(userMsgs.size()).append("次");
        }
        if (!assistantMsgs.isEmpty()) {
            summary.append("，AI回复").append(assistantMsgs.size()).append("次");
        }

        List<String> topics = extractTopics(userMsgs);
        if (!topics.isEmpty()) {
            summary.append("。涉及主题: ").append(String.join(", ", topics));
        }

        return summary.toString();
    }

    private List<String> extractTopics(List<ChatMessage> messages) {
        Set<String> topics = new HashSet<>();
        for (ChatMessage msg : messages) {
            String content = msg.getContentRaw().toLowerCase();
            if (content.contains("分析")) topics.add("数据分析");
            if (content.contains("查询")) topics.add("数据查询");
            if (content.contains("图表")) topics.add("可视化");
            if (content.contains("导出")) topics.add("数据导出");
        }
        return new ArrayList<>(topics);
    }

    private ChatMessage createSummaryMessage(String summary) {
        ChatMessage msg = new ChatMessage();
        msg.setRole("SYSTEM");
        msg.setContentRaw(summary);
        msg.setContentProcessed("[压缩]" + summary);
        return msg;
    }

    public boolean validateConstraints(CompressedContext context, List<String> criticalConstraints) {
        String fullContent = context.getMessages().stream()
                .map(ChatMessage::getContentRaw)
                .collect(Collectors.joining("\n"));

        for (String constraint : criticalConstraints) {
            if (!fullContent.contains(constraint)) {
                log.error("约束条件丢失: {}", constraint);
                return false;
            }
        }
        return true;
    }

    @lombok.Data
    public static class CompressedContext {
        private List<ChatMessage> messages;
        private int originalCount;
        private int compressedCount;
        private double compressionRate;
        private int estimatedTokens;
    }
}
