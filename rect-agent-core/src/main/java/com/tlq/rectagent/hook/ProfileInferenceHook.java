package com.tlq.rectagent.hook;

import com.tlq.rectagent.profile.ProfileInferenceService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ProfileInferenceHook {

    private static final String USER_ID_KEY = "userId";
    private static final String TRACE_ID_KEY = "traceId";

    @Autowired
    private ProfileInferenceService profileInferenceService;

    public String getName() {
        return "ProfileInferenceHook";
    }

    public void afterModel(List<Message> messages, String userId) {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = "unknown";
        }

        if (userId == null || "null".equals(userId)) {
            log.debug("[{}] ProfileInferenceHook: 缺少 userId，跳过画像推断", traceId);
            return;
        }

        log.info("[{}] ProfileInferenceHook: 执行画像推断 userId={}", traceId, userId);

        try {
            String userInput = extractLatestUserMessage(messages);
            String aiResponse = extractAiResponse(messages);

            if (userInput != null && aiResponse != null) {
                profileInferenceService.inferAndRecord(userId, userInput, aiResponse);
                log.info("[{}] 画像自动推断完成: userId={}", traceId, userId);
            } else {
                log.debug("[{}] 画像推断: 未提取到有效消息对，跳过", traceId);
            }
        } catch (Exception e) {
            log.warn("[{}] 画像推断失败: {}", traceId, e.getMessage());
        }
    }

    private String extractLatestUserMessage(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                return msg.getText();
            }
        }
        return null;
    }

    private String extractAiResponse(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage am) {
                return am.getText();
            }
        }
        return null;
    }
}
