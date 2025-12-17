package com.intenthealer.core.engine.circuit;

import com.intenthealer.core.config.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for circuit breaker cost limiting and half-open state behavior.
 */
@DisplayName("CircuitBreaker Cost Limits and Half-Open State")
class CircuitBreakerCostLimitTest {

    private CircuitBreaker breaker;
    private CircuitBreakerConfig config;

    @BeforeEach
    void setUp() {
        config = new CircuitBreakerConfig();
        config.setEnabled(true);
        config.setFailureThreshold(3);
        config.setSuccessThresholdToClose(2);
        config.setOpenDurationSeconds(5); // Short for testing
        config.setHalfOpenMaxAttempts(2);
        config.setDailyCostLimitUsd(10.0);
        breaker = new CircuitBreaker(config);
    }

    @Nested
    @DisplayName("Cost Limit Tests")
    class CostLimitTests {

        @Test
        @DisplayName("should open circuit when daily cost limit exceeded")
        void opensWhenCostLimitExceeded() {
            assertTrue(breaker.isHealingAllowed());
            assertEquals(CircuitState.CLOSED, breaker.getState());

            // Add costs approaching limit
            breaker.recordSuccess(3.0);
            breaker.recordSuccess(3.0);
            assertTrue(breaker.isHealingAllowed());
            assertEquals(6.0, breaker.getDailyCost(), 0.001);

            // Add cost that exceeds limit
            breaker.recordSuccess(5.0);
            assertEquals(11.0, breaker.getDailyCost(), 0.001);

            // Now should be blocked
            assertFalse(breaker.isHealingAllowed());
            assertEquals(CircuitState.OPEN, breaker.getState());
            assertTrue(breaker.isOpenedDueToCost());
        }

        @Test
        @DisplayName("should track cost from both successes and failures")
        void tracksCostFromBothOutcomes() {
            breaker.recordSuccess(2.0);
            breaker.recordFailure(3.0);
            breaker.recordSuccess(1.5);

            assertEquals(6.5, breaker.getDailyCost(), 0.001);
        }

        @Test
        @DisplayName("should not count negative costs")
        void ignoresNegativeCosts() {
            breaker.recordSuccess(5.0);
            breaker.addCost(-2.0);

            assertEquals(5.0, breaker.getDailyCost(), 0.001);
        }

        @Test
        @DisplayName("should allow resetting daily cost")
        void allowsDailyCostReset() {
            breaker.recordSuccess(8.0);
            assertEquals(8.0, breaker.getDailyCost(), 0.001);

            breaker.resetDailyCost();
            assertEquals(0.0, breaker.getDailyCost(), 0.001);
        }

        @Test
        @DisplayName("should report cost limit in stats")
        void reportsStatsWithCost() {
            breaker.recordSuccess(5.0);

            CircuitBreaker.CircuitStats stats = breaker.getStats();
            assertEquals(5.0, stats.dailyCost(), 0.001);
            assertEquals(10.0, stats.dailyCostLimit(), 0.001);
            assertFalse(stats.openedDueToCost());
        }

        @Test
        @DisplayName("should not open on cost if cost limit is disabled (zero)")
        void doesNotOpenWhenCostLimitDisabled() {
            config.setDailyCostLimitUsd(0.0);
            CircuitBreaker unlimitedBreaker = new CircuitBreaker(config);

            unlimitedBreaker.recordSuccess(100.0);
            unlimitedBreaker.recordSuccess(100.0);

            assertTrue(unlimitedBreaker.isHealingAllowed());
            assertEquals(CircuitState.CLOSED, unlimitedBreaker.getState());
        }

        @Test
        @DisplayName("should distinguish cost-based open from failure-based open")
        void distinguishesCostOpenFromFailureOpen() {
            // Open due to failures
            for (int i = 0; i < 3; i++) {
                breaker.recordFailure();
            }
            assertEquals(CircuitState.OPEN, breaker.getState());
            assertFalse(breaker.isOpenedDueToCost());

            // Reset and open due to cost
            breaker.reset();
            breaker.recordSuccess(15.0);
            assertFalse(breaker.isHealingAllowed());
            assertTrue(breaker.isOpenedDueToCost());
        }
    }

    @Nested
    @DisplayName("Half-Open State Tests")
    class HalfOpenStateTests {

        @Test
        @DisplayName("should transition to half-open after cooldown")
        void transitionsToHalfOpenAfterCooldown() throws InterruptedException {
            config.setOpenDurationSeconds(1); // 1 second for fast testing
            CircuitBreaker fastBreaker = new CircuitBreaker(config);

            // Open the circuit
            for (int i = 0; i < 3; i++) {
                fastBreaker.recordFailure();
            }
            assertEquals(CircuitState.OPEN, fastBreaker.getState());

            // Wait for cooldown
            Thread.sleep(1100);

            // Check state - should transition to half-open
            assertEquals(CircuitState.HALF_OPEN, fastBreaker.getState());
        }

        @Test
        @DisplayName("should allow limited attempts in half-open state")
        void limitsAttemptsInHalfOpen() throws InterruptedException {
            config.setOpenDurationSeconds(1);
            config.setHalfOpenMaxAttempts(2);
            CircuitBreaker fastBreaker = new CircuitBreaker(config);

            // Open the circuit
            for (int i = 0; i < 3; i++) {
                fastBreaker.recordFailure();
            }

            // Wait for half-open
            Thread.sleep(1100);

            // First two attempts should be allowed
            assertTrue(fastBreaker.isHealingAllowed());
            assertTrue(fastBreaker.isHealingAllowed());

            // Third should not be allowed
            assertFalse(fastBreaker.isHealingAllowed());
        }

