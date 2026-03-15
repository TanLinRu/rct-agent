package com.tlq.rectagent.context;

import com.tlq.rectagent.data.entity.ChatMessage;
import com.tlq.rectagent.data.entity.ChatSession;
import com.tlq.rectagent.data.service.ChatMessageService;
import com.tlq.rectagent.data.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextLoader {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    @Value("${rectagent.context.l1.hot-turns:5}")
    private int l1HotTurns;

    @Value("${rectagent.context.l2.warm-turns:10}")
    private int l2WarmTurns;

    public static final String LAYER_HOT = "L1_HOT";
    public static final String LAYER_WARM = "L2_WARM";
    public static final String LAYER_PROFILE = "L3_PROFILE";

    public Context loadContext(String sessionId, String userId) {
        log.info("开始加载分层上下文: sessionId={}, userId={}", sessionId, userId);

        Context context = new Context();
        context.setSessionId(sessionId);
        context.setUserId(userId);

        ChatSession session = chatSessionService.getSessionById(sessionId);
        if (session == null) {
            log.warn("会话不存在，创建新会话上下文");
            return context;
        }

        context.setSession(session);
        context.setTraceId(session.getTraceId());

        loadHotLayer(context, sessionId);
        loadWarmLayer(context, sessionId);
        loadProfileLayer(context, userId);

        checkAndRecoverFromCheckpoint(context);

        log.info("上下文加载完成: L1={}条, L2={}条, L3={}个标签",
                context.getHotMessages().size(),
                context.getWarmMessages().size(),
                context.getProfileTags().size());

        return context;
    }

    private void loadHotLayer(Context context, String sessionId) {
        List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(sessionId, l1HotTurns * 2);
        Collections.reverse(recentMessages);
        context.setHotMessages(recentMessages);
        log.debug("L1热数据加载: {}条消息", recentMessages.size());
    }

    private void loadWarmLayer(Context context, String sessionId) {
        ChatSession session = context.getSession();
        if (session != null && session.getSummarySnapshot() != null) {
            context.setSessionSummary(session.getSummarySnapshot());
            log.debug("L2温数据加载: 会话摘要={}", session.getSummarySnapshot().substring(0, Math.min(50, session.getSummarySnapshot().length())));
        }
    }

    private void loadProfileLayer(Context context, String userId) {
        List<String> tags = extractProfileTags(userId);
        context.setProfileTags(tags);
        log.debug("L3画像加载: {}个标签", tags.size());
    }

    private List<String> extractProfileTags(String userId) {
        List<String> tags = new ArrayList<>();
        return tags;
    }

    private void checkAndRecoverFromCheckpoint(Context context) {
        String sessionId = context.getSessionId();
        var checkpoint = chatMessageService.getLatestCheckpointBySessionId(sessionId);

        if (checkpoint != null) {
            log.warn("检测到未完成的会话，准备从断点恢复: sessionId={}, step={}",
                    sessionId, checkpoint.getStepIndex());
            context.setCheckpointState(checkpoint.getStateData());
            context.setRequiresRecovery(true);
        }
    }

    public String buildPromptFromContext(Context context) {
        StringBuilder prompt = new StringBuilder();

        if (!context.getProfileTags().isEmpty()) {
            prompt.append("【用户画像】\n");
            prompt.append(String.join(", ", context.getProfileTags()));
            prompt.append("\n\n");
        }

        if (context.getSessionSummary() != null) {
            prompt.append("【会话历史摘要】\n");
            prompt.append(context.getSessionSummary());
            prompt.append("\n\n");
        }

        if (!context.getHotMessages().isEmpty()) {
            prompt.append("【最近对话】\n");
            for (ChatMessage msg : context.getHotMessages()) {
                prompt.append(msg.getRole()).append(": ").append(msg.getContentRaw()).append("\n");
            }
        }

        return prompt.toString();
    }

    @lombok.Data
    public static class Context {
        private String sessionId;
        private String userId;
        private String traceId;
        private ChatSession session;

        private List<ChatMessage> hotMessages = new ArrayList<>();
        private String sessionSummary;
        private List<String> profileTags = new ArrayList<String>();

        private String checkpointState;
        private boolean requiresRecovery;

        private Map<String, Object> metadata = new HashMap<>();
    }

    public static class ContextBuilder {
        private List<String> profileTags = new ArrayList<String>();

        public ContextBuilder profileTags(List<String> profileTags) {
            this.profileTags = profileTags;
            return this;
        }
    }
}
