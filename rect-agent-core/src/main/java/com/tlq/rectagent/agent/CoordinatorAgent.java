package com.tlq.rectagent.agent;

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

    @Autowired
    private IntentRecognitionAgent intentRecognitionAgent;

    @Autowired
    private DynamicPromptAgent dynamicPromptAgent;

    @Autowired
    private DataAnalysisAgent dataAnalysisAgent;

    @Autowired
    private SequentialAgentExecutor sequentialAgentExecutor;

    public String processRequest(String userInput) {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = "local-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put(TRACE_ID_KEY, traceId);
        }
        try {
            int inputLen = userInput != null ? userInput.length() : 0;
            log.info("[{}] 开始处理用户请求: 输入长度={}", traceId, inputLen);
            log.debug("[{}] 用户输入详情: {}", traceId, userInput);

            List<com.alibaba.cloud.ai.graph.agent.ReactAgent> agents = new ArrayList<>();
            agents.add(intentRecognitionAgent.getAgent());
            agents.add(dynamicPromptAgent.getAgent());
            agents.add(dataAnalysisAgent.getAgent());

            Map<String, String> outputKeyMap = new HashMap<>();
            outputKeyMap.put("intent_recognition_agent", "user_intent");
            outputKeyMap.put("dynamic_prompt_agent", "generated_prompt");
            outputKeyMap.put("data_analysis_agent", "analysis_result");

            log.info("[{}] 执行智能体序列: 共{}个智能体", traceId, agents.size());
            SequentialAgentExecutor.SequentialResult result = sequentialAgentExecutor.execute(
                    agents, userInput, outputKeyMap);

            String finalOutput = result.getFinalOutput();
            int outputLen = finalOutput != null ? finalOutput.length() : 0;
            log.info("[{}] 用户请求处理完成: 输出长度={}", traceId, outputLen);
            log.debug("[{}] 各阶段输出: user_intent={}, generated_prompt={}, analysis_result={}", 
                    traceId, result.getData("user_intent"), result.getData("generated_prompt"), result.getData("analysis_result"));
            return finalOutput;
        } catch (Exception e) {
            log.error("[{}] 处理请求失败: {}", traceId, e.getMessage(), e);
            return "处理请求失败: " + e.getMessage();
        }
    }
}
