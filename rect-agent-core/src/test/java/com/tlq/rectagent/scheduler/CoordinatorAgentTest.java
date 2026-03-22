package com.tlq.rectagent.scheduler;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.tlq.rectagent.agent.CoordinatorAgent;
import com.tlq.rectagent.agent.AgentDataContext;
import com.tlq.rectagent.agent.IntentRecognitionAgent;
import com.tlq.rectagent.agent.DynamicPromptAgent;
import com.tlq.rectagent.agent.DataAnalysisAgent;
import com.tlq.rectagent.agent.SequentialAgentExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
public class CoordinatorAgentTest {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    @MockBean
    private SequentialAgentExecutor sequentialAgentExecutor;

    @MockBean
    private IntentRecognitionAgent intentRecognitionAgent;

    @MockBean
    private DynamicPromptAgent dynamicPromptAgent;

    @MockBean
    private DataAnalysisAgent dataAnalysisAgent;

    @Test
    public void testProcessRequestReturnsMockFinal() {
        // Prepare mock ReactAgent instances for the three sub-agents
        ReactAgent mockIntent = org.mockito.Mockito.mock(ReactAgent.class);
        ReactAgent mockPrompt = org.mockito.Mockito.mock(ReactAgent.class);
        ReactAgent mockData = org.mockito.Mockito.mock(ReactAgent.class);

        // Wire mocks to be returned by getAgent()
        when(intentRecognitionAgent.getAgent()).thenReturn(mockIntent);
        when(dynamicPromptAgent.getAgent()).thenReturn(mockPrompt);
        when(dataAnalysisAgent.getAgent()).thenReturn(mockData);

        // Prepare a mock result for the SequentialAgentExecutor
        AgentDataContext context = new AgentDataContext();
        Map<String, String> outputs = new LinkedHashMap<>();
        SequentialAgentExecutor.SequentialResult mockResult = new SequentialAgentExecutor.SequentialResult(
                "mock_final_output", context, outputs);
        when(sequentialAgentExecutor.execute(anyList(), anyString(), anyMap())).thenReturn(mockResult);

        // Execute
        String result = coordinatorAgent.processRequest("测试输入");

        // Verify
        assertEquals("mock_final_output", result);
    }
}
