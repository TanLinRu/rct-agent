package com.tlq.rectagent.agent.tools;

public interface AgentTool {
    String getName();
    
    String getDescription();
    
    String apply(String input);
}
