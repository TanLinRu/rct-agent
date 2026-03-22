package com.tlq.rectagent.config;

import com.tlq.rectagent.config.ModelAutoConfiguration;
import com.tlq.rectagent.config.ModelConfigProperties;
import com.tlq.rectagent.config.ModelConfigProperties.ProviderConfig;
import com.tlq.rectagent.model.DashScopeProvider;
import com.tlq.rectagent.model.ModelRouter;
import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Arrays;

public class ModelAutoConfigurationTest {
    @Test
    public void autoRegistrationSetsUpRouter() throws Exception {
        ModelAutoConfiguration cfg = new ModelAutoConfiguration();
        ModelConfigProperties props = new ModelConfigProperties();
        ModelConfigProperties.ProviderConfig p = new ModelConfigProperties.ProviderConfig();
        p.setName("dash");
        p.setEnabled(true);
        p.setType("dashscope");
        p.setApiKey("dummy");
        p.setModel("default");
        p.setCostPerToken(0.1);
        p.setMock(true);
        p.setPriority(1);
        props.setProviders(Arrays.asList(p));

        Field f = ModelAutoConfiguration.class.getDeclaredField("config");
        f.setAccessible(true);
        f.set(cfg, props);

        // initialize registry via init()
        cfg.init();

        ModelRouter router = cfg.modelRouter();
        assertNotNull(router);
        String out = router.route("hello");
        // DashScope provider should respond with its name (dash)
        assertTrue(out.contains("DashScope"));
    }
}
