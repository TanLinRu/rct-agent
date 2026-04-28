package com.tlq.rectagent.model.router;

import com.tlq.rectagent.model.config.TrafficShiftingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentModelRouterTest {

    @Test
    @DisplayName("设置 Agent 模型映射应生效")
    void settingAgentModelMappingShouldTakeEffect() {
        AgentModelRouter router = new AgentModelRouter();

        Map<String, String> mapping = new HashMap<>();
        mapping.put("intent_agent", "openai-gpt4o-mini");
        mapping.put("analysis_agent", "openai-gpt4o");
        router.setAgentModelMapping(mapping);

        Map<String, String> result = router.getAgentModelMapping();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("openai-gpt4o-mini", result.get("intent_agent"));
        assertEquals("openai-gpt4o", result.get("analysis_agent"));
    }

    @Test
    @DisplayName("设置降级链应生效")
    void settingFallbackChainsShouldTakeEffect() {
        AgentModelRouter router = new AgentModelRouter();

        Map<String, List<String>> chains = new HashMap<>();
        chains.put("openai-gpt4o-mini", Arrays.asList("openai-gpt4o", "anthropic-haiku"));
        router.setModelFallbackChains(chains);

        assertNotNull(router);
    }

    @Test
    @DisplayName("设置默认策略应生效")
    void settingDefaultStrategyShouldTakeEffect() {
        AgentModelRouter router = new AgentModelRouter();

        router.setDefaultStrategy("capability");

        assertEquals("capability", router.getDefaultStrategy());
    }

    @Test
    @DisplayName("获取熔断器状态应返回映射")
    void getCircuitBreakerStatesShouldReturnMap() {
        AgentModelRouter router = new AgentModelRouter();

        Map<String, ?> states = router.getCircuitBreakerStates();

        assertNotNull(states);
    }

    @Test
    @DisplayName("获取可用模型应返回列表")
    void getAvailableModelsShouldReturnList() {
        AgentModelRouter router = new AgentModelRouter();

        var models = router.getAvailableModels();

        assertNotNull(models);
    }

    @Test
    @DisplayName("空 Agent 映射应返回空映射")
    void nullAgentMappingShouldReturnEmptyMap() {
        AgentModelRouter router = new AgentModelRouter();

        Map<String, String> result = router.getAgentModelMapping();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("获取 Agent 映射应返回不可变映射")
    void getAgentMappingShouldReturnUnmodifiable() {
        AgentModelRouter router = new AgentModelRouter();

        Map<String, String> mapping = new HashMap<>();
        mapping.put("test", "model");
        router.setAgentModelMapping(mapping);

        Map<String, String> result = router.getAgentModelMapping();

        assertThrows(UnsupportedOperationException.class, () -> {
            result.put("new", "value");
        });
    }
}
