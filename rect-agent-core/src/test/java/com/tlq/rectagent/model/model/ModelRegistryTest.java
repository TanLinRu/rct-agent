package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModelRegistryTest {
    @Test
    public void registerAndQuery() {
        OpenAIProvider openai = new OpenAIProvider(new ModelProviderConfig(){{
            setName("openai"); setEnabled(true); setType("openai"); setModel("gpt-4"); setCostPerToken(0.2);}});
        AnthropicProvider anthropic = new AnthropicProvider(new ModelProviderConfig(){{
            setName("anthropic"); setEnabled(true); setType("anthropic"); setModel("claude-3"); setCostPerToken(0.3);}});

        ModelRegistry reg = new ModelRegistry();
        reg.register(openai);
        reg.register(anthropic);

        assertFalse(reg.isEmpty());
        assertTrue(reg.get("openai").isPresent());
        assertTrue(reg.get("anthropic").isPresent());
    }
}
