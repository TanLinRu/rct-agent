package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 协调智能体
 * 负责管理多智能体之间的协作和调度
 */
@Component
public class CoordinatorAgent {

    @Autowired
    private IntentRecognitionAgent intentRecognitionAgent;

    @Autowired
    private DynamicPromptAgent dynamicPromptAgent;

    @Autowired
    private DataAnalysisAgent dataAnalysisAgent;

    /**
     * 处理用户请求
     * @param userInput 用户输入
     * @return 处理结果
     */
    public String processRequest(String userInput) {
        try {
            System.out.println("开始处理用户请求: " + userInput);
            
            // 创建智能体列表，按照执行顺序排列
            List<com.alibaba.cloud.ai.graph.agent.Agent> agents = new ArrayList<>();
            agents.add(intentRecognitionAgent.getAgent());
            agents.add(dynamicPromptAgent.getAgent());
            agents.add(dataAnalysisAgent.getAgent());
            
            // 创建SequentialAgent
            SequentialAgent sequentialAgent = SequentialAgent.builder()
                    .name("coordinator_agent")
                    .subAgents(agents)
                    .build();
            
            // 执行智能体序列
            System.out.println("执行智能体序列");
            String result = sequentialAgent.invoke(userInput).get().toString();
            
            System.out.println("用户请求处理完成");
            System.out.println("处理结果: " + result);
            return result;
        } catch (Exception e) {
            System.err.println("处理请求失败: " + e.getMessage());
            e.printStackTrace();
            return "处理请求失败: " + e.getMessage();
        }
    }
}