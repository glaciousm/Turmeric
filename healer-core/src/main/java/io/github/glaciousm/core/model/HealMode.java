package io.github.glaciousm.core.model;

/**
 * Defines the operational mode for healing.
 * This is similar to HealPolicy but represents runtime mode rather than configuration.
 */
public enum HealMode {
    /**
     * Healing is completely disabled.
     */
    OFF,

    /**
     * Only report potential heals without applying them.
     */
    REPORT_ONLY,

    /**
     * Apply heals automatically for safe actions.
     */
    AUTO_SAFE,

    /**
     * Require confirmation before applying any heal.
     */
    CONFIRM,

    /**
     * Apply all heals automatically (use with caution).
     */
    AUTO_ALL
}
