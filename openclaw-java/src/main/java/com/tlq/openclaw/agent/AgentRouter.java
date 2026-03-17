package com.tlq.openclaw.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentRouter {
    @Autowired
    private AgentManager agentManager;
    
    private String defaultAgentId;
    
    public void setDefaultAgentId(String defaultAgentId) {
        this.defaultAgentId = defaultAgentId;
    }
    
    public String routeMessage(String sessionId, String message) {
        // 这里可以根据会话 ID、消息内容等因素选择合适的 Agent
        // 目前简单返回默认 Agent 的处理结果
        Agent agent = agentManager.getAgent(defaultAgentId);
        if (agent == null) {
            // 如果没有默认 Agent，创建一个
            agent = agentManager.createAgent("default", "anthropic/claude-opus-4-6");
            defaultAgentId = agent.getId();
        }
        return agent.processMessage(message);
    }
}