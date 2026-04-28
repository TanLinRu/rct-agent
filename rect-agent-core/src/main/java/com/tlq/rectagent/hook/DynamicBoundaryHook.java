package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.tlq.rectagent.context.ContextLoader;
import com.tlq.rectagent.context.SessionContextKeys;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class DynamicBoundaryHook extends MessagesModelHook {

    private static final String TRACE_ID_KEY = "traceId";
    public static final String DYNAMIC_BOUNDARY = "__SYSTEM_PROMPT_DYNAMIC_BOUNDARY__";

    @Autowired
    private ContextLoader contextLoader;

    @Value("${rectagent.hook.dynamic-boundary.enabled:false}")
    private boolean enabled;

    @Override
    public String getName() {
        return "DynamicBoundaryHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String traceId = getTraceId(config);
        
        if (!enabled) {
            log.debug("[{}] DynamicBoundaryHook: disabled, skipping", traceId);
            return new AgentCommand(previousMessages);
        }

        SystemMessage systemMsg = findSystemMessage(previousMessages);
        if (systemMsg == null) {
            log.debug("[{}] DynamicBoundaryHook: no system message found", traceId);
            return new AgentCommand(previousMessages);
        }

        String content = systemMsg.getText();
        if (!content.contains(DYNAMIC_BOUNDARY)) {
            log.debug("[{}] DynamicBoundaryHook: no dynamic boundary marker found", traceId);
            return new AgentCommand(previousMessages);
        }

        try {
            log.info("[{}] DynamicBoundaryHook: processing - messages={}, systemMessage length={}",
                    traceId, previousMessages.size(), content.length());
            
            log.info("[{}] ===== Original messages =====", traceId);
            for (int i = 0; i < previousMessages.size(); i++) {
                Message m = previousMessages.get(i);
                log.info("[{}] [{}] {}:\n{}", traceId, i, m.getClass().getSimpleName(), m.getText());
            }
            
            String dynamicContent = buildDynamicContent(config, traceId);
            String newContent = content.replace(DYNAMIC_BOUNDARY, dynamicContent);

            List<Message> result = previousMessages.stream()
                .filter(m -> !(m instanceof SystemMessage))
                .collect(Collectors.toList());
            result.add(0, new SystemMessage(newContent));

            log.info("[{}] Dynamic boundary injected: systemMessage {} -> {} chars",
                    traceId, content.length(), newContent.length());
            
            log.info("[{}] ===== Messages AFTER dynamic boundary injection =====", traceId);
            for (int i = 0; i < result.size(); i++) {
                Message m = result.get(i);
                log.info("[{}] [{}] {}:\n{}", traceId, i, m.getClass().getSimpleName(), m.getText());
            }
            
            return new AgentCommand(result, UpdatePolicy.REPLACE);

        } catch (Exception e) {
            log.warn("[{}] Dynamic boundary injection failed: {}", traceId, e.getMessage());
            return new AgentCommand(previousMessages);
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
    
    private String buildDynamicContent(RunnableConfig config, String traceId) {
        String sessionId = (String) config.context().get(SessionContextKeys.SESSION_ID);
        String userId = (String) config.context().get(SessionContextKeys.USER_ID);

        if (sessionId == null || userId == null) {
            log.debug("[{}] sessionId or userId not found in context", traceId);
            return "";
        }

        log.debug("[{}] Loading context for sessionId={}, userId={}", traceId, sessionId, userId);
        
        ContextLoader.Context ctx = contextLoader.loadContext(sessionId, userId);

        StringBuilder dynamic = new StringBuilder();

        String compactSummary = (String) config.context().get(SessionContextKeys.COMPACT_SUMMARY);
        if (compactSummary != null && !compactSummary.isEmpty()) {
            dynamic.append("【历史摘要】\n").append(compactSummary).append("\n");
        }

        if (ctx.getSessionSummary() != null && !ctx.getSessionSummary().isEmpty()) {
            dynamic.append("【会话摘要】\n").append(ctx.getSessionSummary()).append("\n");
        }

        if (ctx.getProfileTags() != null && !ctx.getProfileTags().isEmpty()) {
            dynamic.append("【用户画像】").append(String.join(", ", ctx.getProfileTags())).append("\n");
        }

        return dynamic.toString();
    }

    private SystemMessage findSystemMessage(List<Message> messages) {
        for (Message msg : messages) {
            if (msg instanceof SystemMessage sm) {
                return sm;
            }
        }
        return null;
    }
    
    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}