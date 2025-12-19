package io.github.glaciousm.core.engine.circuit;

/**
 * Circuit breaker states.
 */
public enum CircuitState {
    /**
     * Circuit is closed - healing is allowed.
     */
    CLOSED,

    /**
     * Circuit is open - healing is disabled due to too many failures.
     */
    OPEN,

    /**
     * Circuit is half-open - allowing test heals to check if system has recovered.
     */
    HALF_OPEN
}
