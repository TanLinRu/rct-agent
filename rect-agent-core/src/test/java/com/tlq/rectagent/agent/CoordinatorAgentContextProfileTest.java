package com.tlq.rectagent.agent;

import com.tlq.rectagent.context.ContextLoader;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Timeout;
import com.tlq.rectagent.context.TokenBudgetManager;
import com.tlq.rectagent.data.entity.ChatMessage;
import com.tlq.rectagent.data.entity.ChatSession;
import com.tlq.rectagent.data.entity.ProfileChange;
import com.tlq.rectagent.data.service.ChatMessageService;
import com.tlq.rectagent.data.service.ChatSessionService;
import com.tlq.rectagent.data.service.DataGovernanceService;
import com.tlq.rectagent.data.service.ProfileChangeService;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
@Timeout(600)
public class CoordinatorAgentContextProfileTest {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    @Autowired
    private DataGovernanceService dataGovernanceService;

    @Autowired
    private ContextLoader contextLoader;

    @Autowired
    private TokenBudgetManager tokenBudgetManager;

    @Autowired
    private ProfileChangeService profileChangeService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @AfterEach
    public void cleanup() {
        List<ChatSession> sessions = chatSessionService.getSessionsByUserId(TEST_USER_ID, 100);
        for (ChatSession s : sessions) {
            dataGovernanceService.endSession(s.getSessionId(), "TEST_CLEANUP");
        }
        MDC.remove("traceId");
    }

    private static final String TEST_USER_ID = "ctx_profile_user_" + System.currentTimeMillis();

