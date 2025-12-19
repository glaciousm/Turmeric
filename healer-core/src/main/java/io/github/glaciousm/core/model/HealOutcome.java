package io.github.glaciousm.core.model;

/**
 * Outcome of a heal attempt.
 */
public enum HealOutcome {
    /**
     * Heal was successful - action executed and outcome validated.
     */
    SUCCESS,

    /**
     * Heal was refused by guardrails or LLM.
     */
    REFUSED,

    /**
     * Heal was attempted but failed during execution.
     */
    FAILED,

    /**
     * Heal was successful but outcome validation failed.
     */
    OUTCOME_FAILED,

    /**
     * Heal was successful but an invariant was violated.
     */
    INVARIANT_VIOLATED,

    /**
     * Heal was suggested but not applied (SUGGEST mode).
     */
    SUGGESTED
}
