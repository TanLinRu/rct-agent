package com.tlq.rectagent.agent.tools;

public class IntentTool implements AgentTool {
    private final String name;
    private final String description;

    public IntentTool(String name) {
        this(name, "用户意图识别工具");
    }

    public IntentTool(String name, String description) {
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
