package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
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

        public SequentialResult(String finalOutput, AgentDataContext dataContext, Map<String, String> agentOutputs) {
            this.finalOutput = finalOutput;
            this.dataContext = dataContext;
            this.agentOutputs = agentOutputs;
        }

        public String getFinalOutput() {
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

        AgentDataContext context = new AgentDataContext();
        String currentInput = initialInput;
        Map<String, String> agentOutputs = new java.util.LinkedHashMap<>();

        for (int i = 0; i < agents.size(); i++) {
            ReactAgent agent = agents.get(i);
            String agentName = agent.getName();
            String outputKey = outputKeyMap.getOrDefault(agentName, "output");

            log.info("Executing agent {} ({}/{}): {}", agentName, i + 1, agents.size(), currentInput);

            try {
                String agentOutput = agent.call(currentInput).getText();
                agentOutputs.put(agentName, agentOutput);

                context.put(outputKey, agentOutput);
                log.debug("Agent {} output stored with key '{}': {}", agentName, outputKey, agentOutput);

                // 将当前输出作为下一个智能体的输入
                currentInput = agentOutput;

            } catch (GraphRunnerException e) {
                log.error("Agent {} execution failed: {}", agentName, e.getMessage(), e);
                throw new RuntimeException("Agent execution failed: " + agentName, e);
            }
        }

        log.info("SequentialAgent execution completed. Final output length: {}", currentInput.length());
        return new SequentialResult(currentInput, context, agentOutputs);
    }

    /**
     * 简化版执行方法
     */
    public String executeSimple(List<ReactAgent> agents, String initialInput) {
        Map<String, String> outputKeyMap = agents.stream()
                .collect(Collectors.toMap(
                        ReactAgent::getName,
                        agent -> "output"
                ));
        return execute(agents, initialInput, outputKeyMap).getFinalOutput();
    }
}
