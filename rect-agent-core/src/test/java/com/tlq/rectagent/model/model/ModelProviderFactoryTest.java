package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class ModelProviderFactoryTest {
    @Test
    public void createsOpenAIProviderFromConfig() {
        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai");
        cfg.setEnabled(true);
        cfg.setType("openai");
        cfg.setModel("gpt-4");
        cfg.setCostPerToken(0.2);
        ModelProvider p = ModelProviderFactory.createFromConfig(cfg);
        assertTrue(p instanceof OpenAIProvider);
    }

    @Test
    public void createsAnthropicProviderFromConfig() {
        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("anthropic");
        cfg.setEnabled(true);
        cfg.setType("anthropic");
        cfg.setModel("claude-3");
        cfg.setCostPerToken(0.3);
        ModelProvider p = ModelProviderFactory.createFromConfig(cfg);
        assertTrue(p instanceof AnthropicProvider);
    }
}
