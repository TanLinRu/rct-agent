package com.tlq.rectagent.model.router;

import com.tlq.rectagent.model.config.ModelInstanceConfig;
import com.tlq.rectagent.model.config.ProviderConfig;
import com.tlq.rectagent.model.config.TrafficShiftingRule;
import com.tlq.rectagent.model.circuit.CircuitBreaker;
import com.tlq.rectagent.model.pool.ChatModelPool;
import com.tlq.rectagent.model.pool.ModelInstance;
import com.tlq.rectagent.model.routing.CapabilityRoutingStrategy;
import com.tlq.rectagent.model.routing.CostRoutingStrategy;
import com.tlq.rectagent.model.routing.PriorityRoutingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AgentModelRouter {

    private ChatModelPool modelPool;
    private CostRoutingStrategy costStrategy;
    private CapabilityRoutingStrategy capabilityStrategy;
    private PriorityRoutingStrategy priorityStrategy;
    private TrafficShiftingRouter trafficShiftingRouter;

    private Map<String, String> agentModelMapping = new HashMap<>();
    private Map<String, List<String>> modelFallbackChains = new HashMap<>();
    private String defaultStrategy = CostRoutingStrategy.NAME;

    private Map<String, ModelInstanceConfig> modelConfigs = new HashMap<>();
    private Map<String, ProviderConfig> providerConfigs = new HashMap<>();

    public void setModelPool(ChatModelPool modelPool) {
        this.modelPool = modelPool;
    }

    public void setCostStrategy(CostRoutingStrategy costStrategy) {
        this.costStrategy = costStrategy;
    }

    public void setCapabilityStrategy(CapabilityRoutingStrategy capabilityStrategy) {
        this.capabilityStrategy = capabilityStrategy;
    }

    public void setPriorityStrategy(PriorityRoutingStrategy priorityStrategy) {
        this.priorityStrategy = priorityStrategy;
    }

    public void setTrafficShiftingRouter(TrafficShiftingRouter trafficShiftingRouter) {
        this.trafficShiftingRouter = trafficShiftingRouter;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("AgentModelRouter initialized with default strategy: {}", defaultStrategy);
    }

    public void setAgentModelMapping(Map<String, String> mapping) {
        if (mapping != null) {
            this.agentModelMapping = new ConcurrentHashMap<>(mapping);
            log.info("Agent model mapping updated: {}", mapping);
        }
    }

    public void setModelFallbackChains(Map<String, List<String>> chains) {
        if (chains != null) {
            this.modelFallbackChains = new ConcurrentHashMap<>(chains);
            log.info("Model fallback chains updated: {}", chains);
        }
    }

    public void setDefaultStrategy(String strategy) {
        if (strategy != null) {
            this.defaultStrategy = strategy;
            log.info("Default routing strategy changed to: {}", strategy);
        }
    }

    public void registerModelInstance(String name, ModelInstanceConfig config, ProviderConfig providerConfig) {
        modelConfigs.put(name, config);
        if (providerConfig != null) {
            providerConfigs.put(config.getProvider(), providerConfig);
        }
        modelPool.registerModel(config, providerConfig);
    }

    public void registerProvider(String providerType, ProviderConfig config) {
        providerConfigs.put(providerType, config);
        modelPool.registerProvider(providerType, config);
    }

    public ChatModel getChatModel(String agentName) {
        return getChatModel(agentName, Collections.emptySet());
    }

    public ChatModel getChatModel(String agentName, Set<String> requiredCapabilities) {
        String traceId = org.slf4j.MDC.get("traceId");

        ChatModel result = doRoute(agentName, requiredCapabilities, traceId);

        if (result == null) {
            log.error("[{}] Failed to route ChatModel for agent: {}", traceId, agentName);
        }

        return result;
    }

    private ChatModel doRoute(String agentName, Set<String> requiredCapabilities, String traceId) {
        if (trafficShiftingRouter != null && trafficShiftingRouter.isEnabled()) {
            ChatModel trafficModel = trafficShiftingRouter.getChatModel(agentName);
            if (trafficModel != null) {
                return trafficModel;
            }
        }

        String modelName = agentModelMapping.get(agentName);
        if (modelName != null) {
            Optional<ModelInstance> instance = modelPool.get(modelName);
            if (instance.isPresent() && instance.get().getBreaker().allowRequest()) {
                log.debug("[{}] {} -> {} (static mapping)", traceId, agentName, modelName);
                return instance.get().getChatModel();
            }

            ChatModel fallback = tryModelFallback(modelName, traceId);
            if (fallback != null) {
                return fallback;
            }
        }

        return selectByStrategy(requiredCapabilities, traceId);
    }

    private ChatModel tryModelFallback(String failedModel, String traceId) {
        List<String> chain = modelFallbackChains.get(failedModel);
        if (chain == null || chain.isEmpty()) {
            log.debug("[{}] No fallback chain for model: {}", traceId, failedModel);
            return null;
        }

        for (String candidate : chain) {
            Optional<ModelInstance> instance = modelPool.get(candidate);
            if (instance.isPresent() && instance.get().getBreaker().allowRequest()) {
                log.warn("[{}] Fallback: {} -> {}", traceId, failedModel, candidate);
                return instance.get().getChatModel();
            }
        }

        log.warn("[{}] All fallback models unavailable for: {}", traceId, failedModel);
        return null;
    }

    private ChatModel selectByStrategy(Set<String> capabilities, String traceId) {
        log.debug("[{}] Using strategy routing: {}", traceId, defaultStrategy);

        return switch (defaultStrategy.toLowerCase()) {
            case CapabilityRoutingStrategy.NAME -> capabilityStrategy.select(capabilities);
            case PriorityRoutingStrategy.NAME -> priorityStrategy.select();
            default -> costStrategy.select();
        };
    }

    public Map<String, CircuitBreaker.State> getCircuitBreakerStates() {
        if (modelPool == null) {
            return Collections.emptyMap();
        }
        Map<String, CircuitBreaker.State> states = new HashMap<>();
        for (String name : modelPool.getModelNames()) {
            Optional<ModelInstance> instance = modelPool.get(name);
            instance.ifPresent(i -> states.put(name, i.getBreaker().getState()));
        }
        return states;
    }

    public List<ModelInstance> getAvailableModels() {
        if (modelPool == null) {
            return Collections.emptyList();
        }
        return modelPool.getAllAvailable();
    }

    public Map<String, String> getAgentModelMapping() {
        return Collections.unmodifiableMap(agentModelMapping);
    }

    public String getDefaultStrategy() {
        return defaultStrategy;
    }
}
