package com.tlq.rectagent.skill.learner;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;

import java.util.List;

public class DashScopeLearner implements LMLearner {
    private final ReactAgent learnerAgent;

    public DashScopeLearner(ReactAgent learnerAgent) {
        this.learnerAgent = learnerAgent;
    }

    @Override
    public String learn(List<String> documentContents) {
        StringBuilder context = new StringBuilder();
        context.append("请学习以下文档内容，以便后续回答相关问题：\n\n");

        for (int i = 0; i < documentContents.size(); i++) {
            context.append("文档 " + (i + 1) + "：\n");
            context.append(documentContents.get(i));
            context.append("\n\n");
        }

        context.append("请总结这些文档的核心内容，以便后续使用。");

        try {
            return learnerAgent.call(context.toString()).getText();
        } catch (Exception e) {
            throw new RuntimeException("学习文档内容失败：" + e.getMessage(), e);
        }
    }
}