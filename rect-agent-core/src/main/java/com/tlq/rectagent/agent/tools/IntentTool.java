package com.tlq.rectagent.agent.tools;

public class IntentTool implements AgentTool {
    private final String name;
    public IntentTool(String name) { this.name = name; }
    @Override
    public String getName() { return name; }
    @Override
    public String apply(String input) {
        // Placeholder: real implementation would perform intent recognition
        return input;
    }
}
