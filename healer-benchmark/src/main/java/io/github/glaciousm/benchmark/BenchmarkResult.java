/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Holds the result of a single benchmark scenario execution.
 */
public class BenchmarkResult {

    private final String scenarioId;
    private final String scenarioName;
    private final String category;
    private final ExpectedOutcome expectedOutcome;
    private final ActualOutcome actualOutcome;
    private final boolean passed;
    private final double confidence;
    private final Duration latency;
    private final String originalLocator;
    private final String healedLocator;
    private final String reasoning;
    private final String errorMessage;
    private final Instant timestamp;
    private final String llmProvider;
    private final String llmModel;
    private final double costUsd;

    private BenchmarkResult(Builder builder) {
        this.scenarioId = builder.scenarioId;
        this.scenarioName = builder.scenarioName;
        this.category = builder.category;
        this.expectedOutcome = builder.expectedOutcome;
        this.actualOutcome = builder.actualOutcome;
        this.passed = builder.passed;
        this.confidence = builder.confidence;
        this.latency = builder.latency;
        this.originalLocator = builder.originalLocator;
        this.healedLocator = builder.healedLocator;
        this.reasoning = builder.reasoning;
        this.errorMessage = builder.errorMessage;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.llmProvider = builder.llmProvider;
        this.llmModel = builder.llmModel;
        this.costUsd = builder.costUsd;
    }

