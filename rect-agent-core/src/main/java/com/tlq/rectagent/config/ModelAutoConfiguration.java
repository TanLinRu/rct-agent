package com.tlq.rectagent.config;

import com.tlq.rectagent.model.circuit.CircuitBreakerManager;
import com.tlq.rectagent.model.pool.ChatModelPool;
import com.tlq.rectagent.model.router.AgentModelRouter;
import com.tlq.rectagent.model.router.TrafficShiftingRouter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableConfigurationProperties(ModelConfigProperties.class)
public class ModelAutoConfiguration {

    @Autowired(required = false)
    private ModelConfigProperties config;

    @Autowired
    private ChatModelPool chatModelPool;

    @Autowired
    private AgentModelRouter agentModelRouter;

    @Autowired
    private CircuitBreakerManager circuitBreakerManager;

    @Autowired(required = false)
    private TrafficShiftingRouter trafficShiftingRouter;

    @PostConstruct
    public void init() {
        if (config == null) {
            log.warn("ModelConfigProperties is null, skipping model pool initialization");
            return;
        }

        initProviderConfigs();

        initModelInstances();

        initAgentMapping();

        initFallbackChains();

        initTrafficShifting();

        log.info("ModelAutoConfiguration initialized: providers={}, models={}, mappings={}",
                config.getProviders() != null ? config.getProviders().size() : 0,
                config.getModels() != null ? config.getModels().size() : 0,
                config.getAgentModelMapping() != null ? config.getAgentModelMapping().size() : 0);
    }

    private void initProviderConfigs() {
        if (config.getProviders() == null) {
            return;
        }

        for (Map.Entry<String, ModelConfigProperties.ProviderConfig> entry : config.getProviders().entrySet()) {
            ModelConfigProperties.ProviderConfig providerConfig = entry.getValue();
            if (!providerConfig.isEnabled()) {
                log.debug("Provider {} is disabled, skipping", entry.getKey());
                continue;
            }

            com.tlq.rectagent.model.config.ProviderConfig provider = new com.tlq.rectagent.model.config.ProviderConfig();
            provider.setEnabled(true);
            provider.setBaseUrl(providerConfig.getBaseUrl());
            provider.setApiKey(resolveApiKey(providerConfig));
            provider.setTimeout(providerConfig.getTimeout());
            provider.setMaxRetries(providerConfig.getMaxRetries());

            agentModelRouter.registerProvider(entry.getKey(), provider);
        }
    }

    private String resolveApiKey(ModelConfigProperties.ProviderConfig providerConfig) {
        String apiKey = providerConfig.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }

        if ("dashscope".equalsIgnoreCase(providerConfig.getType())) {
            String envKey = System.getenv("DASHSCOPE_API_KEY");
            if (envKey != null && !envKey.isEmpty()) {
                return envKey;
            }
        } else if ("openai".equalsIgnoreCase(providerConfig.getType())) {
            String envKey = System.getenv("OPENAI_API_KEY");
            if (envKey != null && !envKey.isEmpty()) {
                return envKey;
            }
        } else if ("anthropic".equalsIgnoreCase(providerConfig.getType())) {
            String envKey = System.getenv("ANTHROPIC_API_KEY");
            if (envKey != null && !envKey.isEmpty()) {
                return envKey;
            }
        }

        return null;
    }

    private void initModelInstances() {
        if (config.getModels() == null || config.getProviders() == null) {
            return;
        }

        for (Map.Entry<String, ModelConfigProperties.ModelInstanceConfig> entry : config.getModels().entrySet()) {
            ModelConfigProperties.ModelInstanceConfig modelConfig = entry.getValue();
            String providerType = modelConfig.getProvider();

            ModelConfigProperties.ProviderConfig providerConfig = config.getProviders().get(providerType);
            if (providerConfig == null || !providerConfig.isEnabled()) {
                log.warn("Provider '{}' not found or disabled for model '{}'", providerType, entry.getKey());
                continue;
            }

            com.tlq.rectagent.model.config.ProviderConfig provider = new com.tlq.rectagent.model.config.ProviderConfig();
            provider.setEnabled(true);
            provider.setBaseUrl(providerConfig.getBaseUrl());
            provider.setApiKey(resolveApiKey(providerConfig));
            provider.setTimeout(providerConfig.getTimeout());
            provider.setMaxRetries(providerConfig.getMaxRetries());

            com.tlq.rectagent.model.config.ModelInstanceConfig instanceConfig =
                    new com.tlq.rectagent.model.config.ModelInstanceConfig();
            instanceConfig.setName(entry.getKey());
            instanceConfig.setProvider(providerType);
            instanceConfig.setModel(modelConfig.getModel());
            instanceConfig.setCostPerToken(modelConfig.getCostPerToken());
            instanceConfig.setPriority(modelConfig.getPriority());
            instanceConfig.setCapabilities(modelConfig.getCapabilities());

            try {
                agentModelRouter.registerModelInstance(entry.getKey(), instanceConfig, provider);
            } catch (Exception e) {
                log.error("Failed to register model '{}': {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void initAgentMapping() {
        if (config.getAgentModelMapping() != null && !config.getAgentModelMapping().isEmpty()) {
            agentModelRouter.setAgentModelMapping(config.getAgentModelMapping());
        }
    }

    private void initFallbackChains() {
        if (config.getModelFallbackChains() != null && !config.getModelFallbackChains().isEmpty()) {
            Map<String, List<String>> chains = new HashMap<>();
            config.getModelFallbackChains().forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    chains.put(key, Arrays.asList(value.split(",")));
                }
            });
            agentModelRouter.setModelFallbackChains(chains);
        }
    }

    private void initTrafficShifting() {
        if (trafficShiftingRouter == null) {
            return;
        }

        if (config.getTrafficShifting() != null) {
            trafficShiftingRouter.setEnabled(config.getTrafficShifting().isEnabled());

            if (config.getTrafficShifting().getRules() != null) {
                List<com.tlq.rectagent.model.config.TrafficShiftingRule> rules = config.getTrafficShifting().getRules()
                        .stream()
                        .map(r -> {
                            com.tlq.rectagent.model.config.TrafficShiftingRule rule =
                                    new com.tlq.rectagent.model.config.TrafficShiftingRule();
                            rule.setAgent(r.getAgent());
                            rule.setModel(r.getModel());
                            rule.setPercentage(r.getPercentage());
                            return rule;
                        })
                        .toList();
                trafficShiftingRouter.registerRules(rules);
            }
        }
    }

    @Bean
    public ChatModelPool chatModelPool() {
        return new ChatModelPool();
    }

    @Bean
    public CircuitBreakerManager circuitBreakerManager() {
        return new CircuitBreakerManager();
    }

    @Bean
    @Primary
    public AgentModelRouter agentModelRouter() {
        return new AgentModelRouter();
    }

    @Bean
    public TrafficShiftingRouter trafficShiftingRouter() {
        return new TrafficShiftingRouter();
    }
}
