package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.hook.ContextInjectionHook;
import com.tlq.rectagent.hook.HookConfiguration;
import com.tlq.rectagent.hook.ProfileInferenceHook;
import com.tlq.rectagent.tools.DataAnalysisTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据分析智能体
 * 负责执行具体的数据分析任务
 */
@Slf4j
@Component
public class DataAnalysisAgent {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Value("${rectagent.prompts.data-analysis}")
    private String systemPrompt;

    @Autowired
    private HookConfiguration hookConfiguration;

    @Autowired
    private ContextInjectionHook contextInjectionHook;

    @Autowired
    private ProfileInferenceHook profileInferenceHook;

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

                    List<com.alibaba.cloud.ai.graph.agent.hook.Hook> allHooks = new ArrayList<>();
                    allHooks.add(contextInjectionHook);
                    allHooks.addAll(hookConfiguration.getFrameworkHooks());
                    allHooks.add(profileInferenceHook);

                    agent = ReactAgent.builder()
                            .name("data_analysis_agent")
                            .chatOptions(ChatOptions.builder().build())
                            .model(chatModel)
                            .methodTools(dataAnalysisTools)
                            .systemPrompt(systemPrompt)
                            .instruction("请根据以下任务描述执行数据分析：\n{generated_prompt}")
                            .outputKey("analysis_result")
                            .includeContents(true)
                            .returnReasoningContents(false)
                            .saver(new MemorySaver())
                            .hooks(allHooks)
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