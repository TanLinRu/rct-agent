package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.tlq.rectagent.profile.ProfileInferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@HookPositions({HookPosition.AFTER_MODEL})
public class ProfileInferenceHook extends ModelHook {

    @Autowired
    private ProfileInferenceService profileInferenceService;

    @Override
    public String getName() {
        return "ProfileInferenceHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        String traceId = config.context() != null
                ? String.valueOf(config.context().getOrDefault("traceId", "unknown"))
                : "unknown";
        String userId = config.context() != null
                ? String.valueOf(config.context().getOrDefault("userId", null))
                : null;

        if (userId == null || "null".equals(userId)) {
            return CompletableFuture.completedFuture(Map.of());
        }

        try {
            String userInput = extractLatestUserMessage(state);
            String aiResponse = extractAiResponse(state);

            if (userInput != null && aiResponse != null) {
                profileInferenceService.inferAndRecord(userId, userInput, aiResponse);
                log.debug("[{}] 画像自动推断完成: userId={}", traceId, userId);
            }
        } catch (Exception e) {
            log.warn("[{}] 画像推断失败: {}", traceId, e.getMessage());
        }

        return CompletableFuture.completedFuture(Map.of());
    }

    private String extractLatestUserMessage(OverAllState state) {
        try {
            Optional<Object> messagesOpt = state.value("messages");
            if (messagesOpt.isEmpty()) return null;
            Object messages = messagesOpt.get();
            if (messages instanceof List<?> list) {
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object msg = list.get(i);
                    String role = getMessageRole(msg);
                    if ("user".equalsIgnoreCase(role)) {
                        return getMessageText(msg);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract user message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAiResponse(OverAllState state) {
        try {
            Optional<Object> lastMsgOpt = state.value("messages");
            if (lastMsgOpt.isEmpty()) return null;
            Object messages = lastMsgOpt.get();
            if (messages instanceof List<?> list && !list.isEmpty()) {
                Object last = list.get(list.size() - 1);
                String role = getMessageRole(last);
                if ("assistant".equalsIgnoreCase(role)) {
                    return getMessageText(last);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract AI response: {}", e.getMessage());
        }
        return null;
    }

    private String getMessageRole(Object msg) {
        try {
            return msg.getClass().getMethod("getRole").invoke(msg).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getMessageText(Object msg) {
        try {
            Object result = msg.getClass().getMethod("getText").invoke(msg);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            try {
                Object result = msg.getClass().getMethod("getContent").invoke(msg);
                return result != null ? result.toString() : null;
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
