package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModelRegistryTest {
    @Test
    public void registerAndQuery() {
        DashScopeProvider dash = new DashScopeProvider(new ModelProviderConfig(){{
            setName("dash"); setEnabled(true); setType("dashscope"); setModel("default"); setCostPerToken(0.1);}});
        OpenAIProvider openai = new OpenAIProvider(new ModelProviderConfig(){{
            setName("openai"); setEnabled(true); setType("openai"); setModel("gpt-4"); setCostPerToken(0.2);}});

        ModelRegistry reg = new ModelRegistry();
        reg.register(dash);
        reg.register(openai);

        assertFalse(reg.isEmpty());
        assertTrue(reg.get("dash").isPresent());
        assertTrue(reg.get("openai").isPresent());
    }
}
