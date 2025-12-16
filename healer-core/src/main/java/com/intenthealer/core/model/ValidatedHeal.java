package com.intenthealer.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a heal that has been validated by a passing test.
 * Used for auto-updating source code with confirmed fixes.
 */
public final class ValidatedHeal {

    private final String healId;
    private final SourceLocation sourceLocation;
    private final String originalLocator;
    private final String healedLocator;
    private final LocatorInfo.LocatorStrategy locatorStrategy;
    private final double confidence;
    private final String testName;
    private final String scenarioName;
    private final Instant validatedAt;
    private final String reasoning;

    @JsonCreator
    public ValidatedHeal(
            @JsonProperty("healId") String healId,
            @JsonProperty("sourceLocation") SourceLocation sourceLocation,
            @JsonProperty("originalLocator") String originalLocator,
            @JsonProperty("healedLocator") String healedLocator,
            @JsonProperty("locatorStrategy") LocatorInfo.LocatorStrategy locatorStrategy,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("testName") String testName,
            @JsonProperty("scenarioName") String scenarioName,
            @JsonProperty("validatedAt") Instant validatedAt,
            @JsonProperty("reasoning") String reasoning) {
        this.healId = Objects.requireNonNull(healId, "healId cannot be null");
        this.sourceLocation = sourceLocation;
        this.originalLocator = originalLocator;
        this.healedLocator = Objects.requireNonNull(healedLocator, "healedLocator cannot be null");
        this.locatorStrategy = locatorStrategy;
        this.confidence = confidence;
        this.testName = testName;
        this.scenarioName = scenarioName;
        this.validatedAt = validatedAt != null ? validatedAt : Instant.now();
        this.reasoning = reasoning;
    }

    public String getHealId() {
        return healId;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public String getOriginalLocator() {
        return originalLocator;
    }

    public String getHealedLocator() {
        return healedLocator;
    }

    public LocatorInfo.LocatorStrategy getLocatorStrategy() {
        return locatorStrategy;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getTestName() {
        return testName;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public String getReasoning() {
        return reasoning;
    }

    /**
     * Returns true if this heal has a valid source location for auto-update.
     */
    public boolean canAutoUpdate() {
        return sourceLocation != null && sourceLocation.isUpdatable();
    }

    /**
     * Returns true if the confidence meets or exceeds the threshold.
     */
    public boolean meetsConfidenceThreshold(double threshold) {
        return confidence >= threshold;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidatedHeal that = (ValidatedHeal) o;
        return Objects.equals(healId, that.healId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(healId);
    }

    @Override
    public String toString() {
        return "ValidatedHeal{" +
                "healId='" + healId + '\'' +
                ", originalLocator='" + originalLocator + '\'' +
                ", healedLocator='" + healedLocator + '\'' +
                ", confidence=" + String.format("%.2f", confidence) +
                ", testName='" + testName + '\'' +
                (sourceLocation != null ? ", source=" + sourceLocation.toShortString() : "") +
                '}';
    }

    /**
     * Creates a builder for ValidatedHeal.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String healId;
        private SourceLocation sourceLocation;
        private String originalLocator;
        private String healedLocator;
        private LocatorInfo.LocatorStrategy locatorStrategy;
        private double confidence;
        private String testName;
        private String scenarioName;
        private Instant validatedAt;
        private String reasoning;

        private Builder() {}

        public Builder healId(String healId) {
            this.healId = healId;
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder originalLocator(String originalLocator) {
            this.originalLocator = originalLocator;
            return this;
        }

        public Builder healedLocator(String healedLocator) {
            this.healedLocator = healedLocator;
            return this;
        }

        public Builder locatorStrategy(LocatorInfo.LocatorStrategy locatorStrategy) {
            this.locatorStrategy = locatorStrategy;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder testName(String testName) {
            this.testName = testName;
            return this;
        }

        public Builder scenarioName(String scenarioName) {
            this.scenarioName = scenarioName;
            return this;
        }

        public Builder validatedAt(Instant validatedAt) {
            this.validatedAt = validatedAt;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public ValidatedHeal build() {
            return new ValidatedHeal(healId, sourceLocation, originalLocator, healedLocator,
                    locatorStrategy, confidence, testName, scenarioName, validatedAt, reasoning);
        }
    }
}
