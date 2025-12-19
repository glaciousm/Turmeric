package io.github.glaciousm.core.exception;

/**
 * Base exception for healing-related errors.
 */
public class HealingException extends RuntimeException {

    private final HealingFailureReason reason;

    public HealingException(String message) {
        super(message);
        this.reason = HealingFailureReason.UNKNOWN;
    }

    public HealingException(String message, HealingFailureReason reason) {
        super(message);
        this.reason = reason;
    }

    public HealingException(String message, Throwable cause) {
        super(message, cause);
        this.reason = HealingFailureReason.UNKNOWN;
    }

    public HealingException(String message, Throwable cause, HealingFailureReason reason) {
        super(message, cause);
        this.reason = reason;
    }

    public HealingFailureReason getReason() {
        return reason;
    }

    /**
     * Reasons for healing failure.
     */
    public enum HealingFailureReason {
        /**
         * LLM provider is unavailable or returned an error.
         */
        LLM_UNAVAILABLE,

        /**
         * LLM returned an invalid or unparseable response.
         */
        LLM_INVALID_RESPONSE,

        /**
         * Guardrail blocked the healing attempt.
         */
        GUARDRAIL_VIOLATION,

        /**
         * No candidate elements found on the page.
         */
        NO_CANDIDATES_FOUND,

        /**
         * LLM confidence was below threshold.
         */
        LOW_CONFIDENCE,

        /**
         * Outcome check failed after healed action.
         */
        OUTCOME_CHECK_FAILED,

        /**
         * Invariant was violated after healed action.
         */
        INVARIANT_VIOLATED,

        /**
         * Action execution failed.
         */
        ACTION_EXECUTION_FAILED,

        /**
         * Could not re-find element from snapshot.
         */
        ELEMENT_NOT_REFINDABLE,

        /**
         * Configuration error.
         */
        CONFIGURATION_ERROR,

        /**
         * Circuit breaker is open.
         */
        CIRCUIT_BREAKER_OPEN,

        /**
         * Cost limit exceeded.
         */
        COST_LIMIT_EXCEEDED,

        /**
         * Unknown or unclassified error.
         */
        UNKNOWN
    }
}
