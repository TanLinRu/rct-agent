package com.tlq.rectagent.model.pool;

import com.tlq.rectagent.model.circuit.CircuitBreaker;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Set;

@Data
@Builder
public class ModelInstance {
    private String name;
    private String provider;
    private String model;
    private ChatModel chatModel;
    private double costPerToken;
    private int priority;
    private Set<String> capabilities;
    private boolean enabled;
    private CircuitBreaker breaker;
}
