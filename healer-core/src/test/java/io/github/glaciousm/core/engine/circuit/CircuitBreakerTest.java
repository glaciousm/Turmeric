package io.github.glaciousm.core.engine.circuit;

import io.github.glaciousm.core.config.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CircuitBreaker")
class CircuitBreakerTest {

    private CircuitBreaker breaker;
    private CircuitBreakerConfig config;

    @BeforeEach
    void setUp() {
        config = new CircuitBreakerConfig();
        config.setEnabled(true);
        config.setFailureThreshold(3);
        config.setSuccessThresholdToClose(2);
        config.setOpenDurationSeconds(60);
        config.setHalfOpenMaxAttempts(2);
        breaker = new CircuitBreaker(config);
    }

    @Test
    @DisplayName("should start in CLOSED state")
    void startsInClosedState() {
        assertEquals(CircuitState.CLOSED, breaker.getState());
        assertTrue(breaker.isHealingAllowed());
    }

    @Test
    @DisplayName("should open after failure threshold is reached")
    void opensAfterFailureThreshold() {
        assertTrue(breaker.isHealingAllowed());

        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.isHealingAllowed());

        breaker.recordFailure(); // Third failure - threshold reached
        assertEquals(CircuitState.OPEN, breaker.getState());
        assertFalse(breaker.isHealingAllowed());
    }

    @Test
    @DisplayName("should reset failure count on success")
    void resetsFailureCountOnSuccess() {
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(2, breaker.getFailureCount());

        breaker.recordSuccess();
        breaker.recordSuccess();

        // After success threshold, failures should reset
        assertEquals(0, breaker.getFailureCount());
    }

    @Test
    @DisplayName("should allow manual reset")
    void allowsManualReset() {
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure();
        }
        assertEquals(CircuitState.OPEN, breaker.getState());

        // Reset
        breaker.reset();
        assertEquals(CircuitState.CLOSED, breaker.getState());
        assertEquals(0, breaker.getFailureCount());
        assertTrue(breaker.isHealingAllowed());
    }

    @Test
    @DisplayName("should allow forcing open")
    void allowsForceOpen() {
        assertEquals(CircuitState.CLOSED, breaker.getState());

        breaker.forceOpen();

        assertEquals(CircuitState.OPEN, breaker.getState());
        assertFalse(breaker.isHealingAllowed());
    }

    @Test
    @DisplayName("refusals should not affect circuit state")
    void refusalsDoNotAffectState() {
        breaker.recordFailure();
        breaker.recordFailure();

        breaker.recordRefusal();
        breaker.recordRefusal();

        // Still at 2 failures, not opened
        assertEquals(CircuitState.CLOSED, breaker.getState());
        assertEquals(2, breaker.getFailureCount());
    }

    @Test
    @DisplayName("should provide statistics")
    void providesStatistics() {
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();

        CircuitBreaker.CircuitStats stats = breaker.getStats();

        assertEquals(CircuitState.CLOSED, stats.state());
        assertEquals(2, stats.failureCount());
        assertEquals(0, stats.successCount()); // Reset after failure
    }

    @Test
    @DisplayName("disabled breaker always allows healing")
    void disabledBreakerAllowsAll() {
        config.setEnabled(false);
        CircuitBreaker disabledBreaker = new CircuitBreaker(config);

        // Record many failures
        for (int i = 0; i < 10; i++) {
            disabledBreaker.recordFailure();
        }

        // Still allowed because disabled
        assertTrue(disabledBreaker.isHealingAllowed());
    }
}
