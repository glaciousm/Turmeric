package io.github.glaciousm.core.engine.approval;

/**
 * Callback interface for heal proposal approval workflow.
 * Implementations can provide different approval mechanisms:
 * - Console-based interactive approval
 * - Web UI approval
 * - API-based approval
 * - Slack/Teams integration
 */
public interface ApprovalCallback {

    /**
     * Request approval for a heal proposal.
     * This method may block waiting for approval depending on implementation.
     *
     * @param proposal The heal proposal requiring approval
     * @return The approval decision
     */
    ApprovalDecision requestApproval(HealProposal proposal);

    /**
     * Notify that a proposal was auto-applied (for logging/audit).
     *
     * @param proposal The auto-applied proposal
     */
    default void notifyAutoApplied(HealProposal proposal) {
        // Default no-op
    }

    /**
     * Notify that a proposal was rejected by guardrails.
     *
     * @param proposal The rejected proposal
     * @param reason The rejection reason
     */
    default void notifyRejected(HealProposal proposal, String reason) {
        // Default no-op
    }
}
