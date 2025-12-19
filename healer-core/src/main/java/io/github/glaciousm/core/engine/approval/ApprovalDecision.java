package io.github.glaciousm.core.engine.approval;

/**
 * The decision made on a heal proposal.
 */
public class ApprovalDecision {

    private final boolean approved;
    private final String reason;
    private final boolean applyToFuture;
    private final boolean addToBlacklist;

    private ApprovalDecision(boolean approved, String reason, boolean applyToFuture, boolean addToBlacklist) {
        this.approved = approved;
        this.reason = reason;
        this.applyToFuture = applyToFuture;
        this.addToBlacklist = addToBlacklist;
    }

    /**
     * Create an approval decision.
     */
    public static ApprovalDecision approve() {
        return new ApprovalDecision(true, null, false, false);
    }

    /**
     * Create an approval with notes.
     */
    public static ApprovalDecision approve(String notes) {
        return new ApprovalDecision(true, notes, false, false);
    }

    /**
     * Approve and also apply to future occurrences.
     */
    public static ApprovalDecision approveAndRemember() {
        return new ApprovalDecision(true, null, true, false);
    }

    /**
     * Create a rejection decision.
     */
    public static ApprovalDecision reject(String reason) {
        return new ApprovalDecision(false, reason, false, false);
    }

    /**
     * Reject and add to blacklist to prevent future attempts.
     */
    public static ApprovalDecision rejectAndBlacklist(String reason) {
        return new ApprovalDecision(false, reason, false, true);
    }

    /**
     * Skip this proposal (don't apply, but don't reject either).
     */
    public static ApprovalDecision skip() {
        return new ApprovalDecision(false, "Skipped by reviewer", false, false);
    }

    /**
     * Timeout - no decision made in time.
     */
    public static ApprovalDecision timeout() {
        return new ApprovalDecision(false, "Approval timed out", false, false);
    }

    public boolean isApproved() {
        return approved;
    }

    public String getReason() {
        return reason;
    }

    public boolean shouldApplyToFuture() {
        return applyToFuture;
    }

    public boolean shouldAddToBlacklist() {
        return addToBlacklist;
    }

    @Override
    public String toString() {
        return "ApprovalDecision{" +
                "approved=" + approved +
                ", reason='" + reason + '\'' +
                ", applyToFuture=" + applyToFuture +
                ", addToBlacklist=" + addToBlacklist +
                '}';
    }
}
