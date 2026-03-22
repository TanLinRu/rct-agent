package com.tlq.rectagent.agent;

import com.tlq.rectagent.model.ModelProvider;
import com.tlq.rectagent.model.ModelRegistry;
import com.tlq.rectagent.model.ModelRouter;
import com.tlq.rectagent.model.CostBasedStrategy;
import com.tlq.rectagent.agent.tools.AgentTool;
import com.tlq.rectagent.agent.tools.IntentTool;
import com.tlq.rectagent.agent.tools.AnalysisTool;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

public class SupervisorAgentTest {
    @Test
    public void orchestratesPipelineAndRoutes() {
        // Dummy provider
        ModelProvider dummy = new ModelProvider() {
            @Override public String getName() { return "dummy"; }
            @Override public boolean isEnabled() { return true; }
            @Override public double getCostPerToken() { return 0.0; }
            @Override public String call(String input) { return "OUT:" + input; }
            @Override public int getPriority() { return 0; }
        };

        ModelRegistry reg = new ModelRegistry();
        reg.register(dummy);
        ModelRouter router = new ModelRouter(reg, new CostBasedStrategy());

        IntentTool intent = new IntentTool("intent");
        AnalysisTool analysis = new AnalysisTool("analysis");
        SupervisorAgent supervisor = new SupervisorAgent(router, Arrays.asList((AgentTool)intent, (AgentTool)analysis));

        String out = supervisor.invoke("hello");
        assertEquals("OUT:hello", out);
    }
}
