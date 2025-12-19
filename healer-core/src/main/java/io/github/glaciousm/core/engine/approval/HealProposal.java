package io.github.glaciousm.core.engine.approval;

import io.github.glaciousm.core.model.LocatorInfo;
import io.github.glaciousm.core.model.ActionType;

import java.time.Instant;
import java.util.UUID;

/**
 * A proposed heal that requires human approval before being applied.
 */
public class HealProposal {

    private final String id;
    private final String featureName;
    private final String scenarioName;
    private final String stepText;
    private final LocatorInfo originalLocator;
    private final LocatorInfo proposedLocator;
    private final ActionType actionType;
    private final double confidence;
    private final String reasoning;
    private final String pageUrl;
    private final Instant createdAt;
    private ApprovalStatus status;
    private String reviewerNotes;
    private String reviewedBy;
    private Instant reviewedAt;

    private HealProposal(Builder builder) {
        this.id = UUID.randomUUID().toString();
        this.featureName = builder.featureName;
        this.scenarioName = builder.scenarioName;
        this.stepText = builder.stepText;
        this.originalLocator = builder.originalLocator;
        this.proposedLocator = builder.proposedLocator;
        this.actionType = builder.actionType;
        this.confidence = builder.confidence;
        this.reasoning = builder.reasoning;
        this.pageUrl = builder.pageUrl;
        this.createdAt = Instant.now();
        this.status = ApprovalStatus.PENDING;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Approve this heal proposal.
     */
    public void approve(String reviewedBy, String notes) {
        this.status = ApprovalStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewerNotes = notes;
        this.reviewedAt = Instant.now();
    }

    /**
     * Reject this heal proposal.
     */
    public void reject(String reviewedBy, String reason) {
        this.status = ApprovalStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewerNotes = reason;
        this.reviewedAt = Instant.now();
    }

    /**
     * Mark as needing more information.
     */
    public void requestInfo(String reviewedBy, String question) {
        this.status = ApprovalStatus.NEEDS_INFO;
        this.reviewedBy = reviewedBy;
        this.reviewerNotes = question;
        this.reviewedAt = Instant.now();
    }

    /**
     * Check if this proposal is pending review.
     */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING || status == ApprovalStatus.NEEDS_INFO;
    }

    /**
     * Check if this proposal was approved.
     */
    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }

    /**
     * Check if this proposal was rejected.
     */
    public boolean isRejected() {
        return status == ApprovalStatus.REJECTED;
    }

    // Getters
    public String getId() { return id; }
    public String getFeatureName() { return featureName; }
    public String getScenarioName() { return scenarioName; }
    public String getStepText() { return stepText; }
    public LocatorInfo getOriginalLocator() { return originalLocator; }
    public LocatorInfo getProposedLocator() { return proposedLocator; }
    public ActionType getActionType() { return actionType; }
    public double getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public String getPageUrl() { return pageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public ApprovalStatus getStatus() { return status; }
    public String getReviewerNotes() { return reviewerNotes; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }

    @Override
    public String toString() {
        return String.format("HealProposal{id='%s', step='%s', %s -> %s, confidence=%.2f, status=%s}",
                id, stepText, originalLocator, proposedLocator, confidence, status);
    }

    public static class Builder {
        private String featureName;
        private String scenarioName;
        private String stepText;
        private LocatorInfo originalLocator;
        private LocatorInfo proposedLocator;
        private ActionType actionType;
        private double confidence;
        private String reasoning;
        private String pageUrl;

        public Builder featureName(String featureName) {
            this.featureName = featureName;
            return this;
        }

        public Builder scenarioName(String scenarioName) {
            this.scenarioName = scenarioName;
            return this;
        }

        public Builder stepText(String stepText) {
            this.stepText = stepText;
            return this;
        }

        public Builder originalLocator(LocatorInfo originalLocator) {
            this.originalLocator = originalLocator;
            return this;
        }

        public Builder proposedLocator(LocatorInfo proposedLocator) {
            this.proposedLocator = proposedLocator;
            return this;
        }

        public Builder actionType(ActionType actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder pageUrl(String pageUrl) {
            this.pageUrl = pageUrl;
            return this;
        }

        public HealProposal build() {
            return new HealProposal(this);
        }
    }
}
