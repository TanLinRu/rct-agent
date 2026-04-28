package com.tlq.rectagent.model.circuit;

import com.tlq.rectagent.model.pool.ChatModelPool;
import com.tlq.rectagent.model.pool.ModelInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class CircuitBreakerManager {

    private ChatModelPool modelPool;

    public void setModelPool(ChatModelPool modelPool) {
        this.modelPool = modelPool;
    }

    public CircuitBreaker getBreaker(String modelName) {
        Optional<ModelInstance> instance = modelPool.get(modelName);
        return instance.map(ModelInstance::getBreaker).orElse(null);
    }

    public boolean isAvailable(String modelName) {
        CircuitBreaker breaker = getBreaker(modelName);
        return breaker != null && breaker.allowRequest();
    }

    public void recordSuccess(String modelName, long durationMs) {
        CircuitBreaker breaker = getBreaker(modelName);
        if (breaker != null) {
            breaker.recordSuccess(durationMs);
            log.debug("[CircuitBreaker] {} success: duration={}ms, errorRate={}",
                    modelName, durationMs, breaker.getErrorRate());
        }
    }

    public void recordFailure(String modelName, long durationMs) {
        CircuitBreaker breaker = getBreaker(modelName);
        if (breaker != null) {
            breaker.recordFailure(durationMs);
            log.debug("[CircuitBreaker] {} failure: duration={}ms, errorRate={}",
                    modelName, durationMs, breaker.getErrorRate());
        }
    }

    public void recordSlowCall(String modelName) {
        CircuitBreaker breaker = getBreaker(modelName);
        if (breaker != null) {
            breaker.recordSlowCall();
            log.debug("[CircuitBreaker] {} slow call recorded, slowRate={}",
                    modelName, breaker.getSlowCallRate());
        }
    }

    public Map<String, CircuitBreaker.State> getAllStates() {
        Map<String, CircuitBreaker.State> states = new HashMap<>();
        for (String name : modelPool.getModelNames()) {
            CircuitBreaker breaker = getBreaker(name);
            if (breaker != null) {
                states.put(name, breaker.getState());
            }
        }
        return states;
    }

    public Map<String, Object> getBreakerStats(String modelName) {
        CircuitBreaker breaker = getBreaker(modelName);
        if (breaker == null) {
            return Map.of();
        }
        return Map.of(
                "modelName", breaker.getModelName(),
                "state", breaker.getState(),
                "failureCount", breaker.getFailureCount(),
                "totalCallCount", breaker.getTotalCallCount(),
                "errorRate", breaker.getErrorRate(),
                "slowCallRate", breaker.getSlowCallRate()
        );
    }
}
