package com.tlq.rectagent.config;

import com.tlq.rectagent.model.ModelProvider;
import com.tlq.rectagent.model.ModelRegistry;
import java.util.Map;
import java.util.Optional;

public class AgentModelSelector {
    private final ModelRegistry registry;
    private final Map<String, String> agentProviderMap;

    public AgentModelSelector(ModelRegistry registry, Map<String, String> agentProviderMap) {
        this.registry = registry;
        this.agentProviderMap = agentProviderMap;
    }

    public Optional<ModelProvider> selectForAgent(String agentType) {
        String providerName = agentProviderMap.get(agentType);
        if (providerName == null) return Optional.empty();
        return registry.get(providerName);
    }

    public ModelProvider selectForAgentOrDefault(String agentType) {
        return selectForAgent(agentType).orElse(null);
    }
}
