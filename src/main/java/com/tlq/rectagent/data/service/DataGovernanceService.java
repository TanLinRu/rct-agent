package com.tlq.rectagent.data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlq.rectagent.data.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataGovernanceService {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ToolExecutionService toolExecutionService;
    private final ProfileChangeService profileChangeService;
    private final ConversationCheckpointService checkpointService;
    private final ObjectMapper objectMapper;

    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public ChatSession startNewSession(String userId) {
        String traceId = generateTraceId();
        return chatSessionService.createSession(userId, traceId);
    }

    public ChatSession getOrCreateSession(String userId, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return startNewSession(userId);
        }
        ChatSession session = chatSessionService.getSessionById(sessionId);
        if (session == null) {
            return startNewSession(userId);
        }
        return session;
    }

    public ChatMessage recordUserMessage(String sessionId, String contentRaw, 
                                         String contentProcessed, String tokenUsage) {
        int turnIndex = chatMessageService.getMaxTurnIndex(sessionId) + 1;
        ChatMessage message = chatMessageService.saveMessage(
                sessionId, turnIndex, ChatMessage.Role.USER.name(), contentRaw, contentProcessed);
        if (tokenUsage != null) {
            message.setTokenUsage(tokenUsage);
            chatMessageService.updateById(message);
        }
        return message;
    }

    public ChatMessage recordAssistantMessage(String sessionId, String contentRaw, 
                                               String contentProcessed, String tokenUsage) {
        int turnIndex = chatMessageService.getMaxTurnIndex(sessionId) + 1;
        ChatMessage message = chatMessageService.saveMessage(
                sessionId, turnIndex, ChatMessage.Role.ASSISTANT.name(), contentRaw, contentProcessed);
        if (tokenUsage != null) {
            message.setTokenUsage(tokenUsage);
            chatMessageService.updateById(message);
        }
        return message;
    }

    public ToolExecution startToolExecution(String messageId, String mcpServerName, 
                                              String toolName, String requestPayload) {
        return toolExecutionService.createExecution(messageId, mcpServerName, toolName, requestPayload);
    }

    public void completeToolExecution(String executionId, String responsePayload, long durationMs) {
        toolExecutionService.updateExecutionSuccess(executionId, responsePayload, durationMs);
    }

    public void failToolExecution(String executionId, String errorMessage) {
        toolExecutionService.updateExecutionFailed(executionId, errorMessage);
    }

    public void timeoutToolExecution(String executionId) {
        toolExecutionService.updateExecutionTimeout(executionId);
    }

    public void createCheckpoint(String sessionId, String messageId, Object stateData, int stepIndex) {
        try {
            String stateJson = objectMapper.writeValueAsString(stateData);
            checkpointService.createCheckpoint(sessionId, messageId, stateJson, stepIndex);
        } catch (Exception e) {
            log.error("Failed to serialize checkpoint state", e);
        }
    }

    public ConversationCheckpoint getLatestCheckpoint(String sessionId) {
        return checkpointService.getLatestCheckpoint(sessionId);
    }

    public void markCheckpointResumed(String checkpointId) {
        checkpointService.markAsResumed(checkpointId);
    }

    public void updateSessionSummary(String sessionId, String summary) {
        chatSessionService.updateSessionSummary(sessionId, summary);
    }

    public void updateSessionTokens(String sessionId, int tokens) {
        chatSessionService.updateSessionTokens(sessionId, tokens);
    }

    public void endSession(String sessionId, String status) {
        chatSessionService.endSession(sessionId, status);
    }

    public void recordProfileChange(String userId, String fieldName, 
                                    String oldValue, String newValue, String reasoning) {
        profileChangeService.recordChange(userId, fieldName, oldValue, newValue, reasoning);
    }

    public List<ChatMessage> getSessionMessages(String sessionId) {
        return chatMessageService.getMessagesBySessionId(sessionId);
    }

    public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        return chatMessageService.getRecentMessages(sessionId, limit);
    }

    public List<ToolExecution> getToolExecutions(String messageId) {
        return toolExecutionService.getExecutionsByMessageId(messageId);
    }

    public List<ProfileChange> getProfileChanges(String userId) {
        return profileChangeService.getChangesByUserId(userId);
    }
}
