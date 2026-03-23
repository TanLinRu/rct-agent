package com.tlq.rectagent.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rectagent.model.circuit-breaker")
public class CircuitBreakerProperties {
    private boolean enabled = true;
    private double errorRateThreshold = 0.5;
    private long slowCallDurationMs = 5000;
    private double slowCallRateThreshold = 0.8;
    private int minCallCount = 10;
    private long waitDurationInOpenMs = 30000;
}
