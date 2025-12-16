package com.intenthealer.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Complete result of a healing attempt.
 * Contains the outcome, decision details, and any error information.
 */
public final class HealResult {
    private final String id;
    private final HealOutcome outcome;
    private final HealDecision decision;
    private final Integer healedElementIndex;
    private final String healedLocator;
    private final double confidence;
    private final String reasoning;
    private final String failureReason;
    private final Instant timestamp;
    private final Duration duration;
    private final boolean fromCache;
    private final SourceLocation sourceLocation;

    @JsonCreator
    public HealResult(
            @JsonProperty("id") String id,
            @JsonProperty("outcome") HealOutcome outcome,
            @JsonProperty("decision") HealDecision decision,
            @JsonProperty("healedElementIndex") Integer healedElementIndex,
            @JsonProperty("healedLocator") String healedLocator,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("reasoning") String reasoning,
            @JsonProperty("failureReason") String failureReason,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("duration") Duration duration,
            @JsonProperty("fromCache") boolean fromCache,
            @JsonProperty("sourceLocation") SourceLocation sourceLocation) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.outcome = Objects.requireNonNull(outcome, "outcome cannot be null");
        this.decision = decision;
        this.healedElementIndex = healedElementIndex;
        this.healedLocator = healedLocator;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.failureReason = failureReason;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.duration = duration;
        this.fromCache = fromCache;
        this.sourceLocation = sourceLocation;
    }

    public String getId() {
        return id;
    }

    public HealOutcome getOutcome() {
        return outcome;
    }

    public Optional<HealDecision> getDecision() {
        return Optional.ofNullable(decision);
    }

    public Optional<Integer> getHealedElementIndex() {
        return Optional.ofNullable(healedElementIndex);
    }

    public Optional<String> getHealedLocator() {
        return Optional.ofNullable(healedLocator);
    }

    public double getConfidence() {
        return confidence;
    }

    public Optional<String> getReasoning() {
        return Optional.ofNullable(reasoning);
    }

    public Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Optional<Duration> getDuration() {
        return Optional.ofNullable(duration);
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public Optional<SourceLocation> getSourceLocation() {
        return Optional.ofNullable(sourceLocation);
    }

    public boolean isSuccess() {
        return outcome == HealOutcome.SUCCESS;
    }

    public boolean isRefused() {
        return outcome == HealOutcome.REFUSED;
    }

    public boolean isFailed() {
        return outcome == HealOutcome.FAILED ||
               outcome == HealOutcome.OUTCOME_FAILED ||
               outcome == HealOutcome.INVARIANT_VIOLATED;
    }

    /**
     * Creates a successful heal result.
     */
    public static HealResult success(int elementIndex, double confidence, String reasoning, String healedLocator) {
        return builder()
                .outcome(HealOutcome.SUCCESS)
                .healedElementIndex(elementIndex)
                .confidence(confidence)
                .reasoning(reasoning)
                .healedLocator(healedLocator)
                .build();
    }

    /**
     * Creates a refused heal result.
     */
    public static HealResult refused(String reason) {
        return builder()
                .outcome(HealOutcome.REFUSED)
                .failureReason(reason)
                .build();
    }

    /**
     * Creates a failed heal result.
     */
    public static HealResult failed(String reason) {
        return builder()
                .outcome(HealOutcome.FAILED)
                .failureReason(reason)
                .build();
    }

    /**
     * Creates a suggested heal result (for SUGGEST mode).
     */
    public static HealResult suggested(HealDecision decision) {
        return builder()
                .outcome(HealOutcome.SUGGESTED)
                .decision(decision)
                .healedElementIndex(decision.getSelectedElementIndex())
                .confidence(decision.getConfidence())
                .reasoning(decision.getReasoning())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "HealResult{outcome=" + outcome +
               ", confidence=" + String.format("%.2f", confidence) +
               ", healedElementIndex=" + healedElementIndex +
               (failureReason != null ? ", failureReason='" + failureReason + "'" : "") + "}";
    }

    public static final class Builder {
        private String id;
        private HealOutcome outcome;
        private HealDecision decision;
        private Integer healedElementIndex;
        private String healedLocator;
        private double confidence;
        private String reasoning;
        private String failureReason;
        private Instant timestamp;
        private Duration duration;
        private boolean fromCache;
        private SourceLocation sourceLocation;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder outcome(HealOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder decision(HealDecision decision) {
            this.decision = decision;
            return this;
        }

        public Builder healedElementIndex(Integer healedElementIndex) {
            this.healedElementIndex = healedElementIndex;
            return this;
        }

        public Builder healedLocator(String healedLocator) {
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

        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder fromCache(boolean fromCache) {
            this.fromCache = fromCache;
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public HealResult build() {
            return new HealResult(id, outcome, decision, healedElementIndex, healedLocator,
                    confidence, reasoning, failureReason, timestamp, duration, fromCache, sourceLocation);
        }
    }
}
