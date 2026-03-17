package com.tlq.openclaw.tool;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public abstract class AbstractTool implements Tool {
    @Getter
    private final String id;
    @Getter
    private final String name;
    @Getter
    private final String description;
    @Getter
    @Setter
    private boolean enabled;
    
    public AbstractTool(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.enabled = true;
    }
    
    @Override
    public Object execute(Map<String, Object> parameters) {
        if (!enabled) {
            log.warn("Tool {} is disabled, skipping execute", name);
            return null;
        }
        log.info("Executing tool: {} with parameters: {}", name, parameters);
        try {
            return doExecute(parameters);
        } catch (Exception e) {
            log.error("Error executing tool: {}", name, e);
            return "Error: " + e.getMessage();
        }
    }
    
    protected abstract Object doExecute(Map<String, Object> parameters);
}