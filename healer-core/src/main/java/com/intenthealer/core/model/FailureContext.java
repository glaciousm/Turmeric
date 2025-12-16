package com.intenthealer.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains all context needed to understand a test failure.
 * This is the primary input to the healing engine.
 */
public final class FailureContext {
    private final String featureName;
    private final String scenarioName;
    private final String stepText;
    private final String stepKeyword;
    private final List<String> tags;
    private final String exceptionType;
    private final String exceptionMessage;
    private final LocatorInfo originalLocator;
    private final ActionType actionType;
    private final Object actionData;
    private final FailureKind failureKind;
    private final IntentMetadata intentMetadata;
    private final Instant timestamp;
    private final Map<String, Object> additionalContext;
    private final SourceLocation sourceLocation;

    @JsonCreator
    public FailureContext(
            @JsonProperty("featureName") String featureName,
            @JsonProperty("scenarioName") String scenarioName,
            @JsonProperty("stepText") String stepText,
            @JsonProperty("stepKeyword") String stepKeyword,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("exceptionType") String exceptionType,
            @JsonProperty("exceptionMessage") String exceptionMessage,
            @JsonProperty("originalLocator") LocatorInfo originalLocator,
            @JsonProperty("actionType") ActionType actionType,
            @JsonProperty("actionData") Object actionData,
            @JsonProperty("failureKind") FailureKind failureKind,
            @JsonProperty("intentMetadata") IntentMetadata intentMetadata,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("additionalContext") Map<String, Object> additionalContext,
            @JsonProperty("sourceLocation") SourceLocation sourceLocation) {
        this.featureName = featureName;
        this.scenarioName = scenarioName;
        this.stepText = Objects.requireNonNull(stepText, "stepText cannot be null");
        this.stepKeyword = stepKeyword;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.exceptionType = exceptionType;
        this.exceptionMessage = exceptionMessage;
        this.originalLocator = originalLocator;
        this.actionType = actionType != null ? actionType : ActionType.UNKNOWN;
        this.actionData = actionData;
        this.failureKind = failureKind != null ? failureKind : FailureKind.UNKNOWN;
        this.intentMetadata = intentMetadata;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.additionalContext = additionalContext != null ? Map.copyOf(additionalContext) : Map.of();
        this.sourceLocation = sourceLocation;
    }

    public String getFeatureName() {
        return featureName;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public String getStepText() {
        return stepText;
    }

    public String getStepKeyword() {
        return stepKeyword;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public LocatorInfo getOriginalLocator() {
        return originalLocator;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public Object getActionData() {
        return actionData;
    }

    public FailureKind getFailureKind() {
        return failureKind;
    }

    public IntentMetadata getIntentMetadata() {
        return intentMetadata;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getAdditionalContext() {
        return additionalContext;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    /**
     * Returns true if this failure is an assertion step (Then keyword).
     */
    public boolean isAssertionStep() {
        return "Then".equalsIgnoreCase(stepKeyword);
    }

    /**
     * Returns a builder for creating FailureContext instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String featureName;
        private String scenarioName;
        private String stepText;
        private String stepKeyword;
        private List<String> tags;
        private String exceptionType;
        private String exceptionMessage;
        private LocatorInfo originalLocator;
        private ActionType actionType;
        private Object actionData;
        private FailureKind failureKind;
        private IntentMetadata intentMetadata;
        private Instant timestamp;
        private Map<String, Object> additionalContext;
        private SourceLocation sourceLocation;

        private Builder() {
        }

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

        public Builder stepKeyword(String stepKeyword) {
            this.stepKeyword = stepKeyword;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder exceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
            return this;
        }

        public Builder exceptionMessage(String exceptionMessage) {
            this.exceptionMessage = exceptionMessage;
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

        public Builder actionData(Object actionData) {
            this.actionData = actionData;
            return this;
        }

        public Builder failureKind(FailureKind failureKind) {
            this.failureKind = failureKind;
            return this;
        }

        public Builder intentMetadata(IntentMetadata intentMetadata) {
            this.intentMetadata = intentMetadata;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder additionalContext(Map<String, Object> additionalContext) {
            this.additionalContext = additionalContext;
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder exception(RuntimeException exception) {
            if (exception != null) {
                this.exceptionType = exception.getClass().getSimpleName();
                this.exceptionMessage = exception.getMessage();
            }
            return this;
        }

        public Builder pageUrl(String url) {
            if (this.additionalContext == null) {
                this.additionalContext = new java.util.HashMap<>();
            }
            this.additionalContext.put("pageUrl", url);
            return this;
        }

        public Builder pageTitle(String title) {
            if (this.additionalContext == null) {
                this.additionalContext = new java.util.HashMap<>();
            }
            this.additionalContext.put("pageTitle", title);
            return this;
        }

        public FailureContext build() {
            return new FailureContext(
                    featureName, scenarioName, stepText, stepKeyword, tags,
                    exceptionType, exceptionMessage, originalLocator, actionType,
                    actionData, failureKind, intentMetadata, timestamp, additionalContext,
                    sourceLocation);
        }
    }
}
