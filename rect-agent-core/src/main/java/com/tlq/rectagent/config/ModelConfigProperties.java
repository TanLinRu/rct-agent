package com.tlq.rectagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

@Data
@ConfigurationProperties(prefix = "rectagent.model")
public class ModelConfigProperties {
    private String defaultModel;
    private String routingStrategy = "cost";
    private Map<String, ProviderConfig> providers;
    private Map<String, ModelInstanceConfig> models;
    private Map<String, String> agentModelMapping;
    private Map<String, String> modelFallbackChains;
    private CircuitBreakerConfig circuitBreaker;
    private TrafficShiftingConfig trafficShifting;

    @Data
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
        private List<String> endpoints;
        private String capability;
        private String baseUrl;
        private int timeout = 30000;
        private int maxRetries = 2;
    }

    @Data
    public static class ModelInstanceConfig {
        private String name;
        private String provider;
        private String model;
        private double costPerToken;
        private int priority;
        private Set<String> capabilities;
    }

    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private double errorRateThreshold = 0.5;
        private long slowCallDurationMs = 5000;
        private double slowCallRateThreshold = 0.8;
        private int minCallCount = 10;
        private long waitDurationInOpenMs = 30000;
    }

    @Data
    public static class TrafficShiftingConfig {
        private boolean enabled;
        private List<TrafficShiftingRule> rules;
    }

    @Data
    public static class TrafficShiftingRule {
        private String agent;
        private String model;
        private int percentage;
    }
}
