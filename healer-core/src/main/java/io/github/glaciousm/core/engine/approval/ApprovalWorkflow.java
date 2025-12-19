package io.github.glaciousm.core.engine.approval;

import io.github.glaciousm.core.engine.blacklist.HealBlacklist;
import io.github.glaciousm.core.engine.cache.CacheKey;
import io.github.glaciousm.core.engine.cache.HealCache;
import io.github.glaciousm.core.engine.trust.TrustLevel;
import io.github.glaciousm.core.engine.trust.TrustLevelManager;
import io.github.glaciousm.core.model.ActionType;
import io.github.glaciousm.core.model.HealPolicy;
import io.github.glaciousm.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the approval workflow for heal proposals.
 * Coordinates between trust levels, approval callbacks, cache, and blacklist.
 */
public class ApprovalWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflow.class);

    private final TrustLevelManager trustManager;
    private final HealCache healCache;
    private final HealBlacklist blacklist;
    private final ApprovalCallback approvalCallback;

    // Pending proposals awaiting approval
    private final Map<String, HealProposal> pendingProposals = new ConcurrentHashMap<>();

    // History of decisions for audit
    private final List<ApprovalRecord> approvalHistory = Collections.synchronizedList(new ArrayList<>());

    public ApprovalWorkflow(TrustLevelManager trustManager, HealCache healCache,
                            HealBlacklist blacklist, ApprovalCallback approvalCallback) {
        this.trustManager = trustManager;
        this.healCache = healCache;
        this.blacklist = blacklist;
        this.approvalCallback = approvalCallback;
    }

    /**
     * Submit a heal proposal for approval.
     * Returns the decision on whether to apply the heal.
     */
    public ApprovalDecision submitForApproval(HealProposal proposal, HealPolicy policy) {
        logger.info("Processing heal proposal: {}", proposal.getId());

        // Check blacklist first
        if (isBlacklisted(proposal)) {
            logger.info("Proposal {} blocked by blacklist", proposal.getId());
            return ApprovalDecision.reject("Heal is blacklisted");
        }

        // Determine if auto-approval is allowed based on trust level and policy
        if (canAutoApprove(proposal, policy)) {
            logger.info("Auto-approving proposal {}", proposal.getId());
            proposal.approve("AUTO", "Auto-approved based on trust level and policy");
            recordDecision(proposal, ApprovalDecision.approve("Auto-approved"));

            if (approvalCallback != null) {
                approvalCallback.notifyAutoApplied(proposal);
            }

            return ApprovalDecision.approve("Auto-approved");
        }

        // Shadow mode - suggest only
        if (policy == HealPolicy.SUGGEST || trustManager.getCurrentLevel() == TrustLevel.L0_SHADOW) {
            logger.info("Proposal {} suggested only (shadow mode)", proposal.getId());
            pendingProposals.put(proposal.getId(), proposal);

            if (approvalCallback != null) {
                // Non-blocking notification
                approvalCallback.notifyAutoApplied(proposal);
            }

            return ApprovalDecision.skip();
        }

        // Manual approval required
        if (approvalCallback != null) {
            logger.info("Requesting manual approval for proposal {}", proposal.getId());
            pendingProposals.put(proposal.getId(), proposal);

            ApprovalDecision decision = approvalCallback.requestApproval(proposal);
            handleDecision(proposal, decision);
            return decision;
        }

        // No callback configured, default to reject
        logger.warn("No approval callback configured, rejecting proposal {}", proposal.getId());
        return ApprovalDecision.reject("No approval mechanism configured");
    }

    /**
     * Check if auto-approval is allowed for this proposal.
     */
    private boolean canAutoApprove(HealProposal proposal, HealPolicy policy) {
        // Policy must allow auto-heal
        if (policy != HealPolicy.AUTO_SAFE && policy != HealPolicy.AUTO_ALL) {
            return false;
        }

        // Check trust level
        TrustLevel level = trustManager.getCurrentLevel();
        if (!level.canAutoApply()) {
            return false;
        }

        // For AUTO_SAFE, only safe actions
        if (policy == HealPolicy.AUTO_SAFE) {
            ActionType actionType = proposal.getActionType();
            return trustManager.canAutoApply(actionType);
        }

        // AUTO_ALL allows everything at sufficient trust level
        return level.getLevel() >= TrustLevel.L3_AUTO.getLevel();
    }

    /**
     * Check if this heal is blacklisted.
     */
    private boolean isBlacklisted(HealProposal proposal) {
        if (blacklist == null) {
            return false;
        }
        return blacklist.isBlacklisted(
                proposal.getPageUrl(),
                proposal.getOriginalLocator(),
                proposal.getProposedLocator()
        );
    }

    /**
     * Handle an approval decision.
     */
    private void handleDecision(HealProposal proposal, ApprovalDecision decision) {
        pendingProposals.remove(proposal.getId());

        if (decision.isApproved()) {
            proposal.approve("MANUAL", decision.getReason());
            trustManager.recordSuccess();

            // Cache if requested
            if (decision.shouldApplyToFuture() && healCache != null) {
                CacheKey key = CacheKey.builder()
                        .pageUrl(proposal.getPageUrl())
                        .originalLocator(proposal.getOriginalLocator())
                        .actionType(proposal.getActionType())
                        .build();
                healCache.put(key, proposal.getProposedLocator(),
                        proposal.getConfidence(), proposal.getReasoning());
            }
        } else {
            proposal.reject("MANUAL", decision.getReason());
            trustManager.recordFailure();

            // Blacklist if requested
            if (decision.shouldAddToBlacklist() && blacklist != null) {
                blacklist.addLocator(
                        proposal.getOriginalLocator(),
                        proposal.getProposedLocator(),
                        decision.getReason()
                );
            }
        }

        recordDecision(proposal, decision);
    }

    /**
     * Record a decision for audit.
     */
    private void recordDecision(HealProposal proposal, ApprovalDecision decision) {
        approvalHistory.add(new ApprovalRecord(proposal, decision));
        logger.debug("Recorded approval decision: {} -> {}",
                proposal.getId(), decision.isApproved() ? "APPROVED" : "REJECTED");
    }

    /**
     * Get pending proposals.
     */
    public List<HealProposal> getPendingProposals() {
        return new ArrayList<>(pendingProposals.values());
    }

    /**
     * Get a pending proposal by ID.
     */
    public Optional<HealProposal> getProposal(String id) {
        return Optional.ofNullable(pendingProposals.get(id));
    }

    /**
     * Approve a pending proposal by ID.
     */
    public boolean approveProposal(String id, String reviewer, String notes, boolean applyToFuture) {
        HealProposal proposal = pendingProposals.get(id);
        if (proposal == null) {
            return false;
        }

        ApprovalDecision decision = applyToFuture
                ? ApprovalDecision.approveAndRemember()
                : ApprovalDecision.approve(notes);
        handleDecision(proposal, decision);
        return true;
    }

    /**
     * Reject a pending proposal by ID.
     */
    public boolean rejectProposal(String id, String reviewer, String reason, boolean addToBlacklist) {
        HealProposal proposal = pendingProposals.get(id);
        if (proposal == null) {
            return false;
        }

        ApprovalDecision decision = addToBlacklist
                ? ApprovalDecision.rejectAndBlacklist(reason)
                : ApprovalDecision.reject(reason);
        handleDecision(proposal, decision);
        return true;
    }

    /**
     * Get approval history.
     */
    public List<ApprovalRecord> getApprovalHistory() {
        return new ArrayList<>(approvalHistory);
    }

    /**
     * Record of an approval decision for audit purposes.
     */
    public record ApprovalRecord(
            HealProposal proposal,
            ApprovalDecision decision
    ) {}
}
