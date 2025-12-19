package io.github.glaciousm.core.engine.approval;

/**
 * Status of a heal proposal approval.
 */
public enum ApprovalStatus {
    /**
     * Awaiting review.
     */
    PENDING,

    /**
     * Approved and can be applied.
     */
    APPROVED,

    /**
     * Rejected and should not be applied.
     */
    REJECTED,

    /**
     * More information needed before decision.
     */
    NEEDS_INFO,

    /**
     * Automatically applied (for auto-heal modes).
     */
    AUTO_APPLIED,

    /**
     * Expired without review.
     */
    EXPIRED
}
