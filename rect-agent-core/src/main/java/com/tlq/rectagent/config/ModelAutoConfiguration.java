package com.tlq.rectagent.config;

import com.tlq.rectagent.model.circuit.CircuitBreakerManager;
import com.tlq.rectagent.model.pool.ChatModelPool;
import com.tlq.rectagent.model.router.AgentModelRouter;
import com.tlq.rectagent.model.router.TrafficShiftingRouter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private AgentModelRouter agentModelRouter;
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

    @Bean
    public CircuitBreakerManager circuitBreakerManager(@Qualifier("chatModelPool") ChatModelPool chatModelPool) {
        CircuitBreakerManager manager = new CircuitBreakerManager();
        manager.setModelPool(chatModelPool);
        return manager;
    }

    @Bean
    @Primary
    public AgentModelRouter agentModelRouter(@Qualifier("chatModelPool") ChatModelPool chatModelPool,
                                           @Qualifier("capabilityRoutingStrategy") com.tlq.rectagent.model.routing.CapabilityRoutingStrategy capabilityStrategy,
                                           @Qualifier("priorityRoutingStrategy") com.tlq.rectagent.model.routing.PriorityRoutingStrategy priorityStrategy,
                                           @Qualifier("costRoutingStrategy") com.tlq.rectagent.model.routing.CostRoutingStrategy costStrategy,
                                           TrafficShiftingRouter trafficShiftingRouter) {
        AgentModelRouter router = new AgentModelRouter();
        router.setModelPool(chatModelPool);
        router.setCapabilityStrategy(capabilityStrategy);
        router.setPriorityStrategy(priorityStrategy);
        router.setCostStrategy(costStrategy);
        router.setTrafficShiftingRouter(trafficShiftingRouter);
        return router;
    }

    @Bean
    public TrafficShiftingRouter trafficShiftingRouter(@Qualifier("chatModelPool") ChatModelPool chatModelPool) {
        TrafficShiftingRouter router = new TrafficShiftingRouter();
        router.setModelPool(chatModelPool);
        this.trafficShiftingRouter = router;
        return router;
    }

    @Bean
    @Primary
    public com.tlq.rectagent.model.routing.CapabilityRoutingStrategy capabilityRoutingStrategy(@Qualifier("chatModelPool") ChatModelPool chatModelPool) {
        com.tlq.rectagent.model.routing.CapabilityRoutingStrategy strategy = 
                new com.tlq.rectagent.model.routing.CapabilityRoutingStrategy();
        strategy.setModelPool(chatModelPool);
        return strategy;
    }

    @Bean
    @Primary
    public com.tlq.rectagent.model.routing.PriorityRoutingStrategy priorityRoutingStrategy(@Qualifier("chatModelPool") ChatModelPool chatModelPool) {
        com.tlq.rectagent.model.routing.PriorityRoutingStrategy strategy = 
                new com.tlq.rectagent.model.routing.PriorityRoutingStrategy();
        strategy.setModelPool(chatModelPool);
        return strategy;
    }

    @Bean
    @Primary
    public com.tlq.rectagent.model.routing.CostRoutingStrategy costRoutingStrategy(@Qualifier("chatModelPool") ChatModelPool chatModelPool) {
        com.tlq.rectagent.model.routing.CostRoutingStrategy strategy = 
                new com.tlq.rectagent.model.routing.CostRoutingStrategy();
        strategy.setModelPool(chatModelPool);
        return strategy;
    }

    private void initProviderConfigs() {
        if (config.getProviders() == null || agentModelRouter == null) {
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

        if ("openai".equalsIgnoreCase(providerConfig.getType())) {
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
        if (config.getModels() == null || config.getProviders() == null || agentModelRouter == null) {
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
        if (config.getAgentModelMapping() != null && !config.getAgentModelMapping().isEmpty() && agentModelRouter != null) {
            agentModelRouter.setAgentModelMapping(config.getAgentModelMapping());
        }
    }

    private void initFallbackChains() {
        if (config.getModelFallbackChains() != null && !config.getModelFallbackChains().isEmpty() && agentModelRouter != null) {
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
        if (trafficShiftingRouter == null || config.getTrafficShifting() == null) {
            return;
        }

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

    @org.springframework.context.annotation.Bean
    public static com.tlq.rectagent.model.pool.ChatModelPool chatModelPool() {
        return new ChatModelPool();
    }
}
