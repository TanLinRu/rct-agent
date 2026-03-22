package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CoordinatorAgentE2ETest {

    private static class TestAssistantMessage extends AssistantMessage {
        private final String text;
        TestAssistantMessage(String text) { super(""); this.text = text; }
        @Override public String getText() { return text; }
    }

    private AssistantMessage msg(String text) { return new TestAssistantMessage(text); }

    @Test
    public void fullPipeline_intentRecognition_to_dynamicPrompt_to_analysis() throws Exception {
        AssistantMessage r1 = msg("{\"intent\":\"data_analysis\",\"entities\":[\"test\"],\"confidence\":0.95}");
        ReactAgent mockIntent = mock(ReactAgent.class);
        when(mockIntent.call(anyString())).thenReturn(r1);
        AgentReflectionUtil.setMockAgentName(mockIntent, "intent_recognition_agent");

        AssistantMessage r2 = msg("请分析项目 test 的风险数据");
        ReactAgent mockPrompt = mock(ReactAgent.class);
        when(mockPrompt.call(anyString())).thenReturn(r2);
        AgentReflectionUtil.setMockAgentName(mockPrompt, "dynamic_prompt_agent");

        AssistantMessage r3 = msg("分析完成：发现3个中等风险项。");
        ReactAgent mockAnalysis = mock(ReactAgent.class);
        when(mockAnalysis.call(anyString())).thenReturn(r3);
        AgentReflectionUtil.setMockAgentName(mockAnalysis, "data_analysis_agent");

        List<ReactAgent> agents = Arrays.asList(mockIntent, mockPrompt, mockAnalysis);
        Map<String, String> outputKeyMap = new HashMap<>();
        outputKeyMap.put("intent_recognition_agent", "user_intent");
        outputKeyMap.put("dynamic_prompt_agent", "generated_prompt");
        outputKeyMap.put("data_analysis_agent", "analysis_result");

        SequentialAgentExecutor executor = new SequentialAgentExecutor();
        SequentialAgentExecutor.SequentialResult result = executor.execute(
            agents, "分析项目 test 的数据", outputKeyMap);

        assertNotNull(result);
        assertEquals("分析完成：发现3个中等风险项。", result.getDataContext().get("analysis_result"));
        assertEquals("请分析项目 test 的风险数据", result.getDataContext().get("generated_prompt"));
        assertTrue(result.getDataContext().get("user_intent").startsWith("{\"intent\":\"data_analysis\""));
        assertEquals("分析完成：发现3个中等风险项。", result.getFinalOutput());
    }

    @Test
    public void outputKeyMapping_correctlyStoresAgentOutputs() throws Exception {
        AssistantMessage r1 = msg("output1");
        ReactAgent agent1 = mock(ReactAgent.class);
        when(agent1.call(anyString())).thenReturn(r1);
        AgentReflectionUtil.setMockAgentName(agent1, "first_agent");

        AssistantMessage r2 = msg("output2");
        ReactAgent agent2 = mock(ReactAgent.class);
        when(agent2.call(anyString())).thenReturn(r2);
        AgentReflectionUtil.setMockAgentName(agent2, "second_agent");

        Map<String, String> outputKeyMap = new HashMap<>();
        outputKeyMap.put("first_agent", "key_one");
        outputKeyMap.put("second_agent", "key_two");

        SequentialAgentExecutor executor = new SequentialAgentExecutor();
        SequentialAgentExecutor.SequentialResult result = executor.execute(
            Arrays.asList(agent1, agent2), "initial input", outputKeyMap);

        assertEquals("output1", result.getDataContext().get("key_one"));
        assertEquals("output2", result.getDataContext().get("key_two"));
        assertEquals("output2", result.getFinalOutput());
    }

    @Test
    public void defaultOutputKey_usedWhenNotInMap() throws Exception {
        AssistantMessage r = msg("agent_output");
        ReactAgent agent = mock(ReactAgent.class);
        when(agent.call(anyString())).thenReturn(r);
        AgentReflectionUtil.setMockAgentName(agent, "test_agent");

        SequentialAgentExecutor executor = new SequentialAgentExecutor();
        SequentialAgentExecutor.SequentialResult result = executor.execute(
            Arrays.asList(agent), "input", new HashMap<>());

        assertEquals("agent_output", result.getDataContext().get("test_agent"));
    }

    @Test
    public void chain_outputOfEachAgent_becomesInputOfNext() throws Exception {
        AssistantMessage r1 = msg("result_from_step1");
        ReactAgent agent1 = mock(ReactAgent.class);
        when(agent1.call(eq("initial"))).thenReturn(r1);
        AgentReflectionUtil.setMockAgentName(agent1, "agent1");

        AssistantMessage r2 = msg("result_from_step2");
        ReactAgent agent2 = mock(ReactAgent.class);
        when(agent2.call(eq("result_from_step1"))).thenReturn(r2);
        AgentReflectionUtil.setMockAgentName(agent2, "agent2");

        SequentialAgentExecutor executor = new SequentialAgentExecutor();
        SequentialAgentExecutor.SequentialResult result = executor.execute(
            Arrays.asList(agent1, agent2), "initial", new HashMap<>());

        assertEquals("result_from_step2", result.getFinalOutput());
        verify(agent1).call(eq("initial"));
        verify(agent2).call(eq("result_from_step1"));
    }

    @Test
    public void throwsRuntimeException_whenAgentCallFails() throws GraphRunnerException {
        ReactAgent failingAgent = mock(ReactAgent.class);
        when(failingAgent.call(anyString())).thenThrow(new GraphRunnerException("Intent recognition failed"));
        AgentReflectionUtil.setMockAgentName(failingAgent, "failing_agent");

        SequentialAgentExecutor executor = new SequentialAgentExecutor();
        try {
            executor.execute(Arrays.asList(failingAgent), "input", new HashMap<>());
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Error message should contain 'failing_agent'", e.getMessage().contains("failing_agent"));
        }
    }

    @Test
    public void simpleExecuteMethod_usesOutputAsDefaultKey() throws Exception {
        AssistantMessage r = msg("simple_output");
        ReactAgent agent = mock(ReactAgent.class);
        when(agent.call(anyString())).thenReturn(r);
        AgentReflectionUtil.setMockAgentName(agent, "simple_agent");

        SequentialAgentExecutor executor = new SequentialAgentExecutor();
        String result = executor.executeSimple(Arrays.asList(agent), "simple input");

        assertEquals("simple_output", result);
    }

    @Test
    public void multipleInvocations_eachIsIndependent() throws Exception {
        AssistantMessage r = msg("response");
        ReactAgent agent = mock(ReactAgent.class);
        when(agent.call(anyString())).thenReturn(r);
        AgentReflectionUtil.setMockAgentName(agent, "repeat_agent");

        SequentialAgentExecutor executor = new SequentialAgentExecutor();

        String r1 = executor.executeSimple(Arrays.asList(agent), "request 1");
        String r2 = executor.executeSimple(Arrays.asList(agent), "request 2");

        assertEquals("response", r1);
        assertEquals("response", r2);
        verify(agent, times(2)).call(anyString());
    }

    @Test
    public void agentDataContext_replacePlaceholders() {
        AgentDataContext ctx = new AgentDataContext();
        ctx.put("user_intent", "查询项目数据");
        ctx.put("generated_prompt", "请分析{user_intent}并生成报告");

        String result = ctx.replacePlaceholders("{generated_prompt}");
        assertEquals("请分析查询项目数据并生成报告", result);
    }

    @Test
    public void agentDataContext_missingPlaceholder_keptAsIs() {
        AgentDataContext ctx = new AgentDataContext();
        ctx.put("user_intent", "查询项目数据");

        String result = ctx.replacePlaceholders("任务：{generated_prompt}");
        assertEquals("任务：{generated_prompt}", result);
    }
}
