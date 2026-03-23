package com.tlq.rectagent.model.circuit;

import com.tlq.rectagent.model.config.CircuitBreakerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private CircuitBreakerProperties props;
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        props = new CircuitBreakerProperties();
        props.setErrorRateThreshold(0.5);
        props.setSlowCallDurationMs(5000);
        props.setSlowCallRateThreshold(0.8);
        props.setMinCallCount(10);
        props.setWaitDurationInOpenMs(100);
        breaker = new CircuitBreaker("test-model", props);
    }

    @Test
    @DisplayName("初始状态应为 CLOSED")
    void shouldStartInClosedState() {
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.allowRequest());
    }

    @Test
    @DisplayName("达到最小调用数前不应转换状态")
    void shouldNotTransitionBeforeMinCallCount() {
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure(100);
        }

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    @DisplayName("达到错误率阈值后应转换为 OPEN")
    void shouldOpenAfterHighErrorRate() {
        for (int i = 0; i < 10; i++) {
            breaker.recordFailure(100);
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.allowRequest());
    }

    @Test
    @DisplayName("OPEN 状态应拒绝请求")
    void openStateShouldRejectRequests() {
        for (int i = 0; i < 10; i++) {
            breaker.recordFailure(100);
        }

        assertFalse(breaker.allowRequest());
    }

    @Test
    @DisplayName("等待恢复时间后应转换到 HALF_OPEN")
    void shouldTransitionToHalfOpenAfterWaitDuration() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            breaker.recordFailure(100);
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        Thread.sleep(150);

        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());
        assertTrue(breaker.allowRequest());
    }

    @Test
    @DisplayName("HALF_OPEN 状态连续成功应转换到 CLOSED")
    void halfOpenWithSuccessShouldClose() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            breaker.recordFailure(100);
        }

        Thread.sleep(150);

        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

        breaker.recordSuccess(100);
        breaker.recordSuccess(100);
        breaker.recordSuccess(100);

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    @DisplayName("HALF_OPEN 状态失败应转换到 OPEN")
    void halfOpenWithFailureShouldOpen() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            breaker.recordFailure(100);
        }

        Thread.sleep(150);

        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

        breaker.recordFailure(100);

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    @DisplayName("记录成功应增加计数")
    void recordSuccessShouldIncrementCount() {
        breaker.recordSuccess(100);
        breaker.recordSuccess(200);
        breaker.recordSuccess(300);

        assertEquals(3, breaker.getTotalCallCount());
        assertEquals(0, breaker.getFailureCount());
        assertEquals(0.0, breaker.getErrorRate());
    }

    @Test
    @DisplayName("记录失败应增加错误计数")
    void recordFailureShouldIncrementErrorCount() {
        breaker.recordFailure(100);
        breaker.recordFailure(200);

        assertEquals(2, breaker.getTotalCallCount());
        assertEquals(2, breaker.getFailureCount());
        assertEquals(1.0, breaker.getErrorRate());
    }

    @Test
    @DisplayName("慢调用应被记录")
    void slowCallsShouldBeRecorded() {
        breaker.recordSuccess(6000);
        breaker.recordSuccess(7000);
        breaker.recordSuccess(4000);

        assertEquals(3, breaker.getTotalCallCount());
        assertTrue(breaker.getSlowCallRate() > 0);
    }

    @Test
    @DisplayName("低错误率应保持 CLOSED")
    void lowErrorRateShouldStayClosed() {
        for (int i = 0; i < 10; i++) {
            if (i < 3) {
                breaker.recordFailure(100);
            } else {
                breaker.recordSuccess(100);
            }
        }

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }
}
