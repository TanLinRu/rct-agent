package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.context.SessionContextKeys;
import com.tlq.rectagent.hook.ClawCodeCompressionHook;
import com.tlq.rectagent.hook.ContextInjectionMessagesHook;
import com.tlq.rectagent.hook.DynamicBoundaryHook;
import com.tlq.rectagent.hook.MessagePersistenceHook;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class SupervisorAgentFramework {

    private static final String SYSTEM_PROMPT = """
            你是一个智能的风控数据分析监督者，负责协调和管理多个专业Agent来完成风控场景的数据分析需求。

            ## 你的职责
            1. 分析用户的风控数据查询需求，将其分解为合适的子任务
            2. 根据任务特性，选择合适的Agent进行处理
            3. 监控任务执行状态，决定是否需要继续处理或完成任务
            4. 当所有任务完成时，返回FINISH结束流程

            ## 可用的子Agent及其职责

            ### intent_recognition_agent
            - 功能: 识别用户风控查询意图，返回结构化意图信息
            - 适用场景: 用户需要识别查询意图、提取关键实体
            - 输出: intent_result

            ### data_analysis_agent
            - 功能: 执行风控数据分析，支持风控分析、用户分析、交易分析等
            - 适用场景: 用户需要对风控数据进行分析和处理
            - 输出: analysis_result

            ### risk_assessment_agent
            - 功能: 执行风险评估，基于数据分析结果进行风险评级
            - 适用场景: 用户需要了解风险等级和风险建议
            - 输出: risk_assessment_result

            ## 动态上下文
            __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__

            ## 决策规则

            1. 单一任务判断:
               - 需要识别意图 -> intent_recognition_agent
               - 需要数据分析 -> data_analysis_agent
               - 需要风险评估 -> risk_assessment_agent

            2. 多步骤任务处理:
               - 如果需求包含多个步骤，需要分步处理
               - 先路由到第一个合适的Agent，等待完成后再路由下一个
               - 所有步骤完成时返回FINISH

            3. 禁止连续两次调用同一Agent
            4. 最多调用3次

            ## 响应格式
            只返回Agent名称或FINISH，不要包含其他解释。
            """;

    private static final String INSTRUCTION = """
            请分析用户输入和上下文，决定调用哪个Agent：
            - 意图识别 -> intent_recognition_agent（输出：{intent_result}）
            - 数据分析 -> data_analysis_agent（输出：{analysis_result}）
            - 风险评估 -> risk_assessment_agent（输出：{risk_assessment_result}）
            - 完成 -> FINISH

            注意：
            - 如果前序Agent已有输出，请参考该输出做决策
            - 禁止连续两次调用同一Agent
            - 最多调用3次
            """;

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired(required = false)
    private ContextInjectionMessagesHook contextInjectionHook;

    @Autowired(required = false)
    private ClawCodeCompressionHook clawCodeCompressionHook;

    @Autowired(required = false)
    private DynamicBoundaryHook dynamicBoundaryHook;

    @Autowired(required = false)
    private MessagePersistenceHook messagePersistenceHook;

    private com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent supervisor;

    public SupervisorAgentFramework() {
    }

    public SupervisorAgentFramework(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    public void setChatModelFactory(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    @PostConstruct
    public void init() {
        if (chatModelFactory == null) {
            log.warn("ChatModelFactory is null, skipping initialization");
            return;
        }

        ChatModel chatModel = chatModelFactory.getChatModel();
        if (chatModel == null) {
            throw new IllegalStateException("ChatModel is not available");
        }

        List<Hook> frameworkHooks = buildFrameworkHooks();

        ReactAgent intentAgent = createIntentAgent(chatModel, frameworkHooks);
        ReactAgent analysisAgent = createDataAnalysisAgent(chatModel, frameworkHooks);
        ReactAgent riskAgent = createRiskAssessmentAgent(chatModel, frameworkHooks);

        ReactAgent mainAgent = createMainAgent(chatModel, frameworkHooks);

        supervisor = SupervisorAgent.builder()
                .name("supervisor")
                .description("风控数据分析监督者")
                .model(chatModel)
                .mainAgent(mainAgent)
                .subAgents(Arrays.asList(intentAgent, analysisAgent, riskAgent))
                .build();

        log.info("SupervisorAgentFramework initialized with framework SupervisorAgent");
    }

    private List<Hook> buildFrameworkHooks() {
        List<Hook> hooks = new ArrayList<>();

        if (contextInjectionHook != null) {
            hooks.add(contextInjectionHook);
            log.info("Added ContextInjectionMessagesHook");
        }

        if (clawCodeCompressionHook != null) {
            hooks.add(clawCodeCompressionHook);
            log.info("Added ClawCodeCompressionHook");
        }

        if (dynamicBoundaryHook != null) {
            hooks.add(dynamicBoundaryHook);
            log.info("Added DynamicBoundaryHook");
        }

        if (messagePersistenceHook != null) {
            hooks.add(messagePersistenceHook);
            log.info("Added MessagePersistenceHook");
        }

        return hooks;
    }

    private ReactAgent createMainAgent(ChatModel chatModel, List<Hook> hooks) {
        return ReactAgent.builder()
                .name("main_agent")
                .model(chatModel)
                .description("主Agent，负责决定路由")
                .systemPrompt(SYSTEM_PROMPT)
                .instruction(INSTRUCTION)
                .outputKey("main_output")
                .includeContents(false)
                .returnReasoningContents(false)
                .hooks(hooks)
                .build();
    }

    private ReactAgent createIntentAgent(ChatModel chatModel, List<Hook> hooks) {
        String intentInstruction = """
                你是风控意图识别专家。分析用户输入，提取关键实体和意图类型。
                用户输入：{input}
                返回格式：intent类型、entities列表、confidence
                """;

        return ReactAgent.builder()
                .name("intent_recognition_agent")
                .model(chatModel)
                .description("识别风控查询意图")
                .instruction(intentInstruction)
                .outputKey("intent_result")
                .includeContents(false)
                .returnReasoningContents(false)
                .hooks(hooks)
                .build();
    }

    private ReactAgent createDataAnalysisAgent(ChatModel chatModel, List<Hook> hooks) {
        String analysisInstruction = """
                你是风控数据分析专家。根据输入执行风控数据分析。
                意图识别结果：{intent_result}
                用户输入：{input}
                返回：分析类型、数据概况、关键发现
                """;

        return ReactAgent.builder()
                .name("data_analysis_agent")
                .model(chatModel)
                .description("执行风控数据分析")
                .instruction(analysisInstruction)
                .outputKey("analysis_result")
                .includeContents(true)
                .returnReasoningContents(false)
                .hooks(hooks)
                .build();
    }

    private ReactAgent createRiskAssessmentAgent(ChatModel chatModel, List<Hook> hooks) {
        String riskInstruction = """
                你是风险评估专家。基于输入进行风险评估。
                意图识别结果：{intent_result}
                数据分析结果：{analysis_result}
                用户输入：{input}
                返回：风险等级、高危因素、建议措施
                """;

        return ReactAgent.builder()
                .name("risk_assessment_agent")
                .model(chatModel)
                .description("执行风险评估")
                .instruction(riskInstruction)
                .outputKey("risk_assessment_result")
                .includeContents(true)
                .returnReasoningContents(false)
                .hooks(hooks)
                .build();
    }

    public String invoke(String input) {
        return invoke(input, null, null).content();
    }

    public SupervisorResult invoke(String input, String sessionId, String userId) {
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[{}] SupervisorAgentFramework 开始处理风控请求: inputLength={}, sessionId={}, userId={}", traceId,
                input != null ? input.length() : 0, sessionId, userId);

        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .addMetadata(SessionContextKeys.SESSION_ID, sessionId != null ? sessionId : "")
                    .addMetadata(SessionContextKeys.USER_ID, userId != null ? userId : "")
                    .addMetadata("traceId", traceId)
                    .build();

            log.info("[{}] SupervisorAgentFramework 调用 supervisor.invoke(), config.threadId={}", traceId, sessionId);
            Optional<OverAllState> result = supervisor.invoke(input, config);

            if (result.isPresent()) {
                OverAllState state = result.get();
                String content = extractResultContent(state, traceId);
                List<String> routeHistory = extractRouteHistory(state);

                log.info("[{}] SupervisorAgentFramework 处理完成: outputLength={}, routeHistory={}",
                        traceId, content != null ? content.length() : 0, routeHistory);

                return new SupervisorResult(content, traceId, "supervisor", routeHistory);
            } else {
                log.warn("[{}] SupervisorAgentFramework 返回空结果", traceId);
                return new SupervisorResult(input, traceId, "empty", List.of());
            }
        } catch (Exception e) {
            log.error("[{}] SupervisorAgentFramework 执行失败: {}", traceId, e.getMessage(), e);
            return new SupervisorResult(
                    "执行失败: " + e.getMessage(),
                    traceId,
                    "error",
                    null
            );
        }
    }

    private String extractResultContent(OverAllState state, String traceId) {
        String[] outputKeys = {"messages", "risk_assessment_result", "analysis_result", "intent_result"};

        for (String key : outputKeys) {
            Optional<Object> value = state.value(key);
            if (value.isPresent()) {
                Object obj = value.get();
                if (obj instanceof String) {
                    return (String) obj;
                }
                return obj.toString();
            }
        }

        log.debug("[{}] 未找到预定义输出键", traceId);
        Optional<Object> messages = state.value("messages");
        if (messages.isPresent()) {
            return messages.get().toString();
        }

        return "";
    }

    private List<String> extractRouteHistory(OverAllState state) {
        List<String> history = new ArrayList<>();
        
        String[] outputKeys = {
            "intent_result", "analysis_result", 
            "risk_assessment_result", "main_output"
        };
        
        for (String key : outputKeys) {
            Optional<Object> value = state.value(key);
            if (value.isPresent()) {
                history.add(key);
            }
        }
        
        return history;
    }

    public record SupervisorResult(
            String content,
            String traceId,
            String selectedAgent,
            List<String> routeHistory
    ) {}
}
