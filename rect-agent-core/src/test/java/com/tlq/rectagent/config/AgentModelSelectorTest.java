package com.tlq.rectagent.config;

import com.tlq.rectagent.model.ModelProvider;
import com.tlq.rectagent.model.ModelRegistry;
import com.tlq.rectagent.model.ModelProviderConfig;
import com.tlq.rectagent.model.OpenAIProvider;
import com.tlq.rectagent.model.AnthropicProvider;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

public class AgentModelSelectorTest {
    @Test
    public void selectsProviderForAgent() {
        ModelProviderConfig openaiCfg = new ModelProviderConfig();
        openaiCfg.setName("openai"); openaiCfg.setEnabled(true); openaiCfg.setType("openai");
        openaiCfg.setModel("gpt-4"); openaiCfg.setPriority(1);
        OpenAIProvider openai = new OpenAIProvider(openaiCfg);

        ModelProviderConfig anthropicCfg = new ModelProviderConfig();
        anthropicCfg.setName("anthropic"); anthropicCfg.setEnabled(true); anthropicCfg.setType("anthropic");
        anthropicCfg.setModel("claude-3"); anthropicCfg.setPriority(2);
        AnthropicProvider anthropic = new AnthropicProvider(anthropicCfg);

        ModelRegistry reg = new ModelRegistry();
        reg.register(openai); reg.register(anthropic);

        Map<String, String> map = new HashMap<>();
        map.put("intent_recognition_agent", "openai");
        map.put("data_analysis_agent", "anthropic");

        AgentModelSelector selector = new AgentModelSelector(reg, map);

        ModelProvider selected = selector.selectForAgentOrDefault("intent_recognition_agent");
        assertNotNull(selected);
        assertEquals("openai", selected.getName());

        ModelProvider selected2 = selector.selectForAgentOrDefault("data_analysis_agent");
        assertNotNull(selected2);
        assertEquals("anthropic", selected2.getName());

        ModelProvider fallback = selector.selectForAgentOrDefault("unknown_agent");
        assertNull(fallback);
    }
}
