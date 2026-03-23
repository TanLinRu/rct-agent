package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tlq.rectagent.context.ContextLoader;
import com.tlq.rectagent.data.service.DataGovernanceService;
import com.tlq.rectagent.profile.ProfileInferenceService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private ContextLoader contextLoader;

    @Autowired
    private DataGovernanceService dataGovernanceService;

    @Autowired
    private ProfileInferenceService profileInferenceService;

    private SequentialAgent cachedSequentialAgent;

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

            Map<String, String> sessionContextMap = new HashMap<>();
            if (sessionId != null && userId != null) {
                ContextLoader.Context ctx = contextLoader.loadContext(sessionId, userId);
                if (ctx.getProfileTags() != null && !ctx.getProfileTags().isEmpty()) {
                    sessionContextMap.put("profileTags", String.join(", ", ctx.getProfileTags()));
                }
                if (ctx.getSessionSummary() != null) {
                    sessionContextMap.put("sessionSummary", ctx.getSessionSummary());
                }
                if (ctx.getHotMessages() != null && !ctx.getHotMessages().isEmpty()) {
                    StringBuilder hotText = new StringBuilder();
                    ctx.getHotMessages().forEach(msg ->
                            hotText.append(msg.getRole()).append(": ").append(msg.getContentRaw()).append("\n"));
                    sessionContextMap.put("hotMessages", hotText.toString());
                }
                log.debug("[{}] 会话上下文已加载: profileTags={}, summary={}, hotMsgs={}",
                        traceId, ctx.getProfileTags().size(),
                        ctx.getSessionSummary() != null ? ctx.getSessionSummary().length() : 0,
                        ctx.getHotMessages().size());
            }

            Map<String, Object> inputState = new HashMap<>();
            inputState.put("input", userInput);
            sessionContextMap.forEach(inputState::put);

            log.info("[{}] 执行智能体序列: 共3个智能体", traceId);
            Optional<OverAllState> optState = getSequentialAgent().invoke(inputState);

            if (optState.isEmpty()) {
                log.error("[{}] SequentialAgent 执行返回空状态", traceId);
                return new AgentResponse("执行失败: 返回空状态", sessionId, userId,
                        null, null, null, Map.of());
            }

            OverAllState state = optState.get();
            Map<String, Object> stateData = state.data();

            String intentResult = extractString(state, "user_intent");
            String generatedPrompt = extractString(state, "generated_prompt");
            String analysisResult = extractString(state, "analysis_result");
            String finalOutput = analysisResult != null ? analysisResult
                    : (generatedPrompt != null ? generatedPrompt : intentResult);

            log.debug("[{}] 各阶段输出: user_intent={}, generated_prompt={}, analysis_result={}",
                    traceId, truncate(intentResult), truncate(generatedPrompt), truncate(analysisResult));

            int outputLen = finalOutput != null ? finalOutput.length() : 0;
            log.info("[{}] 用户请求处理完成: 输出长度={}", traceId, outputLen);

            Map<String, String> agentOutputs = new HashMap<>();
            agentOutputs.put("intent_recognition_agent", intentResult);
            agentOutputs.put("dynamic_prompt_agent", generatedPrompt);
            agentOutputs.put("data_analysis_agent", analysisResult);

            if (sessionId != null) {
                for (Map.Entry<String, String> entry : agentOutputs.entrySet()) {
                    if (entry.getValue() != null) {
                        dataGovernanceService.recordAssistantMessage(sessionId,
                                entry.getValue(), entry.getValue(), null);
                    }
                }
                if (userId != null && finalOutput != null) {
                    profileInferenceService.inferAndRecord(userId, userInput, finalOutput);
                }
            }

            return new AgentResponse(
                    finalOutput, sessionId, userId,
                    intentResult, generatedPrompt, analysisResult,
                    Map.copyOf(agentOutputs)
            );
        } catch (GraphRunnerException e) {
            log.error("[{}] 处理请求失败: {}", traceId, e.getMessage(), e);
            return new AgentResponse("处理请求失败: " + e.getMessage(), sessionId, userId,
                    null, null, null, Map.of());
        } catch (Exception e) {
            log.error("[{}] 处理请求失败: {}", traceId, e.getMessage(), e);
            return new AgentResponse("处理请求失败: " + e.getMessage(), sessionId, userId,
                    null, null, null, Map.of());
        }
    }

    private synchronized SequentialAgent getSequentialAgent() {
        if (cachedSequentialAgent == null) {
            cachedSequentialAgent = SequentialAgent.builder()
                    .name("coordinator_sequential_agent")
                    .description("数据安全分析工作流：意图识别 -> 动态提示词生成 -> 数据分析")
                    .subAgents(List.of(
                            intentRecognitionAgent.getAgent(),
                            dynamicPromptAgent.getAgent(),
                            dataAnalysisAgent.getAgent()
                    ))
                    .build();
        }
        return cachedSequentialAgent;
    }

    private String extractString(OverAllState state, String key) {
        try {
            Optional<Object> opt = state.value(key);
            if (opt.isPresent()) {
                Object val = opt.get();
                if (val instanceof AssistantMessage am) {
                    return am.getText();
                }
                return val.toString();
            }
        } catch (Exception e) {
            log.debug("Failed to extract '{}' from OverAllState: {}", key, e.getMessage());
        }
        return null;
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= 50 ? s : s.substring(0, 50) + "...";
    }
}
