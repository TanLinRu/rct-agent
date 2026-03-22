package com.tlq.rectagent.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import com.tlq.rectagent.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModelConfigProperties.class)
public class ModelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ModelAutoConfiguration.class);

    @Autowired(required = false)
    private ModelConfigProperties config;

    private final ModelRegistry registry = new ModelRegistry();

    @PostConstruct
    public void init() {
        if (config != null && config.getProviders() != null) {
            List<ModelProviderConfig> list = new ArrayList<>();
            for (ModelConfigProperties.ProviderConfig pc : config.getProviders()) {
                if (!pc.isEnabled()) continue;
                ModelProviderConfig mpc = new ModelProviderConfig();
                mpc.setName(pc.getName());
                mpc.setType(pc.getType());
                mpc.setModel(pc.getModel());
                mpc.setCostPerToken(pc.getCostPerToken());
                mpc.setPriority(pc.getPriority());
                mpc.setApiKey(pc.getApiKey());
                mpc.setMock(pc.isMock());
                mpc.setEndpoints(pc.getEndpoints());
                mpc.setCapability(pc.getCapability());

                if (autoDetectApiKey(mpc)) {
                    mpc.setEnabled(true);
                    list.add(mpc);
                } else {
                    log.warn("Provider '{}' skipped: no valid API key and mock mode is disabled", pc.getName());
                }
            }
            registry.clear();
            registry.addAll(ModelProviderFactory.createFromConfigs(list));
        }
    }

    private boolean autoDetectApiKey(ModelProviderConfig mpc) {
        if (mpc.isMock()) return true;
        String key = mpc.getApiKey();
        if (key != null && !key.isEmpty()) return true;
        return false;
    }

    @Bean
    public ModelRouter modelRouter() {
        ModelRoutingStrategy strategy = "priority".equalsIgnoreCase(
                (config != null ? config.getRoutingStrategy() : null))
                ? new PriorityBasedStrategy()
                : new CostBasedStrategy();
        return new ModelRouter(registry, strategy);
    }
}
