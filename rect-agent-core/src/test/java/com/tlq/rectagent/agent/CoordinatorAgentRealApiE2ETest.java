package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tlq.rectagent.service.RectAgentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@DisplayName("CoordinatorAgent 全链路真实 API 集成测试")
public class CoordinatorAgentRealApiE2ETest {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    @Autowired
    private RectAgentService rectAgentService;

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    @DisplayName("场景1: 数据安全分析请求 - 完整链路")
    void dataSecurityAnalysis_fullChain() throws GraphRunnerException {
        String userInput = "获取项目 test 2026-01-01 到 2026-02-01 的数据安全风险分析";

        String result = coordinatorAgent.processRequest(userInput);

        assertNotNull(result);
        assertFalse(result.contains("处理请求失败"));
        assertFalse(result.contains("意图识别失败"));
        System.out.println("=== 数据安全分析结果 ===");
        System.out.println(result);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    @DisplayName("场景2: 纯文本查询请求")
    void textQuery_fullChain() throws GraphRunnerException {
        String userInput = "你好，帮我查一下今天的天气";

        String result = coordinatorAgent.processRequest(userInput);

        assertNotNull(result);
        assertFalse(result.contains("处理请求失败"));
        System.out.println("=== 文本查询结果 ===");
        System.out.println(result);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    @DisplayName("场景3: 多意图混合请求")
    void multiIntentRequest_fullChain() throws GraphRunnerException {
        String userInput = "分析项目A的风险，同时生成一份报告";

        String result = coordinatorAgent.processRequest(userInput);

        assertNotNull(result);
        assertFalse(result.contains("处理请求失败"));
        System.out.println("=== 多意图混合请求结果 ===");
        System.out.println(result);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    @DisplayName("场景4: RectAgentService 单 Agent 调用")
    void singleAgentCall_viaService() throws GraphRunnerException {
        rectAgentService.testAgent();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    @DisplayName("场景5: SequentialExecutor 多 Agent 串联")
    void sequentialExecutor_multiAgent() {
        var result = rectAgentService.testSequentialExecutor();

        assertNotNull(result);
        assertNotNull(result.getFinalOutput());
        assertFalse(result.getFinalOutput().contains("异常") || result.getFinalOutput().contains("失败"));
        assertNotNull(result.getData("user_intent"));
        assertNotNull(result.getData("generated_prompt"));
        assertNotNull(result.getData("analysis_result"));
        System.out.println("=== 意图识别 ===");
        System.out.println(result.getData("user_intent"));
        System.out.println("=== 生成的提示词 ===");
        System.out.println(result.getData("generated_prompt"));
        System.out.println("=== 最终分析结果 ===");
        System.out.println(result.getFinalOutput());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    @DisplayName("场景6: 边界测试 - 空输入")
    void boundaryTest_emptyInput() throws GraphRunnerException {
        String result = coordinatorAgent.processRequest("");
        assertNotNull(result);
        System.out.println("=== 空输入结果 ===");
        System.out.println(result);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    @DisplayName("场景7: 意图识别单独测试")
    void intentRecognition_standalone() {
        String userInput = "我想分析项目 test 的数据";
        String result = coordinatorAgent.processRequest(userInput);

        assertNotNull(result);
        System.out.println("=== 意图识别单独测试结果 ===");
        System.out.println(result);
    }
}

