package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.tlq.rectagent.context.ContextLoader;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Slf4j
@HookPositions({HookPosition.BEFORE_MODEL})
public class ContextInjectionHook extends MessagesModelHook {

    private static final String SESSION_ID_KEY = "sessionId";
    private static final String USER_ID_KEY = "userId";

    @Autowired
    private ContextLoader contextLoader;

    @Value("${rectagent.hook.context.prefix:你是一位专业的数据安全分析助手。以下是用户上下文信息，供你参考：}")
    private String contextPrefix;

    @Override
    public String getName() {
        return "ContextInjectionHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = "unknown";
        }

        String sessionId = extractSessionId(config);
        String userId = extractUserId(config);

        if (sessionId == null || userId == null || "null".equals(sessionId) || "null".equals(userId)) {
            log.debug("[{}] ContextInjectionHook: 缺少 sessionId/userId，跳过上下文注入", traceId);
            return new AgentCommand(previousMessages);
        }

        log.info("[{}] ContextInjectionHook: 加载上下文 sessionId={}, userId={}", traceId, sessionId, userId);

        try {
            ContextLoader.Context ctx = contextLoader.loadContext(sessionId, userId);
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append(contextPrefix).append("\n\n");

            if (ctx.getProfileTags() != null && !ctx.getProfileTags().isEmpty()) {
                contextBuilder.append("【用户画像】")
                        .append(String.join(", ", ctx.getProfileTags()))
                        .append("\n");
            }
            if (ctx.getSessionSummary() != null) {
                contextBuilder.append("【会话摘要】")
                        .append(ctx.getSessionSummary())
                        .append("\n");
            }
            if (ctx.getHotMessages() != null && !ctx.getHotMessages().isEmpty()) {
                contextBuilder.append("【最近对话】\n");
                ctx.getHotMessages().forEach(msg ->
                        contextBuilder.append(msg.getRole())
                                .append(": ")
                                .append(msg.getContentRaw())
                                .append("\n"));
            }

            String contextText = contextBuilder.toString();
            if (contextText.equals(contextPrefix + "\n\n")) {
                return new AgentCommand(previousMessages);
            }

            SystemMessage contextMsg = new SystemMessage(contextText);
            List<Message> enhanced = new java.util.ArrayList<>();
            enhanced.add(contextMsg);
            enhanced.addAll(previousMessages);

            log.debug("[{}] 上下文注入完成: {}条原始消息 + 1条上下文消息",
                    traceId, previousMessages.size());
            return new AgentCommand(enhanced, UpdatePolicy.REPLACE);

        } catch (Exception e) {
            log.warn("[{}] 上下文注入失败，跳过: {}", traceId, e.getMessage());
            return new AgentCommand(previousMessages);
        }
    }

    private String extractFromContext(RunnableConfig config, String key) {
        if (config != null && config.context() != null) {
            Object value = config.context().get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String extractSessionId(RunnableConfig config) {
        String sessionId = extractFromContext(config, SESSION_ID_KEY);
        if (sessionId == null) {
            sessionId = MDC.get(SESSION_ID_KEY);
        }
        return sessionId;
    }

    private String extractUserId(RunnableConfig config) {
        String userId = extractFromContext(config, USER_ID_KEY);
        if (userId == null) {
            userId = MDC.get(USER_ID_KEY);
        }
        return userId;
    }
}
