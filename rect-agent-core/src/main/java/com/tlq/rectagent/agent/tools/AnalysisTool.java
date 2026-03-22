package com.tlq.rectagent.agent.tools;

public class AnalysisTool implements AgentTool {
    private final String name;
    public AnalysisTool(String name) { this.name = name; }
    @Override
    public String getName() { return name; }
    @Override
    public String apply(String input) {
        // Placeholder: real implementation would perform data analysis
        return input;
    }
}
