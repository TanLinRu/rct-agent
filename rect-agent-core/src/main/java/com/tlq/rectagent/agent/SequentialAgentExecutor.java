package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tlq.rectagent.agent.AgentReflectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SequentialAgentExecutor {

    public static class SequentialResult {
        private final String finalOutput;
        private final AgentDataContext dataContext;
        private final Map<String, String> agentOutputs;
        private final String lastAgentName;

        public SequentialResult(String finalOutput, AgentDataContext dataContext, Map<String, String> agentOutputs, String lastAgentName) {
            this.finalOutput = finalOutput;
            this.dataContext = dataContext;
            this.agentOutputs = agentOutputs;
            this.lastAgentName = lastAgentName;
        }

        public String getFinalOutput() {
            if (lastAgentName != null && agentOutputs.containsKey(lastAgentName)) {
                return agentOutputs.get(lastAgentName);
            }
            return finalOutput;
        }

        public AgentDataContext getDataContext() {
            return dataContext;
        }

        public Map<String, String> getAgentOutputs() {
            return agentOutputs;
        }

        public String getData(String key) {
            return dataContext.get(key);
        }

        public String getContextSummary() {
            return String.format(
                "【意图识别结果】%s\n【生成提示词】%s\n【分析结果】%s",
                agentOutputs.getOrDefault("intent_recognition_agent", ""),
                agentOutputs.getOrDefault("dynamic_prompt_agent", ""),
                agentOutputs.getOrDefault("data_analysis_agent", "")
            );
        }
    }

    /**
     * 执行顺序智能体
     * @param agents 智能体列表，按照执行顺序排列
     * @param initialInput 初始输入
     * @param outputKeyMap 智能体名称到输出key的映射
     * @return 执行结果
     */
    public SequentialResult execute(
            List<ReactAgent> agents,
            String initialInput,
            Map<String, String> outputKeyMap) {
        return executeWithContext(agents, initialInput, outputKeyMap, null);
    }

    /**
     * 执行顺序智能体（带上下文）
     * @param agents 智能体列表
     * @param initialInput 初始输入
     * @param outputKeyMap 智能体名称到输出key的映射
     * @param sessionContext 会话上下文（用户画像/会话摘要/热消息），可传 null
     * @return 执行结果
     */
    public SequentialResult executeWithContext(
            List<ReactAgent> agents,
            String initialInput,
            Map<String, String> outputKeyMap,
            SessionContext sessionContext) {

        AgentDataContext context = new AgentDataContext();
        String currentInput = buildEnrichedInput(initialInput, sessionContext);
        Map<String, String> agentOutputs = new java.util.LinkedHashMap<>();
        String lastAgentName = null;

        for (int i = 0; i < agents.size(); i++) {
            ReactAgent agent = agents.get(i);
            String agentName = AgentReflectionUtil.getAgentName(agent);
            String outputKey = outputKeyMap.getOrDefault(agentName, agentName);
            lastAgentName = agentName;

            if (sessionContext != null && i == 0) {
                log.info("Executing agent {} ({}/{}) with context: profileTags={}, sessionSummary={}",
                        agentName, i + 1, agents.size(),
                        sessionContext.getProfileTags(),
                        sessionContext.getSessionSummary() != null ? sessionContext.getSessionSummary().substring(0, Math.min(30, sessionContext.getSessionSummary().length())) : "null");
            } else {
                log.info("Executing agent {} ({}/{}): {}", agentName, i + 1, agents.size(), currentInput);
            }

            try {
                String agentOutput = agent.call(currentInput).getText();
                agentOutputs.put(agentName, agentOutput);

                context.put(outputKey, agentOutput);
                log.debug("Agent {} output stored with key '{}': {}", agentName, outputKey, agentOutput);

                if (i < agents.size() - 1) {
                    String nextInput = buildNextAgentInput(
                            agentOutputs,
                            agentName,
                            outputKeyMap
                    );
                    currentInput = nextInput;
                }

            } catch (GraphRunnerException e) {
                log.error("Agent {} execution failed: {}", agentName, e.getMessage(), e);
                throw new RuntimeException("Agent execution failed: " + agentName, e);
            }
        }

        log.info("SequentialAgent execution completed. Last agent: {}, output length: {}",
                lastAgentName,
                agentOutputs.getOrDefault(lastAgentName, "").length());
        return new SequentialResult(currentInput, context, agentOutputs, lastAgentName);
    }

    private String buildEnrichedInput(String initialInput, SessionContext sessionContext) {
        if (sessionContext == null) {
            return initialInput;
        }
        StringBuilder sb = new StringBuilder();
        if (sessionContext.getProfileTags() != null && !sessionContext.getProfileTags().isEmpty()) {
            sb.append("【用户画像】").append(String.join(", ", sessionContext.getProfileTags())).append("\n");
        }
        if (sessionContext.getSessionSummary() != null) {
            sb.append("【会话摘要】").append(sessionContext.getSessionSummary()).append("\n");
        }
        if (sessionContext.getHotMessages() != null && !sessionContext.getHotMessages().isEmpty()) {
            sb.append("【最近对话】\n");
            sessionContext.getHotMessages().forEach(msg ->
                    sb.append(msg.getRole()).append(": ").append(msg.getContentRaw()).append("\n"));
        }
        sb.append("【当前输入】").append(initialInput);
        return sb.toString();
    }

    private String buildAgentSummary(String agentName, String output) {
        String label = switch (agentName) {
            case "intent_recognition_agent" -> "意图识别结果";
            case "dynamic_prompt_agent" -> "生成提示词";
            case "data_analysis_agent" -> "分析结果";
            default -> "中间结果";
        };
        int previewLen = Math.min(100, output != null ? output.length() : 0);
        String preview = output != null ? output.substring(0, previewLen) : "";
        if (output != null && output.length() > 100) preview += "...";
        return String.format("【%s】%s", label, preview);
    }

    private String buildNextAgentInput(Map<String, String> allOutputs, String previousAgentName,
                                       Map<String, String> outputKeyMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : allOutputs.entrySet()) {
            if (!entry.getKey().equals(previousAgentName)) {
                sb.append(buildAgentSummary(entry.getKey(), entry.getValue())).append("\n");
            }
        }
        String outputKey = outputKeyMap.getOrDefault(previousAgentName, previousAgentName);
        sb.append("【").append(outputKey).append("】")
          .append(allOutputs.get(previousAgentName));
        return sb.toString();
    }

    /**
     * 简化版执行方法
     */
    public String executeSimple(List<ReactAgent> agents, String initialInput) {
        Map<String, String> outputKeyMap = agents.stream()
                .collect(Collectors.toMap(
                        agent -> AgentReflectionUtil.getAgentName(agent),
                        agent -> "output"
                ));
        return execute(agents, initialInput, outputKeyMap).getFinalOutput();
    }

    @lombok.Data
    public static class SessionContext {
        private List<String> profileTags;
        private String sessionSummary;
        private List<? extends com.tlq.rectagent.data.entity.ChatMessage> hotMessages;
    }
}
