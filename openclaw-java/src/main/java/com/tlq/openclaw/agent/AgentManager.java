package com.tlq.openclaw.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Component
public class AgentManager {
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    
    public Agent createAgent(String name, String model) {
        String agentId = UUID.randomUUID().toString();
        Agent agent = new Agent(agentId, name, model);
        agents.put(agentId, agent);
        log.info("Created agent: {} with model: {}", agentId, model);
        return agent;
    }
    
    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }
    
    public void removeAgent(String agentId) {
        agents.remove(agentId);
        log.info("Removed agent: {}", agentId);
    }
    
    public Map<String, Agent> getAllAgents() {
        return agents;
    }
}