    @Test
    @DisplayName("全链路场景: CoordinatorAgent + 上下文 + 用户画像")
    public void fullScenario() {
        String traceId = "";

        System.out.println("============================================");
        System.out.println("【场景一】首次用户 - 风险数据场景 - 开始");
        System.out.println("============================================");

        ChatSession session = dataGovernanceService.startNewSession(TEST_USER_ID);
        String sessionId = session.getSessionId();
        traceId = session.getTraceId();
        MDC.put("traceId", traceId);
        assertNotNull(sessionId, "会话ID非空");
        assertNotNull(traceId, "跟踪ID非空");
        System.out.println("[TRACE=" + traceId + "] 1.1 会话创建成功 sessionId=" + sessionId);

        String rawInput = "帮我分析项目 risk_project 的风险数据";
        String sanitized = "帮我分析项目 risk_project 的风险数据";
        String tokenJson = "{\"input\":50}";
        ChatMessage userMsg = dataGovernanceService.recordUserMessage(sessionId, rawInput, sanitized, tokenJson);
        assertEquals(1, userMsg.getTurnIndex().intValue(), "第1轮用户消息 turnIndex=1");
        System.out.println("[TRACE=" + traceId + "] 1.2 用户消息记录成功 turnIndex=" + userMsg.getTurnIndex());

        ContextLoader.Context ctx = contextLoader.loadContext(sessionId, TEST_USER_ID);
        assertTrue(ctx.getHotMessages().isEmpty() || ctx.getHotMessages().size() <= 1, "首次加载L1为空或<=1条");
        assertNull(ctx.getSessionSummary(), "首次摘要为空");
        assertEquals(traceId, ctx.getTraceId(), "TraceId传递正确");
        System.out.println("[TRACE=" + traceId + "] 1.3 首次上下文加载: L1=" + ctx.getHotMessages().size()
                + "条 L2摘要=" + (ctx.getSessionSummary() == null ? "null" : "有值"));

        String input = "分析项目 risk_project 的风险数据";
        String response = coordinatorAgent.processRequest(input);
        assertNotNull(response, "CoordinatorAgent响应非空");
        assertFalse(response.contains("处理请求失败"), "响应不包含失败标记");
        System.out.println("[TRACE=" + traceId + "] 1.4 CoordinatorAgent处理成功");

        String aiContent = response;
        String tokenJsonAi = "{\"output\":150}";
        ChatMessage aiMsg = dataGovernanceService.recordAssistantMessage(sessionId, aiContent, aiContent, tokenJsonAi);
        assertEquals(2, aiMsg.getTurnIndex().intValue(), "AI响应 turnIndex=2");
        System.out.println("[TRACE=" + traceId + "] 1.5 AI消息记录成功 turnIndex=" + aiMsg.getTurnIndex());

        dataGovernanceService.updateSessionSummary(sessionId, "用户首次关注项目风险数据");
        System.out.println("[TRACE=" + traceId + "] 1.6 会话摘要更新成功");

        dataGovernanceService.recordProfileChange(TEST_USER_ID, "interest_area", null, "risk_analysis", "用户关注风险数据");
        dataGovernanceService.recordProfileChange(TEST_USER_ID, "query_count", null, "1", "首次查询");
        dataGovernanceService.recordProfileChange(TEST_USER_ID, "first_session", null, "true", "首次会话");
        List<ProfileChange> profileChanges = dataGovernanceService.getProfileChanges(TEST_USER_ID);
        assertEquals(3, profileChanges.size(), "记录了3条画像变更");
        System.out.println("[TRACE=" + traceId + "] 1.7 画像变更记录成功: " + profileChanges.size() + "条");

        ContextLoader.Context ctxRound2 = contextLoader.loadContext(sessionId, TEST_USER_ID);
        assertFalse(ctxRound2.getHotMessages().isEmpty(), "第2轮前热消息非空");
        assertNotNull(ctxRound2.getSessionSummary(), "第2轮前摘要非空");
        System.out.println("[TRACE=" + traceId + "] 1.8 第2轮前上下文: L1=" + ctxRound2.getHotMessages().size()
                + "条 L2摘要=" + (ctxRound2.getSessionSummary() != null ? ctxRound2.getSessionSummary().substring(0, Math.min(20, ctxRound2.getSessionSummary().length())) : "null"));

        String turn2Input = "显示前10条风险记录";
        ChatMessage userMsg2 = dataGovernanceService.recordUserMessage(sessionId, turn2Input, turn2Input, "{\"input\":30}");
        assertEquals(3, userMsg2.getTurnIndex().intValue(), "第2轮用户消息 turnIndex=3");
        String response2 = coordinatorAgent.processRequest(turn2Input);
        assertNotNull(response2);
        ChatMessage aiMsg2 = dataGovernanceService.recordAssistantMessage(sessionId, response2, response2, "{\"output\":120}");
        assertEquals(4, aiMsg2.getTurnIndex().intValue(), "第2轮AI响应 turnIndex=4");
        System.out.println("[TRACE=" + traceId + "] 1.9 第2轮完成: 用户turnIndex=3 AI turnIndex=4");

        List<ChatMessage> allMessages = dataGovernanceService.getSessionMessages(sessionId);
        String systemPrompt = "你是一位资深的数据安全分析专家";
        TokenBudgetManager.CompressedContext compressed = tokenBudgetManager.compressContext(allMessages, systemPrompt);
        assertTrue(compressed.getCompressionRate() <= 1.0, "压缩率应<=1.0: " + compressed.getCompressionRate());
        System.out.println("[TRACE=" + traceId + "] 1.10 Token压缩: 原始=" + compressed.getOriginalCount()
                + " 压缩后=" + compressed.getCompressedCount() + " 压缩率=" + String.format("%.1f%%", compressed.getCompressionRate() * 100));

        String builtPrompt = contextLoader.buildPromptFromContext(ctxRound2);
        assertNotNull(builtPrompt, "Prompt构建非空");
        assertFalse(builtPrompt.isEmpty(), "Prompt长度>0");
        assertTrue(builtPrompt.length() > 50, "多轮Prompt应有足够长度: " + builtPrompt.length());
        System.out.println("[TRACE=" + traceId + "] 1.11 Prompt构建: 长度=" + builtPrompt.length());

        System.out.println("============================================");
        System.out.println("【场景一】PASSED - 首次用户 风险数据场景");
        System.out.println("  2轮CoordinatorAgent调用 → 上下文按轮累积 → 画像字段初始化 →");
        System.out.println("  Token压缩验证 → Prompt构建含历史摘要");
        System.out.println("============================================");

        String traceId2 = "";

        System.out.println("============================================");
        System.out.println("【场景二】多轮对话 - 上下文累积与画像演变 - 开始");
        System.out.println("============================================");

        ChatSession session2 = dataGovernanceService.startNewSession(TEST_USER_ID);
        String sessionId2 = session2.getSessionId();
        traceId2 = session2.getTraceId();
        MDC.put("traceId", traceId2);
        assertNotNull(sessionId2);
        assertNotNull(traceId2);
        System.out.println("[TRACE=" + traceId2 + "] 2.1 会话创建成功 sessionId=" + sessionId2);

        String turn1Input = "分析项目 risk_project 的账户安全风险，统计2026年2月数据";
        ChatMessage u1 = dataGovernanceService.recordUserMessage(sessionId2, turn1Input, turn1Input, "{\"input\":50}");
        String r1 = coordinatorAgent.processRequest(turn1Input);
        assertNotNull(r1);
        assertFalse(r1.contains("处理请求失败"));
        ChatMessage a1 = dataGovernanceService.recordAssistantMessage(sessionId2, r1, r1, "{\"output\":150}");
        assertEquals(2, a1.getTurnIndex());
        dataGovernanceService.recordProfileChange(TEST_USER_ID, "interest_area",
                null, "account_security", "用户关注账户安全");
        dataGovernanceService.updateSessionSummary(sessionId2, "用户关注账户安全风险分析");
        System.out.println("[TRACE=" + traceId2 + "] 2.2 第1轮完成 turnIndex=2 消息总数=2");

        String turn2Input2 = "有哪些账户被标记为高风险？具体数量是多少？";
        ChatMessage u2 = dataGovernanceService.recordUserMessage(sessionId2, turn2Input2, turn2Input2, "{\"input\":40}");
        String r2 = coordinatorAgent.processRequest(turn2Input2);
        assertNotNull(r2);
        ChatMessage a2 = dataGovernanceService.recordAssistantMessage(sessionId2, r2, r2, "{\"output\":200}");
        assertEquals(4, a2.getTurnIndex());
        dataGovernanceService.recordProfileChange(TEST_USER_ID, "query_detail_level",
                null, "high_risk_specific", "用户追问高风险详情");
        System.out.println("[TRACE=" + traceId2 + "] 2.3 第2轮完成 turnIndex=4 消息总数=4");

        String turn3Input = "换个角度，帮我分析设备指纹相关的风险模式";
        ChatMessage u3 = dataGovernanceService.recordUserMessage(sessionId2, turn3Input, turn3Input, "{\"input\":45}");
        String r3 = coordinatorAgent.processRequest(turn3Input);
        assertNotNull(r3);
        ChatMessage a3 = dataGovernanceService.recordAssistantMessage(sessionId2, r3, r3, "{\"output\":180}");
        assertEquals(6, a3.getTurnIndex());
        dataGovernanceService.recordProfileChange(TEST_USER_ID, "interest_area",
                "account_security", "device_fingerprint", "用户从账户安全转向设备指纹分析");
        System.out.println("[TRACE=" + traceId2 + "] 2.4 第3轮完成 turnIndex=6 消息总数=6");

        ContextLoader.Context ctxAfter3 = contextLoader.loadContext(sessionId2, TEST_USER_ID);
        assertNotNull(ctxAfter3.getSession(), "会话上下文非空");
        assertFalse(ctxAfter3.getHotMessages().isEmpty(),
                "三轮后热消息非空: " + ctxAfter3.getHotMessages().size());
        assertNotNull(ctxAfter3.getSessionSummary(), "三轮后会话摘要非空");
        assertEquals("用户关注账户安全风险分析", ctxAfter3.getSessionSummary(),
                "会话摘要内容正确");
        System.out.println("[TRACE=" + traceId2 + "] 2.5 三轮后上下文: L1=" + ctxAfter3.getHotMessages().size()
                + "条 L2摘要=" + ctxAfter3.getSessionSummary().substring(0, Math.min(20, ctxAfter3.getSessionSummary().length())));

        List<ChatMessage> allMsgs = dataGovernanceService.getSessionMessages(sessionId2);
        assertTrue(allMsgs.size() >= 6, "三轮对话应有>=6条消息: " + allMsgs.size());
        for (int i = 0; i < allMsgs.size(); i++) {
            assertEquals(i + 1, allMsgs.get(i).getTurnIndex().intValue(),
                    "消息序号应按序排列");
        }
        System.out.println("[TRACE=" + traceId2 + "] 2.6 消息按序排列验证通过 消息数=" + allMsgs.size());

        List<ProfileChange> allProfileChanges = dataGovernanceService.getProfileChanges(TEST_USER_ID);
        assertTrue(allProfileChanges.size() >= 4, "四轮交互后应有>=4条画像变更");
        boolean hasOldValue = allProfileChanges.stream()
                .anyMatch(p -> "interest_area".equals(p.getFieldName())
                        && "account_security".equals(p.getOldValue()));
        boolean hasNewValue = allProfileChanges.stream()
                .anyMatch(p -> "interest_area".equals(p.getFieldName())
                        && "device_fingerprint".equals(p.getNewValue()));
        assertTrue(hasOldValue, "interest_area应有旧值记录(account_security)");
        assertTrue(hasNewValue, "interest_area应有新值记录(device_fingerprint)");
        System.out.println("[TRACE=" + traceId2 + "] 2.7 画像演变验证: old=account_security → new=device_fingerprint");

        String systemPrompt2 = "你是一位资深的数据安全分析专家";
        TokenBudgetManager.CompressedContext compressed2 =
                tokenBudgetManager.compressContext(allMsgs, systemPrompt2);
        assertNotNull(compressed2);
        assertTrue(compressed2.getCompressionRate() <= 1.0,
                "压缩率应<=1.0: " + compressed2.getCompressionRate());
        assertTrue(compressed2.getOriginalCount() >= compressed2.getCompressedCount(),
                "原始>=压缩后: " + compressed2.getOriginalCount() + ">=" + compressed2.getCompressedCount());
        System.out.println("[TRACE=" + traceId2 + "] 2.8 多轮Token压缩: 原始="
                + compressed2.getOriginalCount() + " 压缩后=" + compressed2.getCompressedCount()
                + " 压缩率=" + String.format("%.1f%%", compressed2.getCompressionRate() * 100));

        String builtPrompt2 = contextLoader.buildPromptFromContext(ctxAfter3);
        assertNotNull(builtPrompt2);
        assertFalse(builtPrompt2.isEmpty());
        assertTrue(builtPrompt2.contains("ASSISTANT") || builtPrompt2.contains("USER"),
                "Prompt应包含对话内容");
        assertTrue(builtPrompt2.length() > 50, "多轮Prompt应有足够长度: " + builtPrompt2.length());
        System.out.println("[TRACE=" + traceId2 + "] 2.9 多轮Prompt构建完成 长度=" + builtPrompt2.length()
                + " 含对话=" + (builtPrompt2.contains("USER") || builtPrompt2.contains("ASSISTANT")));

        ContextLoader.Context ctxAfter3Final = contextLoader.loadContext(sessionId2, TEST_USER_ID);
        List<ChatMessage> recentMsgs = dataGovernanceService.getRecentMessages(sessionId2, 3);
        assertEquals(3, recentMsgs.size(), "最近3条消息正确获取");
        assertTrue(ctxAfter3Final.getHotMessages().size() >= 3,
                "三轮后热消息>=3: " + ctxAfter3Final.getHotMessages().size());
        System.out.println("[TRACE=" + traceId2 + "] 2.10 三轮后验证: 热消息=" + ctxAfter3Final.getHotMessages().size()
                + " 最近消息=" + recentMsgs.size());

        dataGovernanceService.endSession(sessionId2, "NORMAL");
        ChatSession endedSession = chatSessionService.getSessionById(sessionId2);
        assertNotNull(endedSession.getEndTime(), "会话有结束时间");
        assertEquals("NORMAL", endedSession.getStatus(), "会话状态为NORMAL");
        System.out.println("[TRACE=" + traceId2 + "] 2.11 会话正常结束 status=NORMAL");

        System.out.println("============================================");
        System.out.println("【场景二】PASSED - 多轮对话 上下文累积与画像演变");
        System.out.println("  3轮真实CoordinatorAgent调用 → 上下文按轮累积 → 画像字段演变 →");
        System.out.println("  Token压缩多轮验证 → Prompt构建含完整历史 → 会话正常结束");
        System.out.println("============================================");
    }
}
