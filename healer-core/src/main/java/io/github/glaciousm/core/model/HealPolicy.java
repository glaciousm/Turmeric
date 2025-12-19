package io.github.glaciousm.core.model;

/**
 * Defines the healing policy for a test step or scenario.
 */
public enum HealPolicy {
    /**
     * No healing attempted. Test will fail normally on element not found.
     */
    OFF,

    /**
     * Find candidates and report them, but still fail the test.
     * Useful for building trust and review periods.
     */
    SUGGEST,

    /**
     * Automatically heal non-destructive actions.
     * This is the default mode for most tests.
     */
    AUTO_SAFE,

    /**
     * Require human confirmation before applying any heal.
     * Useful when building trust or for critical test paths.
     */
    CONFIRM,

    /**
     * Heal all allowed actions including potentially risky ones.
     * Requires explicit opt-in.
     */
    AUTO_ALL
}
