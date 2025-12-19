package io.github.glaciousm.core.engine.blacklist;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A blacklist entry that prevents certain heals from being applied.
 */
public class BlacklistEntry {

    private final String id;
    private final String pageUrlPattern;
    private final String originalLocatorStrategy;
    private final String originalLocatorValue;
    private final String healedLocatorStrategy;
    private final String healedLocatorValue;
    private final String reason;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String addedBy;

    private BlacklistEntry(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.pageUrlPattern = builder.pageUrlPattern;
        this.originalLocatorStrategy = builder.originalLocatorStrategy;
        this.originalLocatorValue = builder.originalLocatorValue;
        this.healedLocatorStrategy = builder.healedLocatorStrategy;
        this.healedLocatorValue = builder.healedLocatorValue;
        this.reason = builder.reason;
        this.createdAt = Instant.now();
        // Direct expiresAt takes precedence over ttlSeconds
        if (builder.expiresAt != null) {
            this.expiresAt = builder.expiresAt;
        } else if (builder.ttlSeconds > 0) {
            this.expiresAt = this.createdAt.plusSeconds(builder.ttlSeconds);
        } else {
            this.expiresAt = null;
        }
        this.addedBy = builder.addedBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Check if this entry matches a heal attempt.
     */
    public boolean matches(String pageUrl, String origStrategy, String origValue,
                           String healedStrategy, String healedValue) {
        // Must match original locator
        if (!matchesLocator(originalLocatorStrategy, originalLocatorValue,
                origStrategy, origValue)) {
            return false;
        }

        // If healed locator is specified in entry, must match that too
        if (healedLocatorStrategy != null && healedLocatorValue != null) {
            if (!matchesLocator(healedLocatorStrategy, healedLocatorValue,
                    healedStrategy, healedValue)) {
                return false;
            }
        }

        // If page pattern is specified, URL must match
        if (pageUrlPattern != null) {
            return pageUrl != null && pageUrl.matches(pageUrlPattern);
        }

        return true;
    }

    private boolean matchesLocator(String expectedStrategy, String expectedValue,
                                   String actualStrategy, String actualValue) {
        if (expectedStrategy == null || expectedValue == null) {
            return true; // No constraint
        }
        return Objects.equals(expectedStrategy, actualStrategy) &&
               Objects.equals(expectedValue, actualValue);
    }

    /**
     * Check if this entry has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public String getId() { return id; }
    public String getPageUrlPattern() { return pageUrlPattern; }
    public String getOriginalLocatorStrategy() { return originalLocatorStrategy; }
    public String getOriginalLocatorValue() { return originalLocatorValue; }
    public String getHealedLocatorStrategy() { return healedLocatorStrategy; }
    public String getHealedLocatorValue() { return healedLocatorValue; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getAddedBy() { return addedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlacklistEntry that = (BlacklistEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BlacklistEntry{");
        sb.append("id='").append(id).append('\'');
        if (pageUrlPattern != null) {
            sb.append(", pageUrlPattern='").append(pageUrlPattern).append('\'');
        }
        sb.append(", original=").append(originalLocatorStrategy)
          .append(":").append(originalLocatorValue);
        if (healedLocatorStrategy != null) {
            sb.append(", healed=").append(healedLocatorStrategy)
              .append(":").append(healedLocatorValue);
        }
        sb.append(", reason='").append(reason).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public static class Builder {
        private String id;
        private String pageUrlPattern;
        private String originalLocatorStrategy;
        private String originalLocatorValue;
        private String healedLocatorStrategy;
        private String healedLocatorValue;
        private String reason;
        private long ttlSeconds;
        private Instant expiresAt;
        private String addedBy;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder pageUrlPattern(String pageUrlPattern) {
            this.pageUrlPattern = pageUrlPattern;
            return this;
        }

        public Builder originalLocator(String strategy, String value) {
            this.originalLocatorStrategy = strategy;
            this.originalLocatorValue = value;
            return this;
        }

        public Builder healedLocator(String strategy, String value) {
            this.healedLocatorStrategy = strategy;
            this.healedLocatorValue = value;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder ttlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder addedBy(String addedBy) {
            this.addedBy = addedBy;
            return this;
        }

        public BlacklistEntry build() {
            if (originalLocatorStrategy == null || originalLocatorValue == null) {
                throw new IllegalArgumentException("Original locator is required");
            }
            return new BlacklistEntry(this);
        }
    }
}
