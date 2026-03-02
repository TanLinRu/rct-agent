package com.tlq.rectagent.skill.executor;

import org.springframework.ai.react.ReactAgent;

public class DashScopeExecutor implements RequirementExecutor {
    private final ReactAgent executorAgent;

    public DashScopeExecutor(ReactAgent executorAgent) {
        this.executorAgent = executorAgent;
    }

    @Override
    public String execute(String knowledge, String userRequirement) {
        StringBuilder context = new StringBuilder();
        context.append("基于以下学习到的知识，实现用户的需求：\n\n");
        context.append("学习到的知识：\n");
        context.append(knowledge);
        context.append("\n\n用户需求：\n");
        context.append(userRequirement);
        context.append("\n\n请基于学习到的知识，详细实现用户的需求。");

        try {
            return executorAgent.invoke(context.toString()).get().toString();
        } catch (Exception e) {
            throw new RuntimeException("实现用户需求失败：" + e.getMessage(), e);
        }
    }
}