package com.tlq.rectagent.agent;

import com.tlq.rectagent.context.ContextLoader;
import com.tlq.rectagent.data.service.DataGovernanceService;
import com.tlq.rectagent.profile.ProfileInferenceService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class CoordinatorAgent {

    private static final String TRACE_ID_KEY = "traceId";

    public record AgentResponse(
            String content,
            String sessionId,
            String userId,
            String intentResult,
            String generatedPrompt,
            String analysisResult,
            Map<String, String> agentOutputs
    ) {}

    @Autowired
    private IntentRecognitionAgent intentRecognitionAgent;

    @Autowired
    private DynamicPromptAgent dynamicPromptAgent;

    @Autowired
    private DataAnalysisAgent dataAnalysisAgent;

    @Autowired
    private SequentialAgentExecutor sequentialAgentExecutor;

    @Autowired
    private ContextLoader contextLoader;

    @Autowired
    private DataGovernanceService dataGovernanceService;

    @Autowired
    private ProfileInferenceService profileInferenceService;

    public String processRequest(String userInput) {
        return processRequest(userInput, null, null);
    }

    public String processRequest(String userInput, String sessionId, String userId) {
        AgentResponse response = processRequestWithMetadata(userInput, sessionId, userId);
        return response.content();
    }

    public AgentResponse processRequestWithMetadata(String userInput, String sessionId, String userId) {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = "local-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put(TRACE_ID_KEY, traceId);
        }
        try {
            int inputLen = userInput != null ? userInput.length() : 0;
            log.info("[{}] 开始处理用户请求: 输入长度={}", traceId, inputLen);
            log.debug("[{}] 用户输入详情: {}", traceId, userInput);

            SequentialAgentExecutor.SessionContext sessionContext = null;
            if (sessionId != null && userId != null) {
                ContextLoader.Context ctx = contextLoader.loadContext(sessionId, userId);
                sessionContext = new SequentialAgentExecutor.SessionContext();
                sessionContext.setProfileTags(ctx.getProfileTags());
                sessionContext.setSessionSummary(ctx.getSessionSummary());
                sessionContext.setHotMessages(ctx.getHotMessages());
                log.debug("[{}] 会话上下文已加载: profileTags={}, summary={}, hotMsgs={}",
                        traceId, ctx.getProfileTags().size(),
                        ctx.getSessionSummary() != null ? ctx.getSessionSummary().length() : 0,
                        ctx.getHotMessages().size());
            }

            List<com.alibaba.cloud.ai.graph.agent.ReactAgent> agents = new ArrayList<>();
            agents.add(intentRecognitionAgent.getAgent());
            agents.add(dynamicPromptAgent.getAgent());
            agents.add(dataAnalysisAgent.getAgent());

            Map<String, String> outputKeyMap = new HashMap<>();
            outputKeyMap.put("intent_recognition_agent", "user_intent");
            outputKeyMap.put("dynamic_prompt_agent", "generated_prompt");
            outputKeyMap.put("data_analysis_agent", "analysis_result");

            log.info("[{}] 执行智能体序列: 共{}个智能体", traceId, agents.size());
            SequentialAgentExecutor.SequentialResult result = sequentialAgentExecutor.executeWithContext(
                    agents, userInput, outputKeyMap, sessionContext);

            Map<String, String> agentOutputs = result.getAgentOutputs();
            log.debug("[{}] 各阶段输出: user_intent={}, generated_prompt={}, analysis_result={}",
                    traceId,
                    truncate(agentOutputs.get("intent_recognition_agent")),
                    truncate(agentOutputs.get("dynamic_prompt_agent")),
                    truncate(agentOutputs.get("data_analysis_agent")));

            String finalOutput = result.getFinalOutput();
            int outputLen = finalOutput != null ? finalOutput.length() : 0;
            log.info("[{}] 用户请求处理完成: 输出长度={}", traceId, outputLen);

            if (sessionId != null) {
                for (Map.Entry<String, String> entry : agentOutputs.entrySet()) {
                    dataGovernanceService.recordAssistantMessage(sessionId,
                            entry.getValue(), entry.getValue(), null);
                }
                if (userId != null && finalOutput != null) {
                    profileInferenceService.inferAndRecord(userId, userInput, finalOutput);
                }
            }

            return new AgentResponse(
                    finalOutput, sessionId, userId,
                    agentOutputs.get("intent_recognition_agent"),
                    agentOutputs.get("dynamic_prompt_agent"),
                    agentOutputs.get("data_analysis_agent"),
                    Map.copyOf(agentOutputs)
            );
        } catch (Exception e) {
            log.error("[{}] 处理请求失败: {}", traceId, e.getMessage(), e);
            return new AgentResponse("处理请求失败: " + e.getMessage(), sessionId, userId,
                    null, null, null, Map.of());
        }
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= 50 ? s : s.substring(0, 50) + "...";
    }
}
