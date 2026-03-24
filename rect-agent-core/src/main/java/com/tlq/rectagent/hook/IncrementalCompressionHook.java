package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@HookPositions({HookPosition.BEFORE_MODEL})
@Component
public class IncrementalCompressionHook extends MessagesModelHook {

    private static final String TRACE_ID_KEY = "traceId";

    @Value("${rectagent.hook.compression.enabled:false}")
    private boolean enabled;

    @Value("${rectagent.hook.compression.threshold-ratio:0.7}")
    private double thresholdRatio;

    @Value("${rectagent.hook.compression.keep-recent:5}")
    private int keepRecent;

    @Value("${rectagent.hook.compression.preserve-tool-calls:true}")
    private boolean preserveToolCalls;

    @Value("${rectagent.hook.compression.max-summary-tokens:1000}")
    private int maxSummaryTokens;

    @Value("${rectagent.token.max-input:8000}")
    private int maxInputTokens;

    private final Map<String, CompressionState> sessionStates = new ConcurrentHashMap<>();

    public double getThresholdRatio() {
        return thresholdRatio;
    }

    public int getKeepRecent() {
        return keepRecent;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getName() {
        return "IncrementalCompressionHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (!enabled) {
            return new AgentCommand(previousMessages);
        }

        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = "unknown";
        }

        String sessionId = extractSessionId(config);
        
        try {
            int currentTokens = estimateTokens(previousMessages);
            int threshold = (int) (maxInputTokens * thresholdRatio);

            log.debug("[{}] 压缩检查(BEFORE): 当前token={}, 阈值={}, sessionId={}", 
                    traceId, currentTokens, threshold, sessionId);

            if (currentTokens > threshold) {
                List<Message> compressed = compressMessages(previousMessages, sessionId, traceId);
                
                CompressionState state = sessionStates.computeIfAbsent(sessionId, k -> new CompressionState());
                state.incrementCompressionCount();

                log.info("[{}] 增量压缩完成: {}条消息 -> {}条消息, 压缩次数={}", 
                        traceId, previousMessages.size(), compressed.size(), 
                        state.getCompressionCount());
                return new AgentCommand(compressed, UpdatePolicy.REPLACE);
            }

        } catch (Exception e) {
            log.warn("[{}] 增量压缩失败: {}", traceId, e.getMessage());
        }

        return new AgentCommand(previousMessages);
    }

    private List<Message> compressMessages(List<Message> messages, String sessionId, String traceId) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        List<Message> result = new ArrayList<>();
        List<Message> historyToCompress = new ArrayList<>();

        int kept = 0;
        for (int i = messages.size() - 1; i >= 0 && kept < keepRecent; i--) {
            Message msg = messages.get(i);
            if (preserveToolCalls && hasToolCalls(msg)) {
                result.add(0, msg);
            } else {
                result.add(0, msg);
                kept++;
            }
        }

        for (int i = 0; i < messages.size() - kept; i++) {
            historyToCompress.add(messages.get(i));
        }

        if (!historyToCompress.isEmpty()) {
            String summary = generateSummary(historyToCompress, traceId);
            UserMessage summaryMessage = new UserMessage(
                    "[上下文压缩摘要]\n" +
                    "- 原始消息数: " + messages.size() + "\n" +
                    "- 已压缩消息数: " + historyToCompress.size() + "\n" +
                    "- 保留最近: " + kept + " 条消息\n" +
                    "- 摘要: " + summary
            );
            result.add(0, summaryMessage);
        }

        return result;
    }

    private String generateSummary(List<Message> messages, String traceId) {
        if (messages == null || messages.isEmpty()) {
            return "无历史消息";
        }

        int userCount = 0;
        int assistantCount = 0;
        int toolCount = 0;

        for (Message msg : messages) {
            String role = msg.getMessageType() != null ? msg.getMessageType().toString() : "";
            switch (role.toLowerCase()) {
                case "user" -> userCount++;
                case "assistant" -> assistantCount++;
                case "tool" -> toolCount++;
            }
        }

        StringBuilder summary = new StringBuilder();
        summary.append("用户提问").append(userCount).append("次");
        summary.append("，AI回复").append(assistantCount).append("次");
        if (toolCount > 0) {
            summary.append("，工具调用").append(toolCount).append("次");
        }

        log.debug("[{}] 生成摘要: {}", traceId, summary);
        return summary.toString();
    }

    private boolean hasToolCalls(Message message) {
        try {
            if (message instanceof org.springframework.ai.chat.messages.AssistantMessage am) {
                return am.getToolCalls() != null && !am.getToolCalls().isEmpty();
            }
        } catch (Exception e) {
            log.debug("检查tool calls失败: {}", e.getMessage());
        }
        return false;
    }

    private int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Message msg : messages) {
            String content = msg.getText();
            if (content != null) {
                total += content.length() / 4;
            }
        }
        return total;
    }

    private String extractSessionId(RunnableConfig config) {
        if (config != null && config.context() != null) {
            Object value = config.context().get("sessionId");
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return MDC.get("sessionId");
    }

    private static class CompressionState {
        private int compressionCount = 0;

        public void incrementCompressionCount() {
            compressionCount++;
        }

        public int getCompressionCount() {
            return compressionCount;
        }
    }
}
