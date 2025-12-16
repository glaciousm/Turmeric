package com.intenthealer.core.model;

/**
 * Represents a request to heal a failed element location.
 */
public final class HealingRequest {
    private final String intentDescription;
    private final LocatorInfo originalLocator;
    private final ActionType actionType;
    private final FailureContext failureContext;

    private HealingRequest(Builder builder) {
        this.intentDescription = builder.intentDescription;
        this.originalLocator = builder.originalLocator;
        this.actionType = builder.actionType;
        this.failureContext = builder.failureContext;
    }

    public String getIntentDescription() {
        return intentDescription;
    }

    public LocatorInfo getOriginalLocator() {
        return originalLocator;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public FailureContext getFailureContext() {
        return failureContext;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String intentDescription;
        private LocatorInfo originalLocator;
        private ActionType actionType;
        private FailureContext failureContext;

        private Builder() {}

        public Builder intentDescription(String intentDescription) {
            this.intentDescription = intentDescription;
            return this;
        }

        public Builder originalLocator(LocatorInfo originalLocator) {
            this.originalLocator = originalLocator;
            return this;
        }

        public Builder actionType(ActionType actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder failureContext(FailureContext failureContext) {
            this.failureContext = failureContext;
            return this;
        }

        public HealingRequest build() {
            return new HealingRequest(this);
        }
    }
}
