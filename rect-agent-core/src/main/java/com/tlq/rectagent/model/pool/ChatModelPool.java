package com.tlq.rectagent.model.pool;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.tlq.rectagent.config.MockChatModel;
import com.tlq.rectagent.model.config.CircuitBreakerProperties;
import com.tlq.rectagent.model.config.ModelInstanceConfig;
import com.tlq.rectagent.model.config.ProviderConfig;
import com.tlq.rectagent.model.circuit.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatModelPool {

    @Autowired(required = false)
    private CircuitBreakerProperties breakerProps;

    private final Map<String, ModelInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, ProviderConfig> providerConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("ChatModelPool initialized");
    }

    public void registerModel(ModelInstanceConfig config, ProviderConfig providerConfig) {
        if (config == null || providerConfig == null) {
            throw new IllegalArgumentException("config and providerConfig cannot be null");
        }

        ChatModel chatModel = createChatModel(config, providerConfig);

        CircuitBreaker breaker = breakerProps != null
                ? new CircuitBreaker(config.getName(), breakerProps)
                : new CircuitBreaker(config.getName(), new CircuitBreakerProperties());

        ModelInstance instance = ModelInstance.builder()
                .name(config.getName())
                .provider(config.getProvider())
                .model(config.getModel())
                .chatModel(chatModel)
                .costPerToken(config.getCostPerToken())
                .priority(config.getPriority())
                .capabilities(config.getCapabilities())
                .enabled(true)
                .breaker(breaker)
                .build();

        instances.put(config.getName(), instance);
        providerConfigs.put(config.getProvider(), providerConfig);

        log.info("Registered model: {} -> {}/{}", config.getName(), config.getProvider(), config.getModel());
    }

    public void registerProvider(String providerType, ProviderConfig config) {
        providerConfigs.put(providerType, config);
    }

    public Optional<ModelInstance> get(String name) {
        return Optional.ofNullable(instances.get(name));
    }

    public List<ModelInstance> getByCapability(String capability) {
        return instances.values().stream()
                .filter(ModelInstance::isEnabled)
                .filter(m -> m.getCapabilities() != null && m.getCapabilities().contains(capability))
                .filter(m -> m.getBreaker().allowRequest())
                .toList();
    }

    public List<ModelInstance> getByCapabilities(Set<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return getAllAvailable();
        }
        return instances.values().stream()
                .filter(ModelInstance::isEnabled)
                .filter(m -> m.getCapabilities() != null && m.getCapabilities().containsAll(capabilities))
                .filter(m -> m.getBreaker().allowRequest())
                .toList();
    }

    public List<ModelInstance> getAllAvailable() {
        return instances.values().stream()
                .filter(ModelInstance::isEnabled)
                .filter(m -> m.getBreaker().allowRequest())
                .toList();
    }

    public List<ModelInstance> getAll() {
        return new ArrayList<>(instances.values());
    }

    public Set<String> getModelNames() {
        return instances.keySet();
    }

    public boolean hasModel(String name) {
        return instances.containsKey(name);
    }

    private ChatModel createChatModel(ModelInstanceConfig config, ProviderConfig providerConfig) {
        String provider = config.getProvider();

        if ("dashscope".equals(provider)) {
            return createDashScopeChatModel(providerConfig, config.getModel());
        }

        log.warn("Provider '{}' not supported yet, using mock model", provider);
        return new MockChatModel();
    }

    private ChatModel createDashScopeChatModel(ProviderConfig config, String model) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("DashScope API key not configured, using mock model");
            return new MockChatModel();
        }

        DashScopeApi api = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .build();
    }
}
