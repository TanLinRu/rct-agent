package com.tlq.rectagent.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlq.rectagent.data.entity.*;
import com.tlq.rectagent.data.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DataGovernanceFullFlowTest {

    @Autowired
    private DataGovernanceService dataGovernanceService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ToolExecutionService toolExecutionService;

    @Autowired
    private ProfileChangeService profileChangeService;

    @Autowired
    private ConversationCheckpointService checkpointService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USER_ID = "test_user_001";

    @Test
    public void testFullConversationFlow() throws Exception {
        System.out.println("========== 开始全链路测试 ==========\n");

        // ============================================
        // 场景1: 新用户创建会话
        // ============================================
        System.out.println("【场景1】新用户创建会话");
        ChatSession session = dataGovernanceService.startNewSession(TEST_USER_ID);
        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertNotNull(session.getTraceId());
        assertEquals(TEST_USER_ID, session.getUserId());
        assertEquals("NORMAL", session.getStatus());
        System.out.println("✓ 会话创建成功: sessionId=" + session.getSessionId());
        System.out.println("✓ TraceID: " + session.getTraceId());

        // ============================================
        // 场景2: 用户发送第一条消息
        // ============================================
        System.out.println("\n【场景2】用户发送第一条消息");
        String userInput1 = "你好，我想了解数据分析功能";
        String processedInput1 = "[脱敏处理]" + userInput1;
        String tokenUsage1 = "{\"input\": 50, \"output\": 0, \"total\": 50}";

        ChatMessage userMessage1 = dataGovernanceService.recordUserMessage(
                session.getSessionId(), userInput1, processedInput1, tokenUsage1);
        assertNotNull(userMessage1);
        assertEquals(1, userMessage1.getTurnIndex());
        assertEquals("USER", userMessage1.getRole());
        System.out.println("✓ 用户消息保存成功: turnIndex=" + userMessage1.getTurnIndex());

        // ============================================
        // 场景3: AI 助手回复（模拟Tool调用前创建断点）
        // ============================================
        System.out.println("\n【场景3】AI助手处理 - 创建断点");

        // 模拟 Agent 处理过程中的状态
        Map<String, Object> agentState = Map.of(
                "currentStep", 1,
                "intent", "data_analysis",
                "hasToolCall", true,
                "toolName", "queryDatabase"
        );

        // 在Tool调用前创建断点（实现断点恢复）
        dataGovernanceService.createCheckpoint(
                session.getSessionId(),
                userMessage1.getMessageId(),
                agentState,
                1
        );
        System.out.println("✓ 断点创建成功，用于Tool调用前的状态保存");

        // ============================================
        // 场景4: 工具执行（模拟MCP调用）
        // ============================================
        System.out.println("\n【场景4】工具执行 - MCP调用模拟");

        // 开始工具执行
        String toolRequest = "{\"query\": \"SELECT * FROM sales WHERE date > '2024-01-01'\"}";
        ToolExecution execution = dataGovernanceService.startToolExecution(
                userMessage1.getMessageId(),
                "data-analysis-mcp",
                "queryDatabase",
                toolRequest
        );
        assertNotNull(execution);
        assertEquals("PENDING", execution.getStatus());
        System.out.println("✓ 工具执行开始: executionId=" + execution.getExecutionId());

        // 模拟工具执行完成
        Thread.sleep(100); // 模拟执行耗时
        String toolResponse = "{\"rows\": 100, \"data\": [...]}";
        dataGovernanceService.completeToolExecution(execution.getExecutionId(), toolResponse, 100);
        System.out.println("✓ 工具执行成功完成，耗时: 100ms");

        // 验证工具执行记录
        ToolExecution savedExecution = toolExecutionService.getById(execution.getExecutionId());
        assertEquals("SUCCESS", savedExecution.getStatus());
        assertNotNull(savedExecution.getResponsePayload());
        assertEquals(100L, savedExecution.getDurationMs());

        // ============================================
        // 场景5: AI 助手生成回复
        // ============================================
        System.out.println("\n【场景5】AI助手生成回复");
        String assistantReply1 = "根据查询结果，2024年1月以来共有100条销售记录。";
        String processedReply1 = assistantReply1;
        String tokenUsage2 = "{\"input\": 150, \"output\": 80, \"total\": 230}";

        ChatMessage assistantMessage1 = dataGovernanceService.recordAssistantMessage(
                session.getSessionId(), assistantReply1, processedReply1, tokenUsage2);
        assertNotNull(assistantMessage1);
        assertEquals(2, assistantMessage1.getTurnIndex());
        assertEquals("ASSISTANT", assistantMessage1.getRole());
        System.out.println("✓ AI回复保存成功: turnIndex=" + assistantMessage1.getTurnIndex());

        // 更新会话Token统计
        dataGovernanceService.updateSessionTokens(session.getSessionId(), 280);
        System.out.println("✓ 会话Token统计更新: +280");

        // ============================================
        // 场景6: 更新会话摘要（Write-Back优化）
        // ============================================
        System.out.println("\n【场景6】更新会话摘要");
        String summary = "用户咨询数据分析功能，AI查询了销售数据并返回了100条记录的分析结果。";
        dataGovernanceService.updateSessionSummary(session.getSessionId(), summary);
        System.out.println("✓ 会话摘要已更新: " + summary);

        // ============================================
        // 场景7: 用户发送第二条消息（多轮对话）
        // ============================================
        System.out.println("\n【场景7】用户发送第二条消息 - 多轮对话");
        String userInput2 = "能否帮我做一个同比增长分析？";
        ChatMessage userMessage2 = dataGovernanceService.recordUserMessage(
                session.getSessionId(), userInput2, "[脱敏]" + userInput2, null);
        assertEquals(3, userMessage2.getTurnIndex());
        System.out.println("✓ 用户消息(第2轮): turnIndex=" + userMessage2.getTurnIndex());

        // 模拟AI处理
        Map<String, Object> agentState2 = Map.of(
                "currentStep", 2,
                "intent", "growth_analysis",
                "hasToolCall", true,
                "toolName", "compareGrowth"
        );
        dataGovernanceService.createCheckpoint(
                session.getSessionId(),
                userMessage2.getMessageId(),
                agentState2,
                2
        );

        // 模拟工具调用
        ToolExecution execution2 = dataGovernanceService.startToolExecution(
                userMessage2.getMessageId(),
                "data-analysis-mcp",
                "compareGrowth",
                "{\"currentYear\": 2024, \"previousYear\": 2023}"
        );
        dataGovernanceService.completeToolExecution(
                execution2.getExecutionId(),
                "{\"growthRate\": 15.5}",
                150
        );

        // AI回复
        ChatMessage assistantMessage2 = dataGovernanceService.recordAssistantMessage(
                session.getSessionId(),
                "同比增长分析显示，2024年相比2023年增长了15.5%。",
                "同比增长分析显示，2024年相比2023年增长了15.5%。",
                null
        );
        assertEquals(4, assistantMessage2.getTurnIndex());
        System.out.println("✓ AI回复(第2轮): turnIndex=" + assistantMessage2.getTurnIndex());

        // ============================================
        // 场景8: 画像变更记录（动态用户画像）
        // ============================================
        System.out.println("\n【场景8】记录用户画像变更");
        dataGovernanceService.recordProfileChange(
                TEST_USER_ID,
                "interests",
                null,
                "[\"数据分析\", \"销售报告\", \"同比增长分析\"]",
                "用户在对话中多次提及数据分析相关需求"
        );
        System.out.println("✓ 用户画像变更记录: interests -> 数据分析相关");

        dataGovernanceService.recordProfileChange(
                TEST_USER_ID,
                "conversation_count",
                "1",
                "2",
                "用户进行了多轮对话"
        );
        System.out.println("✓ 用户画像变更记录: conversation_count 2");

        // ============================================
        // 场景9: 断点恢复测试（模拟服务中断后恢复）
        // ============================================
        System.out.println("\n【场景9】断点恢复测试");

        // 获取最新断点
        ConversationCheckpoint checkpoint = dataGovernanceService.getLatestCheckpoint(session.getSessionId());
        assertNotNull(checkpoint);
        assertEquals(2, checkpoint.getStepIndex());
        System.out.println("✓ 找到断点: stepIndex=" + checkpoint.getStepIndex());

        // 解析断点状态
        Map<String, Object> restoredState = objectMapper.readValue(
                checkpoint.getStateData(), Map.class);
        assertEquals("growth_analysis", restoredState.get("intent"));
        System.out.println("✓ 断点状态解析成功: intent=" + restoredState.get("intent"));

        // 标记断点已恢复
        dataGovernanceService.markCheckpointResumed(checkpoint.getCheckpointId());
        System.out.println("✓ 断点已标记为已恢复");

        // ============================================
        // 场景10: 结束会话
        // ============================================
        System.out.println("\n【场景10】结束会话");
        dataGovernanceService.endSession(session.getSessionId(), "NORMAL");
        ChatSession endedSession = chatSessionService.getSessionById(session.getSessionId());
        assertNotNull(endedSession.getEndTime());
        assertEquals("NORMAL", endedSession.getStatus());
        assertEquals(280, endedSession.getTotalTokens());
        System.out.println("✓ 会话已结束: status=NORMAL, totalTokens=" + endedSession.getTotalTokens());

        // ============================================
        // 场景11: 数据召回验证
        // ============================================
        System.out.println("\n【场景11】数据召回验证");

        // 验证会话消息召回
        List<ChatMessage> messages = dataGovernanceService.getSessionMessages(session.getSessionId());
        assertEquals(4, messages.size()); // 2 user + 2 assistant
        System.out.println("✓ 消息召回成功: 共" + messages.size() + "条消息");

        // 验证最近消息召回
        List<ChatMessage> recentMessages = dataGovernanceService.getRecentMessages(session.getSessionId(), 2);
        assertEquals(2, recentMessages.size());
        System.out.println("✓ 最近2条消息召回成功");

        // 验证工具执行召回
        List<ToolExecution> executions = dataGovernanceService.getToolExecutions(userMessage1.getMessageId());
        assertTrue(executions.size() >= 1);
        System.out.println("✓ 工具执行记录召回成功: " + executions.size() + "条");

        // 验证画像变更召回
        List<ProfileChange> profileChanges = dataGovernanceService.getProfileChanges(TEST_USER_ID);
        assertEquals(2, profileChanges.size());
        System.out.println("✓ 画像变更记录召回成功: " + profileChanges.size() + "条");

        System.out.println("\n========== 全链路测试完成 ==========");
        System.out.println("✓ 所有测试场景通过！");
    }

    @Test
    public void testSessionRecoveryFromAborted() throws Exception {
        System.out.println("\n========== 测试中断会话恢复 ==========\n");

        // 1. 创建会话并模拟中断
        String userId = "test_user_aborted";
        ChatSession session = dataGovernanceService.startNewSession(userId);

        // 2. 用户发送消息
        ChatMessage userMsg = dataGovernanceService.recordUserMessage(
                session.getSessionId(),
                "查询数据",
                "查询数据",
                null
        );

        // 3. 创建断点（模拟在MCP调用中中断）
        Map<String, Object> abortedState = Map.of(
                "step", 1,
                "status", "MCP_CALL_IN_PROGRESS"
        );
        dataGovernanceService.createCheckpoint(
                session.getSessionId(),
                userMsg.getMessageId(),
                abortedState,
                1
        );

        // 4. 模拟会话中断
        dataGovernanceService.endSession(session.getSessionId(), "ABORTED");

        // 5. 模拟恢复：从H2恢复断点
        ConversationCheckpoint checkpoint = dataGovernanceService.getLatestCheckpoint(session.getSessionId());
        assertNotNull(checkpoint);
        assertFalse(checkpoint.getIsResumed());

        // 6. 验证可以恢复状态
        Map<String, Object> restored = objectMapper.readValue(checkpoint.getStateData(), Map.class);
        assertEquals("MCP_CALL_IN_PROGRESS", restored.get("status"));
        System.out.println("✓ 中断恢复测试通过，可以从断点恢复状态");

        System.out.println("\n========== 中断恢复测试完成 ==========");
    }

    @Test
    public void testTokenBudgetingScenario() throws Exception {
        System.out.println("\n========== 测试Token预算管理场景 ==========\n");

        String userId = "test_token_budget";
        ChatSession session = dataGovernanceService.startNewSession(userId);

        // 模拟多轮对话，积累大量消息
        for (int i = 1; i <= 10; i++) {
            String userInput = "用户消息 " + i + ": 这是一个比较长的用户输入，用于模拟真实对话场景";
            String aiReply = "AI回复 " + i + ": 这是一个比较长的AI回复，包含详细的分析结果和建议";

            dataGovernanceService.recordUserMessage(
                    session.getSessionId(),
                    userInput,
                    "[压缩]" + userInput.substring(0, 20),
                    "{\"input\":" + (i * 100) + ",\"output\":" + (i * 50) + "}"
            );

            dataGovernanceService.recordAssistantMessage(
                    session.getSessionId(),
                    aiReply,
                    "[压缩]" + aiReply.substring(0, 20),
                    "{\"input\":" + (i * 150) + ",\"output\":" + (i * 80) + "}"
            );
        }

        // 更新总Token
        dataGovernanceService.updateSessionTokens(session.getSessionId(), 10000);

        // 验证消息分页
        List<ChatMessage> allMessages = dataGovernanceService.getSessionMessages(session.getSessionId());
        assertEquals(20, allMessages.size());

        List<ChatMessage> recentMessages = dataGovernanceService.getRecentMessages(session.getSessionId(), 5);
        assertEquals(5, recentMessages.size());

        System.out.println("✓ Token预算测试通过: 共20条消息，可按需召回");
        System.out.println("\n========== Token预算测试完成 ==========");
    }

    @Test
    public void testMultiUserIsolation() {
        System.out.println("\n========== 测试多用户数据隔离 ==========\n");

        // 用户A创建会话
        String userA = "user_A";
        ChatSession sessionA = dataGovernanceService.startNewSession(userA);
        dataGovernanceService.recordUserMessage(sessionA.getSessionId(), "用户A的消息", "用户A的消息", null);

        // 用户B创建会话
        String userB = "user_B";
        ChatSession sessionB = dataGovernanceService.startNewSession(userB);
        dataGovernanceService.recordUserMessage(sessionB.getSessionId(), "用户B的消息", "用户B的消息", null);

        // 验证数据隔离
        List<ChatMessage> messagesA = dataGovernanceService.getSessionMessages(sessionA.getSessionId());
        List<ChatMessage> messagesB = dataGovernanceService.getSessionMessages(sessionB.getSessionId());

        assertEquals(1, messagesA.size());
        assertEquals("用户A的消息", messagesA.get(0).getContentRaw());

        assertEquals(1, messagesB.size());
        assertEquals("用户B的消息", messagesB.get(0).getContentRaw());

        System.out.println("✓ 多用户隔离测试通过");
        System.out.println("\n========== 多用户隔离测试完成 ==========");
    }
}
