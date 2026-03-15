package com.tlq.rectagent.context;

import com.tlq.rectagent.data.entity.ChatMessage;
import com.tlq.rectagent.data.service.DataGovernanceService;
import com.tlq.rectagent.memory.ShortTermMemoryManager;
import com.tlq.rectagent.memory.LongTermMemoryManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextManager {

    @Autowired
    private ShortTermMemoryManager shortTermMemoryManager;

    @Autowired
    private LongTermMemoryManager longTermMemoryManager;

    private final ContextLoader contextLoader;
    private final TokenBudgetManager tokenBudgetManager;
    private final CheckpointRecoveryManager checkpointRecoveryManager;
    private final PromptVersionManager promptVersionManager;
    private final MessagePurifier messagePurifier;
    private final DataGovernanceService dataGovernanceService;

    private final Map<String, Map<String, Object>> contextStore;

    public ContextManager() {
        this.contextStore = new HashMap<>();
    }

    public ContextLoader.Context loadSessionContext(String sessionId, String userId) {
        log.info("加载会话上下文: sessionId={}, userId={}", sessionId, userId);

        CheckpointRecoveryManager.RecoveryResult recovery = checkpointRecoveryManager.checkAndRecover(sessionId);

        ContextLoader.Context context = contextLoader.loadContext(sessionId, userId);

        if (recovery.isRecoverable()) {
            context.setRequiresRecovery(true);
            context.setRecoveryState(recovery.getStateData());
            log.info("检测到需要从断点恢复");
        }

        return context;
    }

    public String buildAgentPrompt(String agentType, ContextLoader.Context sessionContext) {
        String basePrompt = promptVersionManager.getPrompt(agentType);

        String contextPrompt = contextLoader.buildPromptFromContext(sessionContext);

        List<ChatMessage> allMessages = sessionContext.getHotMessages();
        if (allMessages.size() > 10) {
            TokenBudgetManager.CompressedContext compressed =
                    tokenBudgetManager.compressContext(allMessages, basePrompt);
            contextPrompt = contextLoader.buildPromptFromContext(sessionContext);
        }

        return basePrompt + "\n\n" + contextPrompt;
    }

    public void recordCheckpoint(String sessionId, String messageId, String status,
                                String toolName, int stepIndex) {
        checkpointRecoveryManager.createCheckpoint(sessionId, messageId, status, toolName, stepIndex);
    }

    public void recordMessage(String sessionId, String role, String content) {
        String sanitizedContent = messagePurifier.sanitizeUserInput(content);

        if ("USER".equals(role)) {
            dataGovernanceService.recordUserMessage(sessionId, content, sanitizedContent, null);
        } else if ("ASSISTANT".equals(role)) {
            dataGovernanceService.recordAssistantMessage(sessionId, content, sanitizedContent, null);
        }
    }

    public String purifyAgentOutput(String agentOutput) {
        MessagePurifier.PurifiedMessage purified = messagePurifier.purifyForInterAgent(agentOutput);
        return purified.getObservation();
    }

    public void saveSessionSummary(String sessionId, String summary) {
        dataGovernanceService.updateSessionSummary(sessionId, summary);
        shortTermMemoryManager.addMessage(sessionId, summary, "SYSTEM");
    }

    public Map<String, Object> createContext(String contextId) {
        Map<String, Object> context = new HashMap<>();
        contextStore.put(contextId, context);
        return context;
    }

    public Map<String, Object> getContext(String contextId) {
        return contextStore.computeIfAbsent(contextId, k -> new HashMap<>());
    }

    public void updateContext(String contextId, String key, Object value) {
        Map<String, Object> context = getContext(contextId);
        context.put(key, value);
    }

    public Object getContextValue(String contextId, String key) {
        Map<String, Object> context = getContext(contextId);
        return context.get(key);
    }

    public void mergeContext(String sourceContextId, String targetContextId) {
        Map<String, Object> sourceContext = getContext(sourceContextId);
        Map<String, Object> targetContext = getContext(targetContextId);
        targetContext.putAll(sourceContext);
    }

    public void clearContext(String contextId) {
        contextStore.remove(contextId);
    }

    public void saveContextToShortTermMemory(String sessionId, String contextId) {
        Map<String, Object> context = getContext(contextId);
        shortTermMemoryManager.saveSessionState(sessionId, context);
    }

    public void loadContextFromShortTermMemory(String sessionId, String contextId) {
        shortTermMemoryManager.getSessionState(sessionId).ifPresent(storeItem -> {
            Map<String, Object> context = getContext(contextId);
            log.info("从短期记忆加载会话状态: {}", sessionId);
        });
    }

    public void saveContextToLongTermMemory(String userId, String contextId, String key) {
        Map<String, Object> context = getContext(contextId);
        Object value = context.get(key);
        if (value != null) {
            Map<String, Object> data = new HashMap<>();
            data.put(key, value);
            longTermMemoryManager.updateUserProfile(userId, data);
        }
    }

    public void loadContextFromLongTermMemory(String userId, String contextId, String key) {
        longTermMemoryManager.getUserProfile(userId).ifPresent(storeItem -> {
            Map<String, Object> context = getContext(contextId);
            log.info("从长期记忆加载用户配置: {}", userId);
        });
    }
}