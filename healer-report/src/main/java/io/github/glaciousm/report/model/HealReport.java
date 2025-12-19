package io.github.glaciousm.report.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Complete healing report for a test run.
 */
public class HealReport {

    @JsonProperty("run_id")
    private String runId = UUID.randomUUID().toString();

    @JsonProperty("timestamp")
    private Instant timestamp = Instant.now();

    @JsonProperty("duration_ms")
    private long durationMs;

    @JsonProperty("config")
    private ConfigSummary config;

    @JsonProperty("summary")
    private ReportSummary summary = new ReportSummary();

    @JsonProperty("events")
    private List<HealEvent> events = new ArrayList<>();

    @JsonProperty("test_name")
    private String testName;

    @JsonProperty("test_status")
    private String testStatus;

    @JsonProperty("start_time")
    private Instant startTime;

    @JsonProperty("end_time")
    private Instant endTime;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public ConfigSummary getConfig() { return config; }
    public void setConfig(ConfigSummary config) { this.config = config; }

    public ReportSummary getSummary() { return summary; }
    public void setSummary(ReportSummary summary) { this.summary = summary; }

    public List<HealEvent> getEvents() { return events; }
    public void setEvents(List<HealEvent> events) { this.events = events; }

    public void addEvent(HealEvent event) {
        events.add(event);
        summary.updateFromEvent(event);
    }

    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }

    public String getTestStatus() { return testStatus; }
    public void setTestStatus(String testStatus) { this.testStatus = testStatus; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    /**
     * Configuration summary included in report.
     */
    public static class ConfigSummary {
        @JsonProperty("mode")
        private String mode;

        @JsonProperty("llm_provider")
        private String llmProvider;

        @JsonProperty("llm_model")
        private String llmModel;

        @JsonProperty("confidence_threshold")
        private double confidenceThreshold;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String getLlmProvider() { return llmProvider; }
        public void setLlmProvider(String llmProvider) { this.llmProvider = llmProvider; }

        public String getLlmModel() { return llmModel; }
        public void setLlmModel(String llmModel) { this.llmModel = llmModel; }

        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
    }

    /**
     * Summary statistics for the report.
     */
    public static class ReportSummary {
        @JsonProperty("total_steps")
        private int totalSteps;

        @JsonProperty("total_failures")
        private int totalFailures;

        @JsonProperty("heal_attempts")
        private int healAttempts;

        @JsonProperty("heal_successes")
        private int healSuccesses;

        @JsonProperty("heal_refusals")
        private int healRefusals;

        @JsonProperty("heal_failures")
        private int healFailures;

        @JsonProperty("total_llm_cost_usd")
        private double totalLlmCostUsd;

        public void updateFromEvent(HealEvent event) {
            healAttempts++;
            switch (event.getResult().getStatus()) {
                case "SUCCESS" -> healSuccesses++;
                case "REFUSED" -> healRefusals++;
                case "FAILED" -> healFailures++;
            }
            if (event.getCost() != null) {
                totalLlmCostUsd += event.getCost().getCostUsd();
            }
        }

        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }

        public int getTotalFailures() { return totalFailures; }
        public void setTotalFailures(int totalFailures) { this.totalFailures = totalFailures; }

        public int getHealAttempts() { return healAttempts; }
        public void setHealAttempts(int healAttempts) { this.healAttempts = healAttempts; }

        public int getHealSuccesses() { return healSuccesses; }
        public void setHealSuccesses(int healSuccesses) { this.healSuccesses = healSuccesses; }

        public int getHealRefusals() { return healRefusals; }
        public void setHealRefusals(int healRefusals) { this.healRefusals = healRefusals; }

        public int getHealFailures() { return healFailures; }
        public void setHealFailures(int healFailures) { this.healFailures = healFailures; }

        public double getTotalLlmCostUsd() { return totalLlmCostUsd; }
        public void setTotalLlmCostUsd(double totalLlmCostUsd) { this.totalLlmCostUsd = totalLlmCostUsd; }
    }
}