    // Getters
    public String getScenarioId() { return scenarioId; }
    public String getScenarioName() { return scenarioName; }
    public String getCategory() { return category; }
    public ExpectedOutcome getExpectedOutcome() { return expectedOutcome; }
    public ActualOutcome getActualOutcome() { return actualOutcome; }
    public boolean isPassed() { return passed; }
    public double getConfidence() { return confidence; }
    public Duration getLatency() { return latency; }
    public String getOriginalLocator() { return originalLocator; }
    public String getHealedLocator() { return healedLocator; }
    public String getReasoning() { return reasoning; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getTimestamp() { return timestamp; }
    public String getLlmProvider() { return llmProvider; }
    public String getLlmModel() { return llmModel; }
    public double getCostUsd() { return costUsd; }

    /**
     * Expected outcome for a benchmark scenario.
     */
    public enum ExpectedOutcome {
        /** Element should be successfully healed */
        HEAL,
        /** Healing should be refused (element removed, ambiguous, forbidden, etc.) */
        REFUSE,
        /** Healing should return low confidence */
        LOW_CONFIDENCE,
        /** System should detect this as a false heal */
        DETECT_FALSE_HEAL
    }

    /**
     * Actual outcome from running the scenario.
     */
    public enum ActualOutcome {
        /** Successfully healed to the correct element */
        HEALED_CORRECT,
        /** Healed but to the wrong element (false heal) */
        HEALED_WRONG,
        /** Correctly refused to heal */
        REFUSED,
        /** Returned low confidence as expected */
        LOW_CONFIDENCE,
        /** Failed with an error */
        ERROR,
        /** Timeout during healing */
        TIMEOUT
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String scenarioId;
        private String scenarioName;
        private String category;
        private ExpectedOutcome expectedOutcome;
        private ActualOutcome actualOutcome;
        private boolean passed;
        private double confidence;
        private Duration latency;
        private String originalLocator;
        private String healedLocator;
        private String reasoning;
        private String errorMessage;
        private Instant timestamp;
        private String llmProvider;
        private String llmModel;
        private double costUsd;

        public Builder scenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
            return this;
        }

        public Builder scenarioName(String scenarioName) {
            this.scenarioName = scenarioName;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder expectedOutcome(ExpectedOutcome expectedOutcome) {
            this.expectedOutcome = expectedOutcome;
            return this;
        }

        public Builder actualOutcome(ActualOutcome actualOutcome) {
            this.actualOutcome = actualOutcome;
            return this;
        }

        public Builder passed(boolean passed) {
            this.passed = passed;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder latency(Duration latency) {
            this.latency = latency;
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

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder llmProvider(String llmProvider) {
            this.llmProvider = llmProvider;
            return this;
        }

        public Builder llmModel(String llmModel) {
            this.llmModel = llmModel;
            return this;
        }

        public Builder costUsd(double costUsd) {
            this.costUsd = costUsd;
            return this;
        }

        public BenchmarkResult build() {
            return new BenchmarkResult(this);
        }
    }

    /**
     * Aggregated statistics from multiple benchmark runs.
     */
    public static class BenchmarkSummary {
        private final int totalScenarios;
        private final int passed;
        private final int failed;
        private final int healsAttempted;
        private final int healsCorrect;
        private final int healsFalse;
        private final int refusalsCorrect;
        private final int refusalsIncorrect;
        private final Duration totalLatency;
        private final Duration p50Latency;
        private final Duration p90Latency;
        private final Duration p99Latency;
        private final double totalCostUsd;
        private final String llmProvider;
        private final String llmModel;
        private final Instant runTimestamp;
        private final Map<String, CategoryStats> categoryStats;

        public BenchmarkSummary(List<BenchmarkResult> results, String llmProvider, String llmModel) {
            this.llmProvider = llmProvider;
            this.llmModel = llmModel;
            this.runTimestamp = Instant.now();
            this.categoryStats = new HashMap<>();

            this.totalScenarios = results.size();
            this.passed = (int) results.stream().filter(BenchmarkResult::isPassed).count();
            this.failed = totalScenarios - passed;

            this.healsAttempted = (int) results.stream()
                .filter(r -> r.getActualOutcome() == ActualOutcome.HEALED_CORRECT
                          || r.getActualOutcome() == ActualOutcome.HEALED_WRONG)
                .count();

            this.healsCorrect = (int) results.stream()
                .filter(r -> r.getActualOutcome() == ActualOutcome.HEALED_CORRECT)
                .count();

            this.healsFalse = (int) results.stream()
                .filter(r -> r.getActualOutcome() == ActualOutcome.HEALED_WRONG)
                .count();

            this.refusalsCorrect = (int) results.stream()
                .filter(r -> r.getExpectedOutcome() == ExpectedOutcome.REFUSE
                          && r.getActualOutcome() == ActualOutcome.REFUSED)
                .count();

            this.refusalsIncorrect = (int) results.stream()
                .filter(r -> r.getExpectedOutcome() == ExpectedOutcome.REFUSE
                          && r.getActualOutcome() != ActualOutcome.REFUSED)
                .count();

            // Calculate latency percentiles
            List<Long> latencies = results.stream()
                .filter(r -> r.getLatency() != null)
                .map(r -> r.getLatency().toMillis())
                .sorted()
                .toList();

            this.totalLatency = Duration.ofMillis(latencies.stream().mapToLong(Long::longValue).sum());
            this.p50Latency = percentile(latencies, 50);
            this.p90Latency = percentile(latencies, 90);
            this.p99Latency = percentile(latencies, 99);

            this.totalCostUsd = results.stream().mapToDouble(BenchmarkResult::getCostUsd).sum();

            // Calculate per-category stats
            results.stream()
                .collect(java.util.stream.Collectors.groupingBy(BenchmarkResult::getCategory))
                .forEach((category, categoryResults) -> {
                    categoryStats.put(category, new CategoryStats(categoryResults));
                });
        }

        private Duration percentile(List<Long> sorted, int percentile) {
            if (sorted.isEmpty()) return Duration.ZERO;
            int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            return Duration.ofMillis(sorted.get(Math.max(0, index)));
        }

        // Getters
        public int getTotalScenarios() { return totalScenarios; }
        public int getPassed() { return passed; }
        public int getFailed() { return failed; }
        public int getHealsAttempted() { return healsAttempted; }
        public int getHealsCorrect() { return healsCorrect; }
        public int getHealsFalse() { return healsFalse; }
        public int getRefusalsCorrect() { return refusalsCorrect; }
        public int getRefusalsIncorrect() { return refusalsIncorrect; }
        public Duration getTotalLatency() { return totalLatency; }
        public Duration getP50Latency() { return p50Latency; }
        public Duration getP90Latency() { return p90Latency; }
        public Duration getP99Latency() { return p99Latency; }
        public double getTotalCostUsd() { return totalCostUsd; }
        public String getLlmProvider() { return llmProvider; }
        public String getLlmModel() { return llmModel; }
        public Instant getRunTimestamp() { return runTimestamp; }
        public Map<String, CategoryStats> getCategoryStats() { return categoryStats; }

        public double getHealSuccessRate() {
            return healsAttempted > 0 ? (double) healsCorrect / healsAttempted * 100 : 0;
        }

        public double getFalseHealRate() {
            return healsAttempted > 0 ? (double) healsFalse / healsAttempted * 100 : 0;
        }

        public double getRefusalAccuracy() {
            int totalRefusalScenarios = refusalsCorrect + refusalsIncorrect;
            return totalRefusalScenarios > 0 ? (double) refusalsCorrect / totalRefusalScenarios * 100 : 0;
        }

        public double getOverallPassRate() {
            return totalScenarios > 0 ? (double) passed / totalScenarios * 100 : 0;
        }

        public double getAverageCostPerHeal() {
            return healsAttempted > 0 ? totalCostUsd / healsAttempted : 0;
        }
    }

    /**
     * Statistics for a specific category of benchmarks.
     */
    public static class CategoryStats {
        private final int total;
        private final int passed;
        private final int failed;

        public CategoryStats(List<BenchmarkResult> results) {
            this.total = results.size();
            this.passed = (int) results.stream().filter(BenchmarkResult::isPassed).count();
            this.failed = total - passed;
        }

        public int getTotal() { return total; }
        public int getPassed() { return passed; }
        public int getFailed() { return failed; }
        public double getPassRate() {
            return total > 0 ? (double) passed / total * 100 : 0;
        }
    }
}
