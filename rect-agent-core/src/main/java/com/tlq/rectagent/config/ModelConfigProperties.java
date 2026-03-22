package com.tlq.rectagent.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rectagent.model")
public class ModelConfigProperties {
    private String defaultProvider;
    private String routingStrategy;
    private List<ProviderConfig> providers;

    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }
    public List<ProviderConfig> getProviders() { return providers; }
    public void setProviders(List<ProviderConfig> providers) { this.providers = providers; }
    public String getRoutingStrategy() { return routingStrategy; }
    public void setRoutingStrategy(String routingStrategy) { this.routingStrategy = routingStrategy; }

    public static class ProviderConfig {
        private String name;
        private boolean enabled;
        private String type;
        private String apiKey;
        private String model;
        private double costPerToken;
        private int priority;
        private boolean mock;
        private String endpoint;
        private java.util.List<String> endpoints;
        private String capability;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getCostPerToken() { return costPerToken; }
        public void setCostPerToken(double costPerToken) { this.costPerToken = costPerToken; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public boolean isMock() { return mock; }
        public void setMock(boolean mock) { this.mock = mock; }
        public java.util.List<String> getEndpoints() { return endpoints; }
        public void setEndpoints(java.util.List<String> endpoints) { this.endpoints = endpoints; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getCapability() { return capability; }
        public void setCapability(String capability) { this.capability = capability; }
    }
}
