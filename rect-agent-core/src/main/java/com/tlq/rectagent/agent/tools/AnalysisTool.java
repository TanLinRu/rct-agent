package com.tlq.rectagent.agent.tools;

public class AnalysisTool implements AgentTool {
    private final String name;
    private final String description;

    public AnalysisTool(String name) {
        this(name, "数据安全分析工具");
    }

    public AnalysisTool(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public String apply(String input) {
        return input;
    }
}
