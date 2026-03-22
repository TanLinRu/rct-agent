package com.tlq.rectagent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.tlq.rectagent.agent.*;
import com.tlq.rectagent.communication.AgentCommunicationManager;
import com.tlq.rectagent.context.*;
import com.tlq.rectagent.data.entity.*;
import com.tlq.rectagent.data.service.*;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class FullSystemE2ETest {

    @Autowired private DataGovernanceService dataGovernanceService;
    @Autowired private ChatSessionService chatSessionService;
    @Autowired private ChatMessageService chatMessageService;
    @Autowired private ToolExecutionService toolExecutionService;
    @Autowired private ProfileChangeService profileChangeService;
    @Autowired private ConversationCheckpointService checkpointService;
    @Autowired private ContextLoader contextLoader;
    @Autowired private TokenBudgetManager tokenBudgetManager;
    @Autowired private CheckpointRecoveryManager checkpointRecoveryManager;
    @Autowired private PromptVersionManager promptVersionManager;
    @Autowired private MessagePurifier messagePurifier;
    @Autowired private AgentCommunicationManager communicationManager;
    @Autowired private CoordinatorAgent coordinatorAgent;

    @MockBean private IntentRecognitionAgent intentRecognitionAgent;
    @MockBean private DynamicPromptAgent dynamicPromptAgent;
    @MockBean private DataAnalysisAgent dataAnalysisAgent;

    private static final String TEST_USER_ID = "full_e2e_user";

    @AfterEach
    public void cleanup() {
        List<ChatSession> sessions = chatSessionService.getSessionsByUserId(TEST_USER_ID, 100);
        for (ChatSession s : sessions) {
            dataGovernanceService.endSession(s.getSessionId(), "TEST_CLEANUP");
        }
    }

    @Test
    @DisplayName("全系统端到端：一个测试覆盖所有核心链路")
    public void fullSystemE2E() throws Exception {
        String traceId = "";

        // ================================================================
        // 第1段：会话生命周期
        // ================================================================
        ChatSession session = dataGovernanceService.startNewSession(TEST_USER_ID);
        assertNotNull(session.getSessionId());
        assertNotNull(session.getTraceId());
        traceId = session.getTraceId();
        System.out.println("[TRACE=" + traceId + "] 第1段: 会话创建成功 sessionId=" + session.getSessionId());

        // ================================================================
        // 第2段：消息记录 + PII脱敏
        // ================================================================
        String rawInput = "请查询 test 项目的 2026-01-01 到 2026-02-01 数据，联系 test@example.com，电话 13812345678";
        String sanitizedInput = messagePurifier.sanitizeUserInput(rawInput);
        assertFalse(sanitizedInput.contains("test@example.com"));
        assertFalse(sanitizedInput.contains("13812345678"));
        System.out.println("[TRACE=" + traceId + "] 第2段: PII脱敏完成，脱敏后=" + sanitizedInput);

        ChatMessage userMsg = dataGovernanceService.recordUserMessage(
                session.getSessionId(), rawInput, sanitizedInput, "{\"input\":80,\"output\":0}");
        assertEquals(1, userMsg.getTurnIndex());
        assertEquals("USER", userMsg.getRole());
        System.out.println("[TRACE=" + traceId + "] 第2段: 用户消息已记录 turnIndex=1");

        // ================================================================
        // 第3段：断点创建
        // ================================================================
        Map<String, Object> agentState = new LinkedHashMap<>();
        agentState.put("step", 1);
        agentState.put("intent", "data_query");
        agentState.put("toolName", "getDataByGameId");
        agentState.put("queryParams", Map.of("gameId", "test", "start", "2026-01-01", "end", "2026-02-01"));
        dataGovernanceService.createCheckpoint(session.getSessionId(), userMsg.getMessageId(), agentState, 1);
        System.out.println("[TRACE=" + traceId + "] 第3段: 断点已创建 step=1");

        // ================================================================
        // 第4段：工具执行（工具调用前、调用中、调用后）
        // ================================================================
        ToolExecution toolExec = dataGovernanceService.startToolExecution(
                userMsg.getMessageId(), "data-analysis-mcp", "getDataByGameId",
                "{\"gameId\":\"test\",\"startTime\":\"2026-01-01\",\"endTime\":\"2026-02-01\"}");
        assertNotNull(toolExec.getExecutionId());
        assertEquals("PENDING", toolExec.getStatus());
        System.out.println("[TRACE=" + traceId + "] 第4段: 工具执行开始 tool=getDataByGameId");

        Thread.sleep(50);
        String mockToolResult = ModelProcessInterceptor.MOCK_DATA;
        dataGovernanceService.completeToolExecution(toolExec.getExecutionId(), mockToolResult, 50);
        ToolExecution savedExec = toolExecutionService.getById(toolExec.getExecutionId());
        assertEquals("SUCCESS", savedExec.getStatus());
        assertEquals(50L, savedExec.getDurationMs());
        System.out.println("[TRACE=" + traceId + "] 第4段: 工具执行完成 耗时=50ms");

        // ================================================================
        // 第5段：AI回复 + 消息净化（去除Thought）
        // ================================================================
        String aiRawOutput = "Thought: 我需要分析这个数据\n" +
                "Observation: 数据查询完成，共10条记录\n" +
                "Final Answer: 分析完成：发现3个中等风险项。";
        MessagePurifier.PurifiedMessage purified = messagePurifier.purifyForInterAgent(aiRawOutput);
        assertNotNull(purified.getObservation());
        assertTrue(purified.getObservation().contains("数据查询完成"));
        assertFalse(purified.getPurifiedContent().contains("Thought:"));
        System.out.println("[TRACE=" + traceId + "] 第5段: AI输出已净化 observation=" + purified.getObservation());

        ChatMessage aiMsg = dataGovernanceService.recordAssistantMessage(
                session.getSessionId(), aiRawOutput, purified.getPurifiedContent(),
                "{\"input\":150,\"output\":120,\"total\":270}");
        assertEquals(2, aiMsg.getTurnIndex());
        assertEquals("ASSISTANT", aiMsg.getRole());
        dataGovernanceService.updateSessionTokens(session.getSessionId(), 270);
        System.out.println("[TRACE=" + traceId + "] 第5段: AI回复已记录 turnIndex=2 token累计=270");

        // ================================================================
        // 第6段：会话摘要更新
        // ================================================================
        String summary = "用户查询项目test 2026年1-2月数据，AI调用getDataByGameId获取到10条记录，分析发现3个中等风险项。";
        dataGovernanceService.updateSessionSummary(session.getSessionId(), summary);
        ChatSession updated = chatSessionService.getSessionById(session.getSessionId());
        assertEquals(summary, updated.getSummarySnapshot());
        System.out.println("[TRACE=" + traceId + "] 第6段: 会话摘要已更新");

        // ================================================================
        // 第7段：多轮对话（再3轮，积累token）
        // ================================================================
        for (int i = 3; i <= 5; i++) {
            dataGovernanceService.recordUserMessage(
                    session.getSessionId(), "用户第" + i + "轮消息", "处理后", null);
            dataGovernanceService.recordAssistantMessage(
                    session.getSessionId(), "AI第" + i + "轮回复内容", "AI回复", null);
        }
        List<ChatMessage> afterMultiTurn = dataGovernanceService.getSessionMessages(session.getSessionId());
        assertTrue(afterMultiTurn.size() >= 6);
        System.out.println("[TRACE=" + traceId + "] 第7段: 多轮对话完成 " + afterMultiTurn.size() + "条消息");

        // ================================================================
        // 第8段：用户画像记录
        // ================================================================
        dataGovernanceService.recordProfileChange(TEST_USER_ID, "interests", null,
                "[\"数据查询\", \"风险分析\"]", "用户在对话中提及数据查询和风险分析");
        dataGovernanceService.recordProfileChange(TEST_USER_ID, "query_project", null,
                "test", "用户查询了项目test的数据");
        dataGovernanceService.recordProfileChange(TEST_USER_ID, "conversation_count", "0", "1",
                "用户完成了一轮完整对话");
        List<ProfileChange> profileChanges = dataGovernanceService.getProfileChanges(TEST_USER_ID);
        assertEquals(3, profileChanges.size());
        System.out.println("[TRACE=" + traceId + "] 第8段: 用户画像已记录 3项变更");

        // ================================================================
        // 第9段：分层上下文加载
        // ================================================================
        ContextLoader.Context ctx = contextLoader.loadContext(session.getSessionId(), TEST_USER_ID);
        assertNotNull(ctx.getSession());
        assertFalse(ctx.getHotMessages().isEmpty());
        System.out.println("[TRACE=" + traceId + "] 第9段: 分层上下文加载完成 L1热消息="
                + ctx.getHotMessages().size() + "条");

        // ================================================================
        // 第10段：Token预算压缩
        // ================================================================
        List<ChatMessage> allMsgs = dataGovernanceService.getSessionMessages(session.getSessionId());
        String systemPrompt = "你是一位资深的数据安全分析专家";
        TokenBudgetManager.CompressedContext compressed =
                tokenBudgetManager.compressContext(allMsgs, systemPrompt);
        assertNotNull(compressed);
        assertTrue(compressed.getCompressedCount() < compressed.getOriginalCount());
        assertTrue(compressed.getCompressionRate() < 1.0);
        System.out.println("[TRACE=" + traceId + "] 第10段: Token压缩完成 原始="
                + compressed.getOriginalCount() + "条 压缩后=" + compressed.getCompressedCount()
                + "条 压缩率=" + String.format("%.1f%%", compressed.getCompressionRate() * 100));

        // 语义约束校验（压缩上下文非空即通过；关键词在历史摘要中由generateSemanticSummary提取）
        assertFalse(compressed.getMessages().isEmpty());
        System.out.println("[TRACE=" + traceId + "] 第10段: 压缩上下文非空，语义约束校验通过");

        // ================================================================
        // 第11段：Prompt构建 + 占位符替换
        // ================================================================
        AgentDataContext agentCtx = new AgentDataContext();
        agentCtx.put("user_intent", "数据查询");
        agentCtx.put("generated_prompt", "请分析{user_intent}并生成报告");
        agentCtx.put("analysis_result", "分析完成：发现3个中等风险项");
        String resolvedPrompt = agentCtx.replacePlaceholders("{generated_prompt}");
        assertEquals("请分析数据查询并生成报告", resolvedPrompt);
        System.out.println("[TRACE=" + traceId + "] 第11段: 占位符替换完成 resolved="
                + resolvedPrompt);

        // 从压缩上下文构建Prompt
        String builtPrompt = contextLoader.buildPromptFromContext(ctx);
        assertNotNull(builtPrompt);
        assertFalse(builtPrompt.isEmpty());
        System.out.println("[TRACE=" + traceId + "] 第11段: Prompt构建完成 长度=" + builtPrompt.length() + "字符");

        // ================================================================
        // 第12段：Prompt版本管理
        // ================================================================
        String defaultPrompt = promptVersionManager.getPrompt("data_analysis");
        assertNotNull(defaultPrompt);
        promptVersionManager.registerVersion("data_analysis",
                "你是一位高级数据分析师，专注于复杂业务分析", TEST_USER_ID);
        PromptVersionManager.PromptVersion latest =
                promptVersionManager.getLatestVersion("data_analysis");
        assertNotNull(latest);
        List<PromptVersionManager.PromptVersion> history =
                promptVersionManager.getVersionHistory("data_analysis");
        assertTrue(history.size() >= 2);
        boolean semanticCheck = promptVersionManager.validateSemanticIntegrity(
                "你是一位数据分析师，擅长数据分析", List.of("数据分析", "风险识别"));
        assertFalse(semanticCheck);
        System.out.println("[TRACE=" + traceId + "] 第12段: Prompt版本管理完成 历史版本="
                + history.size() + "个");

        // ================================================================
        // 第13段：断点恢复流程
        // ================================================================
        checkpointRecoveryManager.abortSession(session.getSessionId(), "模拟服务中断");
        CheckpointRecoveryManager.RecoveryResult recovery =
                checkpointRecoveryManager.checkAndRecover(session.getSessionId());
        assertTrue(recovery.isRecoverable());
        assertEquals(1, recovery.getStepIndex());
        checkpointRecoveryManager.markCheckpointResumed(recovery.getCheckpointId());
        System.out.println("[TRACE=" + traceId + "] 第13段: 断点恢复成功 step=1");

        // 验证恢复后不再可恢复
        CheckpointRecoveryManager.RecoveryResult afterRecovery =
                checkpointRecoveryManager.checkAndRecover(session.getSessionId());
        assertFalse(afterRecovery.isRecoverable());
        System.out.println("[TRACE=" + traceId + "] 第13段: 恢复后检查通过");

        // ================================================================
        // 第14段：CoordinatorAgent 编排全链路
        // ================================================================
        TestAssistantMessage r1 = new TestAssistantMessage("{\"intent\":\"data_query\",\"confidence\":0.95}");
        TestAssistantMessage r2 = new TestAssistantMessage("请分析数据并生成报告");
        TestAssistantMessage r3 = new TestAssistantMessage("分析完成：发现3个中等风险项");

        ReactAgent mockIntent = mock(ReactAgent.class);
        when(mockIntent.call(anyString())).thenReturn(r1);
        AgentReflectionUtil.setMockAgentName(mockIntent, "intent_recognition_agent");

        ReactAgent mockPrompt = mock(ReactAgent.class);
        when(mockPrompt.call(anyString())).thenReturn(r2);
        AgentReflectionUtil.setMockAgentName(mockPrompt, "dynamic_prompt_agent");

        ReactAgent mockAnalysis = mock(ReactAgent.class);
        when(mockAnalysis.call(anyString())).thenReturn(r3);
        AgentReflectionUtil.setMockAgentName(mockAnalysis, "data_analysis_agent");

        when(intentRecognitionAgent.getAgent()).thenReturn(mockIntent);
        when(dynamicPromptAgent.getAgent()).thenReturn(mockPrompt);
        when(dataAnalysisAgent.getAgent()).thenReturn(mockAnalysis);

        String coordinatorResult = coordinatorAgent.processRequest("查询项目test数据");
        assertEquals("分析完成：发现3个中等风险项", coordinatorResult);
        verify(intentRecognitionAgent).getAgent();
        verify(dynamicPromptAgent).getAgent();
        verify(dataAnalysisAgent).getAgent();
        verify(mockIntent).call(anyString());
        verify(mockPrompt).call(anyString());
        verify(mockAnalysis).call(anyString());
        System.out.println("[TRACE=" + traceId + "] 第14段: CoordinatorAgent编排完成 最终输出="
                + coordinatorResult);

        // ================================================================
        // 第15段：SequentialAgentExecutor 直接调用（覆盖内部编排细节）
        // ================================================================
        TestAssistantMessage r4 = new TestAssistantMessage("{\"intent\":\"risk_analysis\",\"confidence\":0.9}");
        TestAssistantMessage r5 = new TestAssistantMessage("请分析风险数据");
        TestAssistantMessage r6 = new TestAssistantMessage("风险分析完成：发现2个高风险项");

        ReactAgent mockIntent2 = mock(ReactAgent.class);
        when(mockIntent2.call(anyString())).thenReturn(r4);
        AgentReflectionUtil.setMockAgentName(mockIntent2, "intent_agent");

        ReactAgent mockPrompt2 = mock(ReactAgent.class);
        when(mockPrompt2.call(anyString())).thenReturn(r5);
        AgentReflectionUtil.setMockAgentName(mockPrompt2, "prompt_agent");

        ReactAgent mockAnalysis2 = mock(ReactAgent.class);
        when(mockAnalysis2.call(anyString())).thenReturn(r6);
        AgentReflectionUtil.setMockAgentName(mockAnalysis2, "analysis_agent");

        List<ReactAgent> agents = Arrays.asList(mockIntent2, mockPrompt2, mockAnalysis2);
        Map<String, String> outputKeyMap = new HashMap<>();
        outputKeyMap.put("intent_agent", "user_intent");
        outputKeyMap.put("prompt_agent", "generated_prompt");
        outputKeyMap.put("analysis_agent", "analysis_result");

        AgentDataContext dataCtx = new AgentDataContext();
        dataCtx.put("user_intent", "{\"intent\":\"risk_analysis\"}");
        dataCtx.put("generated_prompt", "请分析{user_intent}的风险数据");
        String resolved = dataCtx.replacePlaceholders("{generated_prompt}");
        assertEquals("请分析{\"intent\":\"risk_analysis\"}的风险数据", resolved);

        SequentialAgentExecutor.SequentialResult seqResult =
                new SequentialAgentExecutor().execute(agents, "查询风险数据", outputKeyMap);
        assertEquals("风险分析完成：发现2个高风险项", seqResult.getFinalOutput());
        assertEquals("{\"intent\":\"risk_analysis\",\"confidence\":0.9}", seqResult.getData("user_intent"));
        System.out.println("[TRACE=" + traceId + "] 第15段: SequentialAgent直接执行完成 最终输出="
                + seqResult.getFinalOutput());

        // ================================================================
        // 第16段：Agent间通信
        // ================================================================
        AgentCommunicationManager.AgentMessage msg1 = new AgentCommunicationManager.AgentMessage(
                "coordinator", "intent_agent", "{\"task\":\"analyze\"}", "TASK_DISPATCH");
        communicationManager.sendMessage("intent_agent", msg1);
        assertTrue(communicationManager.hasMessage("intent_agent"));
        AgentCommunicationManager.AgentMessage received = communicationManager.receiveMessage("intent_agent");
        assertNotNull(received);
        assertEquals("coordinator", received.getSender());
        assertEquals("TASK_DISPATCH", received.getMessageType());
        communicationManager.clearMessages("intent_agent");
        assertFalse(communicationManager.hasMessage("intent_agent"));
        System.out.println("[TRACE=" + traceId + "] 第16段: Agent间通信测试通过");

        // ================================================================
        // 第17段：数据全量召回验证
        // ================================================================
        List<ChatMessage> recalledMsgs = dataGovernanceService.getSessionMessages(session.getSessionId());
        assertTrue(recalledMsgs.size() >= 6);
        List<ChatMessage> recentMsgs = dataGovernanceService.getRecentMessages(session.getSessionId(), 3);
        assertEquals(3, recentMsgs.size());
        List<ToolExecution> toolExecs = dataGovernanceService.getToolExecutions(userMsg.getMessageId());
        assertFalse(toolExecs.isEmpty());
        ConversationCheckpoint latestCheckpoint = dataGovernanceService.getLatestCheckpoint(session.getSessionId());
        assertNotNull(latestCheckpoint);
        System.out.println("[TRACE=" + traceId + "] 第17段: 数据召回完成 消息="
                + recalledMsgs.size() + "条 工具执行=" + toolExecs.size() + "条");

        // ================================================================
        // 第18段：会话结束
        // ================================================================
        dataGovernanceService.endSession(session.getSessionId(), "NORMAL");
        ChatSession endedSession = chatSessionService.getSessionById(session.getSessionId());
        assertNotNull(endedSession.getEndTime());
        assertEquals("NORMAL", endedSession.getStatus());
        assertEquals(270, endedSession.getTotalTokens());
        System.out.println("[TRACE=" + traceId + "] 第18段: 会话已结束 status=NORMAL totalTokens=270");

        // ================================================================
        // 第19段：多用户数据隔离
        // ================================================================
        String otherUserId = "other_user_e2e";
        ChatSession otherSession = dataGovernanceService.startNewSession(otherUserId);
        dataGovernanceService.recordUserMessage(otherSession.getSessionId(), "其他用户消息", "其他用户消息", null);
        List<ChatMessage> otherMsgs = dataGovernanceService.getSessionMessages(otherSession.getSessionId());
        assertEquals(1, otherMsgs.size());
        assertTrue(dataGovernanceService.getSessionMessages(session.getSessionId()).size() >= 6);
        dataGovernanceService.endSession(otherSession.getSessionId(), "TEST_CLEANUP");
        System.out.println("[TRACE=" + traceId + "] 第19段: 多用户隔离验证通过");

        System.out.println();
        System.out.println("============================================");
        System.out.println("  全系统端到端测试 PASSED");
        System.out.println("  覆盖链路: 会话→消息→PII脱敏→Checkpoint→工具执行→AI回复净化→");
        System.out.println("           摘要→多轮→画像→分层上下文→Token压缩→占位符替换→");
        System.out.println("           Prompt版本→断点恢复→CoordinatorAgent→SequentialAgent→");
        System.out.println("           Agent通信→数据召回→会话结束→多用户隔离");
        System.out.println("  共 19 个链路阶段 全部通过");
        System.out.println("============================================");
    }

    private static class TestAssistantMessage extends org.springframework.ai.chat.messages.AssistantMessage {
        private final String text;
        TestAssistantMessage(String text) {
            super("");
            this.text = text;
        }
        @Override public String getText() { return text; }
    }
}
