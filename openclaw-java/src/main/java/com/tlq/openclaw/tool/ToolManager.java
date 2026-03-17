package com.tlq.openclaw.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Component
public class ToolManager {
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    
    public Tool createTool(String name, String type, String description) {
        String toolId = UUID.randomUUID().toString();
        Tool tool;
        
        switch (type.toLowerCase()) {
            case "file":
                tool = new FileTool(toolId, name, description);
                break;
            default:
                throw new IllegalArgumentException("Unsupported tool type: " + type);
        }
        
        tools.put(toolId, tool);
        log.info("Created tool: {} (type: {})", name, type);
        return tool;
    }
    
    public Tool getTool(String toolId) {
        return tools.get(toolId);
    }
    
    public void removeTool(String toolId) {
        Tool tool = tools.remove(toolId);
        if (tool != null) {
            log.info("Removed tool: {}", tool.getName());
        }
    }
    
    public Map<String, Tool> getAllTools() {
        return tools;
    }
    
    public Object executeTool(String toolId, Map<String, Object> parameters) {
        Tool tool = tools.get(toolId);
        if (tool == null) {
            log.warn("Tool not found: {}", toolId);
            return "Tool not found: " + toolId;
        }
        return tool.execute(parameters);
    }
}