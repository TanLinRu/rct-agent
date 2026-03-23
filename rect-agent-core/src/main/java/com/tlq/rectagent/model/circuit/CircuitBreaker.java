package com.tlq.rectagent.model.circuit;

import com.tlq.rectagent.model.config.CircuitBreakerProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String modelName;
    private final CircuitBreakerProperties props;

    private State state = State.CLOSED;
    private long failureCount = 0;
    private long successCount = 0;
    private long slowCallCount = 0;
    private long totalCallCount = 0;
    private long lastStateChangeTime = 0;

    public CircuitBreaker(String modelName, CircuitBreakerProperties props) {
        this.modelName = modelName;
        this.props = props;
        this.lastStateChangeTime = System.currentTimeMillis();
    }

    public String getModelName() {
        return modelName;
    }

    public State getState() {
        if (state == State.OPEN && shouldTransitionToHalfOpen()) {
            transitionTo(State.HALF_OPEN);
        }
        return state;
    }

    public boolean allowRequest() {
        return getState() != State.OPEN;
    }

    public void recordSuccess(long durationMs) {
        totalCallCount++;

        if (durationMs > props.getSlowCallDurationMs()) {
            slowCallCount++;
        }

        if (state == State.HALF_OPEN) {
            successCount++;
            if (successCount >= 3) {
                transitionTo(State.CLOSED);
            }
        }
    }

    public void recordFailure(long durationMs) {
        totalCallCount++;
        failureCount++;

        if (state == State.HALF_OPEN) {
            transitionTo(State.OPEN);
            return;
        }

        if (shouldTransitionToOpen()) {
            transitionTo(State.OPEN);
        }
    }

    public void recordSlowCall() {
        slowCallCount++;
    }

    private boolean shouldTransitionToOpen() {
        if (totalCallCount < props.getMinCallCount()) {
            return false;
        }

        double errorRate = (double) failureCount / totalCallCount;
        double slowRate = (double) slowCallCount / totalCallCount;

        return errorRate >= props.getErrorRateThreshold()
                || slowRate >= props.getSlowCallRateThreshold();
    }

    private boolean shouldTransitionToHalfOpen() {
        return System.currentTimeMillis() - lastStateChangeTime >= props.getWaitDurationInOpenMs();
    }

    private void transitionTo(State newState) {
        if (state == newState) return;

        State oldState = state;
        state = newState;
        lastStateChangeTime = System.currentTimeMillis();

        log.warn("[CircuitBreaker] Model {} transitioned: {} -> {}", modelName, oldState, newState);

        if (newState == State.CLOSED) {
            reset();
        }
    }

    private void reset() {
        failureCount = 0;
        successCount = 0;
        slowCallCount = 0;
        totalCallCount = 0;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public long getTotalCallCount() {
        return totalCallCount;
    }

    public double getErrorRate() {
        return totalCallCount > 0 ? (double) failureCount / totalCallCount : 0.0;
    }

    public double getSlowCallRate() {
        return totalCallCount > 0 ? (double) slowCallCount / totalCallCount : 0.0;
    }
}
