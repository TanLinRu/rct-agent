package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.tlq.rectagent.data.service.DataGovernanceService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@HookPositions({HookPosition.AFTER_MODEL})
public class MessagePersistenceHook extends MessagesModelHook {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String USER_ID_KEY = "userId";

    @Autowired(required = false)
    private DataGovernanceService dataGovernanceService;

    @Override
    public String getName() {
        return "MessagePersistenceHook";
    }

    @Override
    public AgentCommand afterModel(List<Message> messages, RunnableConfig config) {
        String traceId = getTraceId(config);

        if (dataGovernanceService == null) {
            log.debug("[{}] MessagePersistenceHook: DataGovernanceService not available, skipping", traceId);
            return new AgentCommand(messages);
        }

        String sessionId = getSessionId(config);

        if (sessionId == null) {
            log.debug("[{}] MessagePersistenceHook: missing sessionId, skipping", traceId);
            return new AgentCommand(messages);
        }

        try {
            log.info("[{}] MessagePersistenceHook: persisting {} messages - sessionId={}",
                    traceId, messages.size(), sessionId);

            for (Message msg : messages) {
                persistMessage(sessionId, msg, traceId);
            }

            log.info("[{}] MessagePersistenceHook: completed, persisted {} messages",
                    traceId, messages.size());

        } catch (Exception e) {
            log.warn("[{}] Message persistence failed: {}", traceId, e.getMessage());
        }

        return new AgentCommand(messages);
    }

    private void persistMessage(String sessionId, Message msg, String traceId) {
        try {
            String content = msg.getText();
            if (content == null || content.isBlank()) {
                return;
            }

            if (msg instanceof AssistantMessage) {
                dataGovernanceService.recordAssistantMessage(sessionId, content, content, null);
                log.debug("[{}] Persisted ASSISTANT message: {} chars", traceId, content.length());
            }

        } catch (Exception e) {
            log.warn("[{}] Failed to persist message: {}", traceId, e.getMessage());
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