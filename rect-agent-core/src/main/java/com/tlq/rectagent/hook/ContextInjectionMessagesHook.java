package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.tlq.rectagent.context.ContextLoader;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class ContextInjectionMessagesHook extends MessagesModelHook {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String USER_ID_KEY = "userId";

    @Autowired(required = false)
    private ContextLoader contextLoader;

    @Value("${rectagent.hook.context.prefix:你是一位专业的数据安全分析助手。以下是用户上下文信息，供你参考：}")
    private String contextPrefix;

    private static final int MAX_CONTEXT_LENGTH = 8000;

    @Override
    public String getName() {
        return "ContextInjectionMessagesHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String traceId = getTraceId(config);

        if (contextLoader == null) {
            log.debug("[{}] ContextInjectionMessagesHook: ContextLoader not available, skipping", traceId);
            return new AgentCommand(previousMessages);
        }

        String sessionId = getSessionId(config);
        String userId = getUserId(config);

        if (sessionId == null || userId == null) {
            log.debug("[{}] ContextInjectionMessagesHook: missing sessionId/userId, skipping", traceId);
            return new AgentCommand(previousMessages);
        }

        try {
            log.info("[{}] ContextInjectionMessagesHook: loading context - sessionId={}, userId={}, messages={}",
                    traceId, sessionId, userId, previousMessages.size());

            logMessages(traceId, "BEFORE", previousMessages);

            ContextLoader.Context ctx = contextLoader.loadContext(sessionId, userId);

            List<Message> historyMessages = buildHistoryMessages(ctx);
            if (historyMessages.isEmpty()) {
                log.debug("[{}] ContextInjectionMessagesHook: no history to inject", traceId);
                return new AgentCommand(previousMessages);
            }

            List<Message> result = buildMessageList(historyMessages, previousMessages);

            logMessages(traceId, "AFTER", result);
            log.info("[{}] Context injection completed: history={}, current={}",
                    traceId, historyMessages.size(), previousMessages.size());

            return new AgentCommand(result, UpdatePolicy.REPLACE);

        } catch (Exception e) {
            log.warn("[{}] Context injection failed: {}", traceId, e.getMessage());
            return new AgentCommand(previousMessages);
        }
    }

    private List<Message> buildHistoryMessages(ContextLoader.Context ctx) {
        List<Message> history = new ArrayList<>();

        if (ctx.getHotMessages() != null && !ctx.getHotMessages().isEmpty()) {
            for (var msg : ctx.getHotMessages()) {
                String content = msg.getContentRaw();
                if (content == null || content.isBlank()) continue;

                Message message = switch (msg.getRole().toUpperCase()) {
                    case "USER" -> new UserMessage(content);
                    case "ASSISTANT" -> new AssistantMessage(content);
                    case "SYSTEM" -> new SystemMessage(content);
                    default -> new UserMessage(content);
                };
                history.add(message);
            }
        }

        return history;
    }

    private List<Message> buildMessageList(List<Message> history, List<Message> current) {
        List<Message> result = new ArrayList<>();

        String contextText = buildContextText(history);
        if (contextText != null && !contextText.isBlank()) {
            result.add(new SystemMessage(contextText));
        }

        result.addAll(history);
        result.addAll(current);

        return result;
    }

    private String buildContextText(List<Message> historyMessages) {
        if (historyMessages.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(contextPrefix).append("\n\n");

        sb.append("【历史对话】\n");
        for (Message msg : historyMessages) {
            String roleLabel = switch (msg.getMessageType().toString().toUpperCase()) {
                case "USER" -> "用户";
                case "ASSISTANT" -> "助手";
                case "SYSTEM" -> "系统";
                default -> msg.getMessageType().toString();
            };
            sb.append(roleLabel).append(": ").append(msg.getText()).append("\n");
        }

        String result = sb.toString();
        if (result.length() > MAX_CONTEXT_LENGTH) {
            log.warn("Context text too long ({} chars), truncating to {} chars",
                    result.length(), MAX_CONTEXT_LENGTH);
            result = result.substring(0, MAX_CONTEXT_LENGTH) + "\n...[已截断]";
        }

        return result;
    }

    private void logMessages(String traceId, String phase, List<Message> messages) {
        log.info("[{}] ===== Messages {} context injection =====", traceId, phase);
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String type = m.getClass().getSimpleName();
            String text = m.getText();
            int maxLen = 200;
            String truncated = text.length() > maxLen
                    ? text.substring(0, maxLen) + "..."
                    : text;
            log.info("[{}] [{}] {}:\n{}", traceId, i, type, truncated);
        }
    }

    private String getTraceId(RunnableConfig config) {
        var traceIdObj = config.context().get(TRACE_ID_KEY);
        if (traceIdObj != null) {
            return traceIdObj.toString();
        }
        String mdcTraceId = MDC.get(TRACE_ID_KEY);
        return mdcTraceId != null ? mdcTraceId : "unknown";
    }

    private String getSessionId(RunnableConfig config) {
        var sessionIdObj = config.context().get(SESSION_ID_KEY);
        if (sessionIdObj != null) {
            return sessionIdObj.toString();
        }
        return null;
    }

    private String getUserId(RunnableConfig config) {
        var userIdObj = config.context().get(USER_ID_KEY);
        if (userIdObj != null) {
            return userIdObj.toString();
        }
        return null;
    }
}