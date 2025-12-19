package io.github.glaciousm.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Full intent contract for a test step.
 * Defines what the step is meant to accomplish and how it should be validated.
 */
public final class IntentContract {
    private final String action;
    private final String description;
    private final HealPolicy policy;
    private final boolean destructive;
    private final Class<? extends OutcomeCheck> outcomeCheck;
    private final List<Class<? extends InvariantCheck>> invariants;
    private final String outcomeDescription;

    private IntentContract(Builder builder) {
        this.action = builder.action;
        this.description = builder.description;
        this.policy = builder.policy != null ? builder.policy : HealPolicy.AUTO_SAFE;
        this.destructive = builder.destructive;
        this.outcomeCheck = builder.outcomeCheck;
        this.invariants = builder.invariants != null ? List.copyOf(builder.invariants) : List.of();
        this.outcomeDescription = builder.outcomeDescription;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }

    public HealPolicy getPolicy() {
        return policy;
    }

    public boolean isDestructive() {
        return destructive;
    }

    public Class<? extends OutcomeCheck> getOutcomeCheck() {
        return outcomeCheck;
    }

    public List<Class<? extends InvariantCheck>> getInvariants() {
        return invariants;
    }

    public String getOutcomeDescription() {
        return outcomeDescription;
    }

    /**
     * Checks if healing is allowed for this intent.
     */
    public boolean isHealingAllowed() {
        return policy != HealPolicy.OFF;
    }

    /**
     * Checks if automatic healing is enabled.
     */
    public boolean isAutoHealEnabled() {
        return policy == HealPolicy.AUTO_SAFE || policy == HealPolicy.AUTO_ALL;
    }

    /**
     * Checks if destructive actions are allowed.
     */
    public boolean isDestructiveAllowed() {
        return policy == HealPolicy.AUTO_ALL && destructive;
    }

    /**
     * Converts to lightweight metadata for serialization.
     */
    public IntentMetadata toMetadata() {
        return IntentMetadata.builder()
                .action(action)
                .description(description)
                .policy(policy)
                .destructive(destructive)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default intent contract for steps without explicit annotation.
     */
    public static IntentContract defaultContract(String stepText) {
        return builder()
                .action("unknown")
                .description(stepText)
                .policy(HealPolicy.AUTO_SAFE)
                .destructive(false)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntentContract that = (IntentContract) o;
        return destructive == that.destructive &&
               Objects.equals(action, that.action) &&
               Objects.equals(description, that.description) &&
               policy == that.policy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, description, policy, destructive);
    }

    @Override
    public String toString() {
        return "IntentContract{action='" + action + "', policy=" + policy + ", destructive=" + destructive + "}";
    }

    public static final class Builder {
        private String action;
        private String description;
        private HealPolicy policy;
        private boolean destructive;
        private Class<? extends OutcomeCheck> outcomeCheck;
        private List<Class<? extends InvariantCheck>> invariants;
        private String outcomeDescription;

        private Builder() {
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder policy(HealPolicy policy) {
            this.policy = policy;
            return this;
        }

        public Builder destructive(boolean destructive) {
            this.destructive = destructive;
            return this;
        }

        public Builder outcomeCheck(Class<? extends OutcomeCheck> outcomeCheck) {
            this.outcomeCheck = outcomeCheck;
            return this;
        }

        public Builder invariants(List<Class<? extends InvariantCheck>> invariants) {
            this.invariants = invariants;
            return this;
        }

        public Builder outcomeDescription(String outcomeDescription) {
            this.outcomeDescription = outcomeDescription;
            return this;
        }

        public IntentContract build() {
            return new IntentContract(this);
        }
    }

    /**
     * Interface for outcome validation checks.
     */
    public interface OutcomeCheck {
        /**
         * Verify the expected outcome was achieved.
         */
        OutcomeResult verify(ExecutionContext ctx);

        /**
         * Human-readable description of this check.
         */
        String getDescription();
    }

    /**
     * Interface for invariant checks.
     */
    public interface InvariantCheck {
        /**
         * Check if invariant is still valid.
         */
        InvariantResult verify(ExecutionContext ctx);

        /**
         * Human-readable description of this invariant.
         */
        String getDescription();
    }
}
