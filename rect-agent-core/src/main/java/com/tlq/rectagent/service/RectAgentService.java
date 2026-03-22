package com.tlq.rectagent.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAgentApi;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.interceptor.ModelProcessInterceptor;
import com.tlq.rectagent.interceptor.ToolMonitoringInterceptor;
import com.tlq.rectagent.tools.DataAnalysisTools;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.Arrays;
import java.util.UUID;
import java.util.function.BiFunction;

@Slf4j
@Service
public class RectAgentService {

    private static final String TRACE_ID_KEY = "traceId";

    @Autowired
    private ChatModelFactory chatModelFactory;

    public void testAgent() throws GraphRunnerException {
        String traceId = ensureTraceId();
        ChatModel chatModel = chatModelFactory.getChatModel();
        DataAnalysisTools dataAnalysisTools = new DataAnalysisTools();

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

        AssistantMessage response = agent.call("获取项目 test 2026-01-01 00:00:00 到  2026-02-01 00:00:00 的数据,并进行分析处理");
        int outputLen = response.getText() != null ? response.getText().length() : 0;
        log.info("[{}] testAgent执行完成: 输出长度={}", traceId, outputLen);
        log.debug("[{}] testAgent输出: {}", traceId, response.getText());
    }

    @Autowired
    private com.tlq.rectagent.agent.SequentialAgentExecutor sequentialAgentExecutor;

    public com.tlq.rectagent.agent.SequentialAgentExecutor.SequentialResult testSequentialExecutor() {
        String traceId = ensureTraceId();
        log.info("[{}] testSequentialExecutor开始执行", traceId);
        ChatModel chatModel = chatModelFactory.getChatModel();

        ReactAgent intentAgent = ReactAgent.builder()
                .name("intent_recognition_agent")
                .chatOptions(ChatOptions.builder().build())
                .model(chatModel)
                .systemPrompt("你是一位专业的意图识别专家。")
                .outputKey("user_intent")
                .saver(new MemorySaver())
                .build();

        ReactAgent promptAgent = ReactAgent.builder()
                .name("dynamic_prompt_agent")
                .chatOptions(ChatOptions.builder().build())
                .model(chatModel)
                .systemPrompt("你是一位专业的提示词工程师。")
                .outputKey("generated_prompt")
                .saver(new MemorySaver())
                .build();

        ReactAgent analysisAgent = ReactAgent.builder()
                .name("data_analysis_agent")
                .chatOptions(ChatOptions.builder().build())
                .model(chatModel)
                .systemPrompt("你是一位资深的数据安全分析专家。")
                .outputKey("analysis_result")
                .saver(new MemorySaver())
                .build();

        java.util.List<ReactAgent> agents = java.util.Arrays.asList(intentAgent, promptAgent, analysisAgent);
        java.util.Map<String, String> outputKeyMap = new java.util.HashMap<>();
        outputKeyMap.put("intent_recognition_agent", "user_intent");
        outputKeyMap.put("dynamic_prompt_agent", "generated_prompt");
        outputKeyMap.put("data_analysis_agent", "analysis_result");

        com.tlq.rectagent.agent.SequentialAgentExecutor.SequentialResult result = 
                sequentialAgentExecutor.execute(agents, "测试输入", outputKeyMap);
        log.info("[{}] testSequentialExecutor执行完成", traceId);
        return result;
    }

    private String ensureTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = "svc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }
}
