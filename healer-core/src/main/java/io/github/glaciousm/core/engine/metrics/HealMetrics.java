package io.github.glaciousm.core.engine.metrics;

import java.time.Duration;
import java.time.Instant;

/**
 * Metrics data for a single heal attempt.
 */
public class HealMetrics {

    private final String featureName;
    private final String scenarioName;
    private final String stepName;
    private final String failureKind;
    private final String originalLocator;
    private final Instant startTime;

    private Instant endTime;
    private String result; // SUCCESS, REFUSED, FAILED
    private String healedLocator;
    private double confidence;
    private int inputTokens;
    private int outputTokens;
    private double llmCostUsd;
    private boolean cacheHit;
    private String errorMessage;
    private boolean outcomeCheckPassed;
    private boolean invariantsChecked;
    private boolean invariantsPassed;

    public HealMetrics(String featureName, String scenarioName, String stepName,
                       String failureKind, String originalLocator) {
        this.featureName = featureName;
        this.scenarioName = scenarioName;
        this.stepName = stepName;
        this.failureKind = failureKind;
        this.originalLocator = originalLocator;
        this.startTime = Instant.now();
    }

    public void complete(String result) {
        this.endTime = Instant.now();
        this.result = result;
    }

    public Duration getDuration() {
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end);
    }

    public long getDurationMs() {
        return getDuration().toMillis();
    }

    // Getters and setters
    public String getFeatureName() { return featureName; }
    public String getScenarioName() { return scenarioName; }
    public String getStepName() { return stepName; }
    public String getFailureKind() { return failureKind; }
    public String getOriginalLocator() { return originalLocator; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getResult() { return result; }

    public String getHealedLocator() { return healedLocator; }
    public void setHealedLocator(String healedLocator) { this.healedLocator = healedLocator; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

    public double getLlmCostUsd() { return llmCostUsd; }
    public void setLlmCostUsd(double llmCostUsd) { this.llmCostUsd = llmCostUsd; }

    public boolean isCacheHit() { return cacheHit; }
    public void setCacheHit(boolean cacheHit) { this.cacheHit = cacheHit; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isOutcomeCheckPassed() { return outcomeCheckPassed; }
    public void setOutcomeCheckPassed(boolean outcomeCheckPassed) { this.outcomeCheckPassed = outcomeCheckPassed; }

    public boolean isInvariantsChecked() { return invariantsChecked; }
    public void setInvariantsChecked(boolean invariantsChecked) { this.invariantsChecked = invariantsChecked; }

    public boolean isInvariantsPassed() { return invariantsPassed; }
    public void setInvariantsPassed(boolean invariantsPassed) { this.invariantsPassed = invariantsPassed; }

    public boolean isSuccess() {
        return "SUCCESS".equals(result);
    }

    public boolean isRefused() {
        return "REFUSED".equals(result);
    }

    public boolean isFailed() {
        return "FAILED".equals(result);
    }

    /**
     * Check if this was a false heal (initially success but later found to be incorrect).
     */
    public boolean isFalseHeal() {
        return isSuccess() && (!outcomeCheckPassed || (invariantsChecked && !invariantsPassed));
    }
}
