package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.tools.DataAnalysisTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 数据分析智能体
 * 负责执行具体的数据分析任务
 */
@Slf4j
@Component
public class DataAnalysisAgent {

    @Autowired
    private ChatModelFactory chatModelFactory;

    private ReactAgent agent;
    private DataAnalysisTools dataAnalysisTools;

    public ReactAgent getAgent() {
        if (agent == null) {
            synchronized (this) {
                if (agent == null) {
                    ChatModel chatModel = chatModelFactory.getChatModel();

                    if (dataAnalysisTools == null) {
                        dataAnalysisTools = new DataAnalysisTools();
                    }

                    // 创建数据分析智能体
                    agent = ReactAgent.builder()
                            .name("data_analysis_agent")
                            .chatOptions(ChatOptions.builder().build())
                            .model(chatModel)
                            .methodTools(dataAnalysisTools)
                            .systemPrompt("你是一位资深的数据安全分析专家，专注于从复杂的数据结构中识别安全风险、异常模式和关键洞察。你的核心能力包括数据解析、风险识别、跨维度关联分析、风险量化评估、数据质量评估和业务影响映射。")
                            .instruction("提示词：{generated_prompt}\n请根据提示词执行数据分析任务。")
                            .outputKey("analysis_result")
                            .saver(new MemorySaver())
                            .build();
                }
            }
        }
        return agent;
    }

    /**
     * 执行数据分析
     * @param query 分析查询
     * @return 分析结果
     */
    public String analyzeData(String query) {
        try {
            ReactAgent agent = getAgent();
            String result = agent.call(query).getText();
            int inputLen = query != null ? query.length() : 0;
            int resultLen = result != null ? result.length() : 0;
            log.info("数据分析执行成功: 查询长度={}, 结果长度={}", inputLen, resultLen);
            log.debug("数据分析详情: 查询={}, 结果={}", query, result);
            return result;
        } catch (GraphRunnerException e) {
            log.error("数据分析执行失败: {}", e.getMessage(), e);
            return "数据分析执行失败: " + e.getMessage();
        }
    }
}