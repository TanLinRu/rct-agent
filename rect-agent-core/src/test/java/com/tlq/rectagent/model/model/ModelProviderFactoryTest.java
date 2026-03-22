package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class ModelProviderFactoryTest {
    @Test
    public void createsDashScopeProviderFromConfig() {
        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("dash");
        cfg.setEnabled(true);
        cfg.setType("dashscope");
        cfg.setModel("default");
        cfg.setCostPerToken(0.1);
        ModelProvider p = ModelProviderFactory.createFromConfig(cfg);
        assertTrue(p instanceof DashScopeProvider);
    }

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
}
