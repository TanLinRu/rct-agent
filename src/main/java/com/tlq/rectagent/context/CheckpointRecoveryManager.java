package com.tlq.rectagent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlq.rectagent.data.entity.ChatSession;
import com.tlq.rectagent.data.entity.ConversationCheckpoint;
import com.tlq.rectagent.data.entity.ToolExecution;
import com.tlq.rectagent.data.service.ChatSessionService;
import com.tlq.rectagent.data.service.ConversationCheckpointService;
import com.tlq.rectagent.data.service.ToolExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointRecoveryManager {

    private final ConversationCheckpointService checkpointService;
    private final ChatSessionService chatSessionService;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;

    public static final String STATE_MCP_CALLING = "MCP_CALLING";
    public static final String STATE_MCP_COMPLETED = "MCP_COMPLETED";
    public static final String STATE_PROCESSING = "PROCESSING";
    public static final String STATE_ABORTED = "ABORTED";

    public RecoveryResult checkAndRecover(String sessionId) {
        log.info("检查断点恢复: sessionId={}", sessionId);

        ChatSession session = chatSessionService.getSessionById(sessionId);
        if (session == null) {
            return RecoveryResult.noRecovery("会话不存在");
        }

        if (!"ABORTED".equals(session.getStatus())) {
            return RecoveryResult.noRecovery("会话状态正常");
        }

        ConversationCheckpoint checkpoint = checkpointService.getLatestCheckpoint(sessionId);
        if (checkpoint == null) {
            return RecoveryResult.noRecovery("无断点记录");
        }

        log.warn("检测到中断会话，准备恢复: step={}", checkpoint.getStepIndex());

        try {
            Map<String, Object> stateData = objectMapper.readValue(
                    checkpoint.getStateData(), Map.class);

            RecoveryResult result = new RecoveryResult();
            result.setRecoverable(true);
            result.setCheckpointId(checkpoint.getCheckpointId());
            result.setStepIndex(checkpoint.getStepIndex());
            result.setStateData(stateData);
            result.setMessageId(checkpoint.getMessageId());

            String currentState = (String) stateData.getOrDefault("status", STATE_PROCESSING);
            result.setCurrentState(currentState);

            if (STATE_MCP_CALLING.equals(currentState)) {
                String toolName = (String) stateData.getOrDefault("toolName", "");
                List<ToolExecution> successfulExecutions = toolExecutionService.getExecutionsByMessageId(
                        checkpoint.getMessageId());

                if (!successfulExecutions.isEmpty()) {
                    result.setCanSkipMcp(true);
                    result.setToolExecutions(successfulExecutions);
                    log.info("发现已成功的MCP调用，可跳过: tool={}", toolName);
                }
            }

            log.info("断点恢复检查完成: 可恢复={}, 可跳过MCP={}",
                    result.isRecoverable(), result.isCanSkipMcp());

            return result;

        } catch (Exception e) {
            log.error("解析断点数据失败", e);
            return RecoveryResult.noRecovery("断点数据解析失败: " + e.getMessage());
        }
    }

    public void markCheckpointResumed(String checkpointId) {
        checkpointService.markAsResumed(checkpointId);
        log.info("断点已标记为已恢复: checkpointId={}", checkpointId);
    }

    public void createCheckpoint(String sessionId, String messageId,
                                  String status, String toolName, int stepIndex) {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("status", status);
        stateData.put("toolName", toolName);
        stateData.put("timestamp", System.currentTimeMillis());
        stateData.put("sessionId", sessionId);

        try {
            String stateJson = objectMapper.writeValueAsString(stateData);
            checkpointService.createCheckpoint(sessionId, messageId, stateJson, stepIndex);
            log.info("创建断点: sessionId={}, step={}, status={}", sessionId, stepIndex, status);
        } catch (Exception e) {
            log.error("创建断点失败", e);
        }
    }

    public void abortSession(String sessionId, String reason) {
        chatSessionService.endSession(sessionId, STATE_ABORTED);
        log.warn("会话标记为中断: sessionId={}, reason={}", sessionId, reason);
    }

    public void resumeSession(String sessionId) {
        chatSessionService.updateSessionSummary(sessionId, "[已从断点恢复]");
        log.info("会话已恢复: sessionId={}", sessionId);
    }

    @lombok.Data
    public static class RecoveryResult {
        private boolean recoverable;
        private String reason;
        private String checkpointId;
        private String messageId;
        private int stepIndex;
        private Map<String, Object> stateData;
        private String currentState;
        private boolean canSkipMcp;
        private List<ToolExecution> toolExecutions;

        public static RecoveryResult noRecovery(String reason) {
            RecoveryResult result = new RecoveryResult();
            result.setRecoverable(false);
            result.setReason(reason);
            return result;
        }
    }
}
