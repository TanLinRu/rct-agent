package com.tlq.rectagent.config;

import com.tlq.rectagent.model.ModelProvider;
import com.tlq.rectagent.model.ModelRegistry;
import com.tlq.rectagent.model.ModelProviderConfig;
import com.tlq.rectagent.model.DashScopeProvider;
import com.tlq.rectagent.model.OpenAIProvider;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

public class AgentModelSelectorTest {
    @Test
    public void selectsProviderForAgent() {
        ModelProviderConfig dcfg = new ModelProviderConfig();
        dcfg.setName("dash"); dcfg.setEnabled(true); dcfg.setType("dashscope");
        dcfg.setModel("default"); dcfg.setPriority(1);
        DashScopeProvider dash = new DashScopeProvider(dcfg);

        ModelProviderConfig ocfg = new ModelProviderConfig();
        ocfg.setName("openai"); ocfg.setEnabled(true); ocfg.setType("openai");
        ocfg.setModel("gpt-4"); ocfg.setPriority(2);
        OpenAIProvider openai = new OpenAIProvider(ocfg);

        ModelRegistry reg = new ModelRegistry();
        reg.register(dash); reg.register(openai);

        Map<String, String> map = new HashMap<>();
        map.put("intent_recognition_agent", "dash");
        map.put("data_analysis_agent", "openai");

        AgentModelSelector selector = new AgentModelSelector(reg, map);

        ModelProvider selected = selector.selectForAgentOrDefault("intent_recognition_agent");
        assertNotNull(selected);
        assertEquals("dash", selected.getName());

        ModelProvider selected2 = selector.selectForAgentOrDefault("data_analysis_agent");
        assertNotNull(selected2);
        assertEquals("openai", selected2.getName());

        ModelProvider fallback = selector.selectForAgentOrDefault("unknown_agent");
        assertNull(fallback);
    }
}
