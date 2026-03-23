package com.tlq.rectagent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.tlq.rectagent.profile.ProfileInferenceService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@HookPositions({HookPosition.AFTER_MODEL})
public class ProfileInferenceHook extends ModelHook {

    private static final String USER_ID_KEY = "userId";

    @Autowired
    private ProfileInferenceService profileInferenceService;

    @Override
    public String getName() {
        return "ProfileInferenceHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = "unknown";
        }

        String userId = extractUserId(config);
        if (userId == null || "null".equals(userId)) {
            log.debug("[{}] ProfileInferenceHook: 缺少 userId，跳过画像推断", traceId);
            return CompletableFuture.completedFuture(Map.of());
        }

        log.info("[{}] ProfileInferenceHook: 执行画像推断 userId={}", traceId, userId);

        try {
            String userInput = extractLatestUserMessage(state);
            String aiResponse = extractAiResponse(state);

            if (userInput != null && aiResponse != null) {
                profileInferenceService.inferAndRecord(userId, userInput, aiResponse);
                log.info("[{}] 画像自动推断完成: userId={}", traceId, userId);
            } else {
                log.debug("[{}] 画像推断: 未提取到有效消息对，跳过", traceId);
            }
        } catch (Exception e) {
            log.warn("[{}] 画像推断失败: {}", traceId, e.getMessage());
        }

        return CompletableFuture.completedFuture(Map.of());
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

    private String extractUserId(RunnableConfig config) {
        String userId = extractFromContext(config, USER_ID_KEY);
        if (userId == null) {
            userId = MDC.get(USER_ID_KEY);
        }
        return userId;
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
