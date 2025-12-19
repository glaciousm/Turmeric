package io.github.glaciousm.core.engine.cache;

import io.github.glaciousm.core.model.LocatorInfo;

import java.time.Instant;

/**
 * Cache entry storing a healed locator with metadata.
 */
public class CacheEntry {

    private final CacheKey key;
    private final LocatorInfo healedLocator;
    private final double confidence;
    private final String reasoning;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int hitCount;
    private Instant lastAccessedAt;
    private int successCount;
    private int failureCount;

    private CacheEntry(Builder builder) {
        this.key = builder.key;
        this.healedLocator = builder.healedLocator;
        this.confidence = builder.confidence;
        this.reasoning = builder.reasoning;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(builder.ttlSeconds);
        this.hitCount = 0;
        this.lastAccessedAt = this.createdAt;
        this.successCount = 0;
        this.failureCount = 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Record a cache hit and return the healed locator.
     */
    public LocatorInfo recordHit() {
        hitCount++;
        lastAccessedAt = Instant.now();
        return healedLocator;
    }

    /**
     * Record that the cached heal was successful.
     */
    public void recordSuccess() {
        successCount++;
    }

    /**
     * Record that the cached heal failed.
     */
    public void recordFailure() {
        failureCount++;
    }

    /**
     * Check if this entry has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this entry should be evicted due to failures.
     */
    public boolean shouldEvict() {
        // Evict if failure rate > 50% with at least 3 attempts
        int totalAttempts = successCount + failureCount;
        if (totalAttempts >= 3) {
            return (double) failureCount / totalAttempts > 0.5;
        }
        return false;
    }

    /**
     * Get the success rate of this cached heal.
     */
    public double getSuccessRate() {
        int total = successCount + failureCount;
        return total > 0 ? (double) successCount / total : 1.0;
    }

    public CacheKey getKey() {
        return key;
    }

    public LocatorInfo getHealedLocator() {
        return healedLocator;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getHitCount() {
        return hitCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public static class Builder {
        private CacheKey key;
        private LocatorInfo healedLocator;
        private double confidence;
        private String reasoning;
        private long ttlSeconds = 86400; // Default 24 hours

        public Builder key(CacheKey key) {
            this.key = key;
            return this;
        }

        public Builder healedLocator(LocatorInfo healedLocator) {
            this.healedLocator = healedLocator;
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

        public Builder ttlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public CacheEntry build() {
            return new CacheEntry(this);
        }
    }
}
