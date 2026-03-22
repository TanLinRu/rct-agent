package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;

public class ModelRouterTest {
    @Test
    public void testCostBasedRoutingChoosesCheapestProvider() {
        ModelProviderConfig dashCfg = new ModelProviderConfig();
        dashCfg.setName("dashscope");
        dashCfg.setEnabled(true);
        dashCfg.setType("dashscope");
        dashCfg.setModel("default");
        dashCfg.setCostPerToken(0.5);
        dashCfg.setMock(true);

        ModelProviderConfig openaiCfg = new ModelProviderConfig();
        openaiCfg.setName("openai");
        openaiCfg.setEnabled(true);
        openaiCfg.setType("openai");
        openaiCfg.setModel("gpt-4");
        openaiCfg.setCostPerToken(0.3);
        openaiCfg.setMock(true);

        ModelProvider dash = new DashScopeProvider(dashCfg);
        ModelProvider openai = new OpenAIProvider(openaiCfg);

        ModelRegistry registry = new ModelRegistry();
        registry.register(dash);
        registry.register(openai);

        ModelRouter router = new ModelRouter(registry, new CostBasedStrategy());

        String result = router.route("hello world");
        // Expect the cheaper provider's path to be used
        assertTrue(result.startsWith("[OpenAI:"));
    }
}
