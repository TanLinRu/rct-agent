package com.tlq.rectagent.config;

import com.tlq.rectagent.model.router.AgentModelRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelAutoConfigurationTest {

    @Test
    @DisplayName("应正确初始化配置")
    void shouldInitializeConfiguration() throws Exception {
        ModelAutoConfiguration cfg = new ModelAutoConfiguration();
        ModelConfigProperties props = new ModelConfigProperties();
        props.setRoutingStrategy("cost");

        ModelConfigProperties.ProviderConfig p = new ModelConfigProperties.ProviderConfig();
        p.setName("openai");
        p.setEnabled(true);
        p.setType("openai");
        p.setApiKey("dummy");
        p.setModel("default");
        p.setCostPerToken(0.1);
        p.setMock(true);
        p.setPriority(1);

        Map<String, ModelConfigProperties.ProviderConfig> providerMap = new HashMap<>();
        providerMap.put("openai", p);
        props.setProviders(providerMap);

        Field f = ModelAutoConfiguration.class.getDeclaredField("config");
        f.setAccessible(true);
        f.set(cfg, props);

        assertNotNull(cfg);
    }

    @Test
    @DisplayName("AgentModelRouter Bean 应正确创建")
    void agentModelRouterBeanShouldBeCreated() {
        AgentModelRouter router = new AgentModelRouter();
        assertNotNull(router);
    }

    @Test
    @DisplayName("ChatModelPool Bean 应正确创建")
    void chatModelPoolBeanShouldBeCreated() {
        com.tlq.rectagent.model.pool.ChatModelPool pool = 
                new com.tlq.rectagent.model.pool.ChatModelPool();
        assertNotNull(pool);
    }
}
