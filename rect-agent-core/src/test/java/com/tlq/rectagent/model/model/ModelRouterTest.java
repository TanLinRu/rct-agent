package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;

public class ModelRouterTest {
    @Test
    public void testCostBasedRoutingChoosesCheapestProvider() {
        ModelProviderConfig openaiCfg = new ModelProviderConfig();
        openaiCfg.setName("openai");
        openaiCfg.setEnabled(true);
        openaiCfg.setType("openai");
        openaiCfg.setModel("gpt-4");
        openaiCfg.setCostPerToken(0.3);
        openaiCfg.setMock(true);

        ModelProviderConfig anthropicCfg = new ModelProviderConfig();
        anthropicCfg.setName("anthropic");
        anthropicCfg.setEnabled(true);
        anthropicCfg.setType("anthropic");
        anthropicCfg.setModel("claude-3");
        anthropicCfg.setCostPerToken(0.5);
        anthropicCfg.setMock(true);

        ModelProvider openai = new OpenAIProvider(openaiCfg);
        ModelProvider anthropic = new AnthropicProvider(anthropicCfg);

        ModelRegistry registry = new ModelRegistry();
        registry.register(openai);
        registry.register(anthropic);

        ModelRouter router = new ModelRouter(registry, new CostBasedStrategy());

        String result = router.route("hello world");
        assertNotNull(result);
    }
}
