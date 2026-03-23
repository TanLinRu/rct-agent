package com.tlq.rectagent.agent;

import com.tlq.rectagent.agent.tools.AgentTool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AgentToolRegistry {

    private final List<AgentTool> agentTools = new ArrayList<>();
    private final Map<String, AgentTool> toolMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("AgentToolRegistry 初始化完成: 共 {} 个工具", agentTools.size());
    }

    public void registerTool(AgentTool tool) {
        if (tool == null || tool.getName() == null) {
            log.warn("跳过注册无效工具: tool={}", tool);
            return;
        }
        if (toolMap.containsKey(tool.getName())) {
            log.warn("工具已存在，将被覆盖: {}", tool.getName());
        }
        toolMap.put(tool.getName(), tool);
        agentTools.clear();
        agentTools.addAll(toolMap.values());
        log.debug("注册工具: name={}, description={}", tool.getName(), tool.getDescription());
    }

    public void registerTools(List<AgentTool> tools) {
        tools.forEach(this::registerTool);
    }

    public List<AgentTool> getAgentTools() {
        return List.copyOf(agentTools);
    }

    public AgentTool getTool(String name) {
        return toolMap.get(name);
    }

    public boolean hasTool(String name) {
        return toolMap.containsKey(name);
    }

    public int getToolCount() {
        return toolMap.size();
    }

    public Map<String, AgentTool> getToolMap() {
        return Map.copyOf(toolMap);
    }

    public String listTools() {
        return toolMap.values().stream()
                .map(t -> String.format("- %s: %s", t.getName(), t.getDescription()))
                .collect(Collectors.joining("\n"));
    }
}
