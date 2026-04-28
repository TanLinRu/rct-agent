package com.tlq.rectagent.agent;

import com.tlq.rectagent.config.ChatModelFactory;
import com.tlq.rectagent.config.MockChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.ai.openai.api-key=test-mock-key",
    "spring.ai.openai.base-url=https://mock.openai.com/v1",
    "spring.ai.openai.chat.enabled=false"
})
@DisplayName("SupervisorAgentFramework 框架版测试")
public class SupervisorAgentFrameworkTest {

    @Configuration
    static class TestConfig {
        @Bean
        @Primary
        public ChatModel chatModel() {
            return new MockChatModel();
        }

        @Bean
        public ChatModelFactory chatModelFactory(ChatModel chatModel) {
            return new ChatModelFactory(chatModel);
        }
    }

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private SupervisorAgentFramework supervisorAgent;

    private static final String TEST_SESSION_ID = "test-session-" + System.currentTimeMillis();
    private static final String TEST_USER_ID = "test-user-" + System.currentTimeMillis();

    @BeforeEach
    public void setUp() {
        assertNotNull(chatModelFactory, "ChatModelFactory should be configured");
        assertNotNull(supervisorAgent, "SupervisorAgentFramework should be initialized");
        
        if (chatModelFactory.isMockMode()) {
            System.out.println("警告: 当前使用 Mock 模式，非真实 API 调用");
        } else {
            System.out.println("当前使用真实 API: " + 
                (System.getenv("OPENAI_BASE_URL") != null ? 
                    System.getenv("OPENAI_BASE_URL") : 
                    "https://dashscope.aliyuncs.com/compatible-mode"));
        }
    }

    @Nested
    @DisplayName("风控场景测试")
    class RiskControlScenarioTests {

        @Test
        @DisplayName("场景1: 简单风险查询 - 意图识别")
        public void testScenario1IntentRecognition() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "查询用户ID为1001的风险等级",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            assertNotNull(result.traceId());
            assertNotNull(result.content());
            System.out.println("【场景1】意图识别结果: " + result.content());
            System.out.println("路由历史: " + result.routeHistory());
        }

        @Test
        @DisplayName("场景2: 交易数据分析")
        public void testScenario2TransactionAnalysis() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "分析2026年3月份交易金额超过10万元的风险交易",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            assertNotNull(result.content());
            System.out.println("【场景2】交易数据分析结果: " + result.content());
            System.out.println("路由历史: " + result.routeHistory());
        }

        @Test
        @DisplayName("场景3: 风险评估")
        public void testScenario3RiskAssessment() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "对账户9988进行风险评估，账户近期有多笔大额转出",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("【场景3】风险评估结果: " + result.content());
            System.out.println("路由历史: " + result.routeHistory());
        }

        @Test
        @DisplayName("场景4: 完整风控流程 - 多步骤")
        public void testScenario4FullFlow() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "先识别用户12345的查询意图，然后分析其交易记录，最后给出风险评估",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("【场景4】完整流程结果: " + result.content());
            System.out.println("路由历史: " + result.routeHistory());
            
            assertTrue(result.routeHistory().size() > 0, "应该有路由历史");
        }

        @Test
        @DisplayName("场景5: 高风险账户识别")
        public void testScenario5HighRiskAccounts() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "识别过去一周内交易金额异常（单日累计超过50万）的账户",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("【场景5】高风险账户识别结果: " + result.content());
        }

        @Test
        @DisplayName("场景6: 异常交易监控")
        public void testScenario6AbnormalTransactions() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "监控夜间（23:00-05:00）发生的交易，识别可疑模式",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("【场景6】异常交易监控结果: " + result.content());
        }

        @Test
        @DisplayName("场景7: 反欺诈检测")
        public void testScenario7AntiFraud() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "检测是否存在盗用身份进行贷款的可疑行为，申请人信息：姓名张三，身份证110***********1234",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("【场景7】反欺诈检测结果: " + result.content());
        }

        @Test
        @DisplayName("场景8: 信用风险评估")
        public void testScenario8CreditRisk() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "对授信客户进行信用风险评估，客户ID：VIP2026001，当前负债率75%",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("【场景8】信用风险评估结果: " + result.content());
        }

        @Test
        @DisplayName("场景9: 风控报告生成")
        public void testScenario9RiskReport() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "生成2026年第一季度风控报告，包含风险等级分布、TOP10风险账户、建议措施",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("【场景9】风控报告生成结果: " + result.content());
        }

        @Test
        @DisplayName("场景10: 合规检查")
        public void testScenario10ComplianceCheck() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "检查客户ID:8888是否存在反洗钱合规风险，过去6个月有频繁的同名转账",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("【场景10】合规检查结果: " + result.content());
        }
    }

    @Nested
    @DisplayName("基础功能测试")
    class BasicFunctionTests {

        @Test
        @DisplayName("测试返回结果结构完整性")
        public void testResultStructureCompleteness() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "风险数据分析",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertAll("Result structure validation",
                    () -> assertNotNull(result, "Result should not be null"),
                    () -> assertNotNull(result.traceId(), "traceId should not be null"),
                    () -> assertNotNull(result.content(), "content should not be null"),
                    () -> assertNotNull(result.selectedAgent(), "selectedAgent should not be null"),
                    () -> assertNotNull(result.routeHistory(), "routeHistory should not be null")
            );

            System.out.println("Result structure: " + result);
        }

        @Test
        @DisplayName("测试空输入处理")
        public void testEmptyInput() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            assertNotNull(result.traceId());
            System.out.println("空输入测试结果: " + result.content());
        }

        @Test
        @DisplayName("测试简单查询")
        public void testSimpleQuery() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "查看今日风控概览",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            assertNotNull(result.content());
            System.out.println("简单查询结果: " + result.content());
        }
    }

    @Nested
    @DisplayName("边界测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("测试超长输入")
        public void testLongInput() {
            String longInput = "分析风险情况 " + "这是一个很长的输入 ".repeat(100);
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    longInput,
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("超长输入测试完成");
        }

        @Test
        @DisplayName("测试特殊字符输入")
        public void testSpecialCharacters() {
            SupervisorAgentFramework.SupervisorResult result = supervisorAgent.invoke(
                    "分析用户 <id>123</id> 的风险情况 && &&",
                    TEST_SESSION_ID,
                    TEST_USER_ID
            );

            assertNotNull(result);
            System.out.println("特殊字符测试完成: " + result.content());
        }
    }
}
