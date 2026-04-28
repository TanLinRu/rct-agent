package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.tlq.rectagent.context.ClawCodeSessionCompactor;
import com.tlq.rectagent.context.SessionContextKeys;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class ClawCodeCompressionHook extends MessagesModelHook {

    private static final String TRACE_ID_KEY = "traceId";

    @Autowired
    private ClawCodeSessionCompactor compactor;

    @Value("${rectagent.hook.compression.enabled:false}")
    private boolean enabled;

    @Value("${rectagent.compaction.max-tokens:10000}")
    private int maxTokens;

    @Override
    public String getName() {
        return "ClawCodeCompressionHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String traceId = getTraceId(config);
        
        if (!enabled) {
            log.debug("[{}] ClawCodeCompressionHook: disabled, skipping", traceId);
            return new AgentCommand(previousMessages);
        }

        if (previousMessages == null || previousMessages.isEmpty()) {
            log.debug("[{}] ClawCodeCompressionHook: no messages, skipping", traceId);
            return new AgentCommand(previousMessages);
        }

        try {
            Integer previousTokens = (Integer) config.context().get(SessionContextKeys.ESTIMATED_TOKENS);
            int currentTokens = previousTokens != null ? previousTokens : compactor.estimateTokens(previousMessages);

            log.info("[{}] ClawCodeCompressionHook: beforeModel - messages={}, estimatedTokens={}, maxTokens={}",
                    traceId, previousMessages.size(), currentTokens, maxTokens);
            
            log.info("[{}] ===== Messages BEFORE compression =====", traceId);
            for (int i = 0; i < previousMessages.size(); i++) {
                Message m = previousMessages.get(i);
                log.info("[{}] [{}] {}:\n{}", traceId, i, m.getClass().getSimpleName(), m.getText());
            }

            if (currentTokens > maxTokens) {
                Boolean hasCompacted = (Boolean) config.context().get(SessionContextKeys.HAS_COMPACTED);
                if (Boolean.TRUE.equals(hasCompacted)) {
                    log.debug("[{}] Already compacted in this turn, skipping", traceId);
                    return new AgentCommand(previousMessages);
                }

                List<Message> compressed = compactor.compact(previousMessages);
                config.context().put(SessionContextKeys.HAS_COMPACTED, true);

                int compressedTokens = compactor.estimateTokens(compressed);
                config.context().put(SessionContextKeys.ESTIMATED_TOKENS, compressedTokens);

                log.info("[{}] ClawCode compression completed: {} tokens -> {}, messages: {} -> {}",
                        traceId, currentTokens, compressedTokens,
                        previousMessages.size(), compressed.size());
                
                log.info("[{}] ===== Messages AFTER compression =====", traceId);
                for (int i = 0; i < compressed.size(); i++) {
                    Message m = compressed.get(i);
                    log.info("[{}] [{}] {}:\n{}", traceId, i, m.getClass().getSimpleName(), m.getText());
                }

                return new AgentCommand(compressed, UpdatePolicy.REPLACE);
            }

            log.debug("[{}] No compression needed: {} <= {}", traceId, currentTokens, maxTokens);
            config.context().put(SessionContextKeys.ESTIMATED_TOKENS, currentTokens);

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
}