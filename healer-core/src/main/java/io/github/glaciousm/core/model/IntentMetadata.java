package io.github.glaciousm.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Metadata about the intent of a test step.
 * This is a lightweight version of IntentContract for serialization.
 */
public final class IntentMetadata {
    private final String action;
    private final String description;
    private final HealPolicy policy;
    private final boolean destructive;

    @JsonCreator
    public IntentMetadata(
            @JsonProperty("action") String action,
            @JsonProperty("description") String description,
            @JsonProperty("policy") HealPolicy policy,
            @JsonProperty("destructive") boolean destructive) {
        this.action = action;
        this.description = description;
        this.policy = policy != null ? policy : HealPolicy.AUTO_SAFE;
        this.destructive = destructive;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntentMetadata that = (IntentMetadata) o;
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
        return "IntentMetadata{action='" + action + "', description='" + description +
               "', policy=" + policy + ", destructive=" + destructive + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String action;
        private String description;
        private HealPolicy policy = HealPolicy.AUTO_SAFE;
        private boolean destructive = false;

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

        public IntentMetadata build() {
            return new IntentMetadata(action, description, policy, destructive);
        }
    }
}
