package com.intenthealer.report.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * A single healing event in the report.
 */
public class HealEvent {

    @JsonProperty("event_id")
    private String eventId = UUID.randomUUID().toString();

    @JsonProperty("timestamp")
    private Instant timestamp = Instant.now();

    @JsonProperty("feature")
    private String feature;

    @JsonProperty("scenario")
    private String scenario;

    @JsonProperty("step")
    private String step;

    @JsonProperty("failure")
    private FailureInfo failure;

    @JsonProperty("decision")
    private DecisionInfo decision;

    @JsonProperty("result")
    private ResultInfo result;

    @JsonProperty("artifacts")
    private ArtifactInfo artifacts;

    @JsonProperty("cost")
    private CostInfo cost;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }

    public FailureInfo getFailure() { return failure; }
    public void setFailure(FailureInfo failure) { this.failure = failure; }

    public DecisionInfo getDecision() { return decision; }
    public void setDecision(DecisionInfo decision) { this.decision = decision; }

    public ResultInfo getResult() { return result; }
    public void setResult(ResultInfo result) { this.result = result; }

    public ArtifactInfo getArtifacts() { return artifacts; }
    public void setArtifacts(ArtifactInfo artifacts) { this.artifacts = artifacts; }

    public CostInfo getCost() { return cost; }
    public void setCost(CostInfo cost) { this.cost = cost; }

    // Convenience methods for WeeklyHealthReportGenerator
    public String getOutcome() {
        return result != null ? result.getStatus() : null;
    }

    public Double getLlmCostUsd() {
        return cost != null ? cost.getCostUsd() : null;
    }

    public Double getLlmLatencyMs() {
        // Not tracked in current model, return null
        return null;
    }

    public String getOriginalLocator() {
        return failure != null ? failure.getOriginalLocator() : null;
    }

    public String getStepText() {
        return step;
    }

    public String getHealedLocator() {
        return result != null ? result.getHealedLocator() : null;
    }

    /**
     * Information about the original failure.
     */
    public static class FailureInfo {
        @JsonProperty("exception_type")
        private String exceptionType;

        @JsonProperty("message")
        private String message;

        @JsonProperty("original_locator")
        private String originalLocator;

        public String getExceptionType() { return exceptionType; }
        public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getOriginalLocator() { return originalLocator; }
        public void setOriginalLocator(String originalLocator) { this.originalLocator = originalLocator; }
    }

    /**
     * Information about the LLM's decision.
     */
    public static class DecisionInfo {
        @JsonProperty("can_heal")
        private boolean canHeal;

        @JsonProperty("confidence")
        private double confidence;

        @JsonProperty("selected_element_index")
        private Integer selectedElementIndex;

        @JsonProperty("reasoning")
        private String reasoning;

        @JsonProperty("refusal_reason")
        private String refusalReason;

        public boolean isCanHeal() { return canHeal; }
        public void setCanHeal(boolean canHeal) { this.canHeal = canHeal; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public Integer getSelectedElementIndex() { return selectedElementIndex; }
        public void setSelectedElementIndex(Integer selectedElementIndex) { this.selectedElementIndex = selectedElementIndex; }

        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }

        public String getRefusalReason() { return refusalReason; }
        public void setRefusalReason(String refusalReason) { this.refusalReason = refusalReason; }
    }

    /**
     * Information about the healing result.
     */
    public static class ResultInfo {
        @JsonProperty("status")
        private String status;  // SUCCESS, REFUSED, FAILED

        @JsonProperty("healed_locator")
        private String healedLocator;

        @JsonProperty("outcome_check_passed")
        private Boolean outcomeCheckPassed;

        @JsonProperty("invariants_satisfied")
        private Boolean invariantsSatisfied;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getHealedLocator() { return healedLocator; }
        public void setHealedLocator(String healedLocator) { this.healedLocator = healedLocator; }

        public Boolean getOutcomeCheckPassed() { return outcomeCheckPassed; }
        public void setOutcomeCheckPassed(Boolean outcomeCheckPassed) { this.outcomeCheckPassed = outcomeCheckPassed; }

        public Boolean getInvariantsSatisfied() { return invariantsSatisfied; }
        public void setInvariantsSatisfied(Boolean invariantsSatisfied) { this.invariantsSatisfied = invariantsSatisfied; }
    }

    /**
     * Artifact references.
     */
    public static class ArtifactInfo {
        @JsonProperty("screenshot")
        private String screenshot;

        @JsonProperty("dom_snapshot")
        private String domSnapshot;

        public String getScreenshot() { return screenshot; }
        public void setScreenshot(String screenshot) { this.screenshot = screenshot; }

        public String getDomSnapshot() { return domSnapshot; }
        public void setDomSnapshot(String domSnapshot) { this.domSnapshot = domSnapshot; }
    }

    /**
     * LLM cost information.
     */
    public static class CostInfo {
        @JsonProperty("input_tokens")
        private int inputTokens;

        @JsonProperty("output_tokens")
        private int outputTokens;

        @JsonProperty("cost_usd")
        private double costUsd;

        public int getInputTokens() { return inputTokens; }
        public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

        public int getOutputTokens() { return outputTokens; }
        public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

        public double getCostUsd() { return costUsd; }
        public void setCostUsd(double costUsd) { this.costUsd = costUsd; }
    }
}
