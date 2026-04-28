package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
@Deprecated
/**
 * @deprecated 请使用 {@link ClawCodeCompressionHook} 替代。
 * ClawCodeCompressionHook 实现了与 Claw Code 一致的压缩逻辑：
 * - 保留最近 4 条消息（可配置）
 * - 超过 10K tokens 时触发压缩（可配置）
 * - 生成 Claw Code 格式的摘要（统计、工具列表、用户请求、待办、关键文件、时间线）
 */
public class FrameworkCompressionHook extends MessagesModelHook {

    private static final String TRACE_ID_KEY = "traceId";
    
    @Value("${rectagent.hook.compression.enabled:false}")
    private boolean enabled;

    @Value("${rectagent.hook.compression.threshold-ratio:0.7}")
    private double thresholdRatio;

    @Value("${rectagent.hook.compression.keep-recent:5}")
    private int keepRecent;

    @Value("${rectagent.token.max-input:8000}")
    private int maxInputTokens;

    private ChatModel chatModel;

    public FrameworkCompressionHook() {
    }

    public FrameworkCompressionHook(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String getName() {
        return "FrameworkCompressionHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String traceId = getTraceId(config);
        
        if (!enabled) {
            log.debug("[{}] FrameworkCompressionHook: disabled, skipping", traceId);
            return new AgentCommand(previousMessages);
        }

        if (previousMessages == null || previousMessages.isEmpty()) {
            log.debug("[{}] FrameworkCompressionHook: no messages, skipping", traceId);
            return new AgentCommand(previousMessages);
        }

        try {
            int currentTokens = estimateTokens(previousMessages);
            int threshold = (int) (maxInputTokens * thresholdRatio);

            log.info("[{}] FrameworkCompressionHook: processing - messages={}, tokens={}, threshold={}",
                    traceId, previousMessages.size(), currentTokens, threshold);
            
            log.info("[{}] ===== Messages BEFORE compression =====", traceId);
            for (int i = 0; i < previousMessages.size(); i++) {
                Message m = previousMessages.get(i);
                log.info("[{}] [{}] {}:\n{}", traceId, i, m.getClass().getSimpleName(), m.getText());
            }

            if (currentTokens > threshold) {
                List<Message> compressed = compressMessages(previousMessages);
                log.info("[{}] Framework compression completed: {} messages -> {} messages",
                        traceId, previousMessages.size(), compressed.size());
                
                log.info("[{}] ===== Messages AFTER compression =====", traceId);
                for (int i = 0; i < compressed.size(); i++) {
                    Message m = compressed.get(i);
                    log.info("[{}] [{}] {}:\n{}", traceId, i, m.getClass().getSimpleName(), m.getText());
                }
                
                return new AgentCommand(compressed, UpdatePolicy.REPLACE);
            }

            log.debug("[{}] No compression needed: {} <= {}", traceId, currentTokens, threshold);

        } catch (Exception e) {
            log.warn("[{}] Compression failed: {}", traceId, e.getMessage());
        }

        return new AgentCommand(previousMessages);
    }
    
    private String getTraceId(RunnableConfig config) {
        var traceIdObj = config.context().get(TRACE_ID_KEY);
        if (traceIdObj != null) {
            return traceIdObj.toString();
        }
        String mdcTraceId = MDC.get(TRACE_ID_KEY);
        return mdcTraceId != null ? mdcTraceId : "unknown";
    }
    
    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private List<Message> compressMessages(List<Message> messages) {
        List<Message> result = new ArrayList<>();
        List<Message> historyToCompress = new ArrayList<>();

        int kept = 0;
        for (int i = messages.size() - 1; i >= 0 && kept < keepRecent; i--) {
            Message msg = messages.get(i);
            result.add(0, msg);
            kept++;
        }

        for (int i = 0; i < messages.size() - kept; i++) {
            historyToCompress.add(messages.get(i));
        }

        if (!historyToCompress.isEmpty()) {
            String summary = generateSummary(historyToCompress);
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

    private String generateSummary(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "无历史消息";
        }

        int userCount = 0;
        int assistantCount = 0;
        int toolCount = 0;

        for (Message msg : messages) {
            if (msg instanceof UserMessage) {
                userCount++;
            } else if (msg instanceof AssistantMessage am) {
                assistantCount++;
                if (am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
                    toolCount++;
                }
            }
        }

        StringBuilder summary = new StringBuilder();
        summary.append("用户提问").append(userCount).append("次");
        summary.append("，AI回复").append(assistantCount).append("次");
        if (toolCount > 0) {
            summary.append("，工具调用").append(toolCount).append("次");
        }

        return summary.toString();
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
}
