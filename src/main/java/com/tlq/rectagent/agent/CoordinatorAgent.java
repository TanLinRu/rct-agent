package com.tlq.rectagent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CoordinatorAgent {

    @Autowired
    private IntentRecognitionAgent intentRecognitionAgent;

    @Autowired
    private DynamicPromptAgent dynamicPromptAgent;

    @Autowired
    private DataAnalysisAgent dataAnalysisAgent;

    @Autowired
    private SequentialAgentExecutor sequentialAgentExecutor;

    /**
     * 处理用户请求
     * @param userInput 用户输入
     * @return 处理结果
     */
    public String processRequest(String userInput) {
        try {
            log.info("开始处理用户请求: {}", userInput);

            // 创建智能体列表，按照执行顺序排列
            List<com.alibaba.cloud.ai.graph.agent.ReactAgent> agents = new ArrayList<>();
            agents.add(intentRecognitionAgent.getAgent());
            agents.add(dynamicPromptAgent.getAgent());
            agents.add(dataAnalysisAgent.getAgent());

            // 定义输出key映射
            Map<String, String> outputKeyMap = new HashMap<>();
            outputKeyMap.put("intent_recognition_agent", "user_intent");
            outputKeyMap.put("dynamic_prompt_agent", "generated_prompt");
            outputKeyMap.put("data_analysis_agent", "analysis_result");

            // 执行智能体序列
            log.info("执行智能体序列");
            SequentialAgentExecutor.SequentialResult result = sequentialAgentExecutor.execute(
                    agents, userInput, outputKeyMap);

            String finalOutput = result.getFinalOutput();

            log.info("用户请求处理完成");
            log.debug("意图识别结果: {}", result.getData("user_intent"));
            log.debug("生成的提示词: {}", result.getData("generated_prompt"));
            return finalOutput;
        } catch (Exception e) {
            log.error("处理请求失败: {}", e.getMessage(), e);
            return "处理请求失败: " + e.getMessage();
        }
    }
}
