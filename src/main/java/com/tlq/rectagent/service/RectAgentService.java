package com.tlq.rectagent.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAgentApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import com.tlq.rectagent.tools.DataAnalysisTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.Arrays;
import java.util.function.BiFunction;

@Slf4j
@Service
public class RectAgentService {

    @Autowired
    private ChatModelFactory chatModelFactory;

    public void testAgent() throws GraphRunnerException {
        DashScopeChatModel chatModel = chatModelFactory.getChatModel();

        DataAnalysisTools dataAnalysisTools = new DataAnalysisTools();

        // 创建 agent
        ReactAgent agent = ReactAgent.builder()
                .name("data_analysis_agent")
                .chatOptions(ChatOptions.builder().build())
                .model(chatModel)
                .methodTools(dataAnalysisTools)
                .systemPrompt("你是一位资深的数据安全分析专家，专注于从复杂的数据结构中识别安全风险、异常模式和关键洞察。你的核心能力包括数据解析、风险识别、跨维度关联分析、风险量化评估、数据质量评估和业务影响映射。")
                .interceptors(Arrays.asList(new ModelProcessInterceptor(), new ToolMonitoringInterceptor()))
                .saver(new MemorySaver())
                .hooks(ModelCallLimitHook.builder().runLimit(5).build())
                .build();

        // 运行 agent
        AssistantMessage response = agent.call("获取项目 test 2026-01-01 00:00:00 到  2026-02-01 00:00:00 的数据,并进行分析处理");
        log.info("response: {}", response.getText());
    }

}
