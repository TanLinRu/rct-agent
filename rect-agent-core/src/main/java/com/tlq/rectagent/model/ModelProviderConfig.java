package com.tlq.rectagent.model;

import java.util.List;

public class ModelProviderConfig {
    private String name;
    private boolean enabled;
    private String type; // e.g. openai, anthropic
    private String apiKey;
    private String model;
    private double costPerToken;
    private int priority;
    private boolean mock;
    private String endpoint;
    private java.util.List<String> endpoints;
    private int maxRetries = 0;
    private long retryDelayMs = 0;
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
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public java.util.List<String> getEndpoints() { return endpoints; }
    public void setEndpoints(java.util.List<String> endpoints) { this.endpoints = endpoints; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    public String getCapability() { return capability; }
    public void setCapability(String capability) { this.capability = capability; }
}