        @Test
        @DisplayName("should close circuit after successes in half-open state")
        void closesAfterSuccessesInHalfOpen() throws InterruptedException {
            config.setOpenDurationSeconds(1);
            config.setSuccessThresholdToClose(2);
            CircuitBreaker fastBreaker = new CircuitBreaker(config);

            // Open the circuit
            for (int i = 0; i < 3; i++) {
                fastBreaker.recordFailure();
            }

            // Wait for half-open
            Thread.sleep(1100);
            assertEquals(CircuitState.HALF_OPEN, fastBreaker.getState());

            // Record successes
            fastBreaker.recordSuccess();
            assertEquals(CircuitState.HALF_OPEN, fastBreaker.getState());

            fastBreaker.recordSuccess();
            assertEquals(CircuitState.CLOSED, fastBreaker.getState());
        }

        @Test
        @DisplayName("should reopen circuit on failure in half-open state")
        void reopensOnFailureInHalfOpen() throws InterruptedException {
            config.setOpenDurationSeconds(1);
            CircuitBreaker fastBreaker = new CircuitBreaker(config);

            // Open the circuit
            for (int i = 0; i < 3; i++) {
                fastBreaker.recordFailure();
            }

            // Wait for half-open
            Thread.sleep(1100);
            assertEquals(CircuitState.HALF_OPEN, fastBreaker.getState());

            // One failure should reopen
            fastBreaker.recordFailure();
            assertEquals(CircuitState.OPEN, fastBreaker.getState());
        }

        @Test
        @DisplayName("should not transition to half-open if still within cooldown")
        void staysOpenDuringCooldown() {
            config.setOpenDurationSeconds(60);
            CircuitBreaker slowBreaker = new CircuitBreaker(config);

            // Open the circuit
            for (int i = 0; i < 3; i++) {
                slowBreaker.recordFailure();
            }
            assertEquals(CircuitState.OPEN, slowBreaker.getState());

            // Check immediately - should still be open
            assertFalse(slowBreaker.isHealingAllowed());
            assertEquals(CircuitState.OPEN, slowBreaker.getState());
        }
    }

    @Nested
    @DisplayName("Recovery Tests")
    class RecoveryTests {

        @Test
        @DisplayName("should fully recover from cost-based open after reset")
        void recoversFromCostOpen() {
            // Open due to cost
            breaker.recordSuccess(15.0);
            assertFalse(breaker.isHealingAllowed());
            assertTrue(breaker.isOpenedDueToCost());

            // Reset
            breaker.reset();
            breaker.resetDailyCost();

            // Should be fully recovered
            assertTrue(breaker.isHealingAllowed());
            assertEquals(CircuitState.CLOSED, breaker.getState());
            assertEquals(0.0, breaker.getDailyCost(), 0.001);
            assertFalse(breaker.isOpenedDueToCost());
        }

        @Test
        @DisplayName("should recover from failures after success threshold")
        void recoversFromFailuresAfterSuccesses() {
            // Record some failures (but not enough to open)
            breaker.recordFailure();
            breaker.recordFailure();
            assertEquals(2, breaker.getFailureCount());

            // Record successes to reset
            breaker.recordSuccess();
            breaker.recordSuccess();

            assertEquals(0, breaker.getFailureCount());
        }

        @Test
        @DisplayName("should provide time until close information")
        void providesTimeUntilClose() {
            config.setOpenDurationSeconds(60);
            CircuitBreaker timedBreaker = new CircuitBreaker(config);

            // Open the circuit
            for (int i = 0; i < 3; i++) {
                timedBreaker.recordFailure();
            }

            // Check time until close
            var timeUntilClose = timedBreaker.getTimeUntilClose();
            assertTrue(timeUntilClose.getSeconds() > 0);
            assertTrue(timeUntilClose.getSeconds() <= 60);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle exact cost limit correctly")
        void handlesExactCostLimit() {
            breaker.recordSuccess(10.0);
            assertEquals(10.0, breaker.getDailyCost(), 0.001);

            // At exact limit, should trigger
            assertFalse(breaker.isHealingAllowed());
            assertEquals(CircuitState.OPEN, breaker.getState());
        }

        @Test
        @DisplayName("should handle multiple cost additions atomically")
        void handlesConcurrentCostAdditions() throws InterruptedException {
            final int threads = 10;
            final double costPerThread = 0.5;

            Thread[] addingThreads = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                addingThreads[i] = new Thread(() -> {
                    for (int j = 0; j < 10; j++) {
                        breaker.addCost(costPerThread);
                    }
                });
            }

            for (Thread t : addingThreads) t.start();
            for (Thread t : addingThreads) t.join();

            // Should have all costs added (10 threads * 10 ops * 0.5 = 50)
            assertEquals(50.0, breaker.getDailyCost(), 0.001);
        }

        @Test
        @DisplayName("should handle default constructor")
        void handlesDefaultConstructor() {
            CircuitBreaker defaultBreaker = new CircuitBreaker();
            assertTrue(defaultBreaker.isHealingAllowed());
            assertEquals(CircuitState.CLOSED, defaultBreaker.getState());
        }

        @Test
        @DisplayName("should handle null config")
        void handlesNullConfig() {
            CircuitBreaker nullConfigBreaker = new CircuitBreaker(null);
            assertTrue(nullConfigBreaker.isHealingAllowed());
        }
    }
}
