package io.github.glaciousm.core.engine.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HealMetricsCollector")
class HealMetricsCollectorTest {

    private HealMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new HealMetricsCollector();
    }

    @Test
    @DisplayName("should track successful heals")
    void trackSuccessfulHeals() {
        HealMetrics metrics = createMetrics("SUCCESS");
        collector.record(metrics);

        HealMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertEquals(1, summary.totalAttempts());
        assertEquals(1, summary.successCount());
        assertEquals(0, summary.refusalCount());
        assertEquals(0, summary.failureCount());
        assertEquals(1.0, summary.successRate(), 0.01);
    }

    @Test
    @DisplayName("should track refused heals")
    void trackRefusedHeals() {
        HealMetrics metrics = createMetrics("REFUSED");
        collector.record(metrics);

        HealMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertEquals(1, summary.totalAttempts());
        assertEquals(0, summary.successCount());
        assertEquals(1, summary.refusalCount());
        assertEquals(0, summary.failureCount());
    }

    @Test
    @DisplayName("should track failed heals")
    void trackFailedHeals() {
        HealMetrics metrics = createMetrics("FAILED");
        collector.record(metrics);

        HealMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertEquals(1, summary.totalAttempts());
        assertEquals(0, summary.successCount());
        assertEquals(0, summary.refusalCount());
        assertEquals(1, summary.failureCount());
    }

    @Test
    @DisplayName("should calculate rates correctly")
    void calculateRatesCorrectly() {
        collector.record(createMetrics("SUCCESS"));
        collector.record(createMetrics("SUCCESS"));
        collector.record(createMetrics("REFUSED"));
        collector.record(createMetrics("FAILED"));

        HealMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertEquals(4, summary.totalAttempts());
        assertEquals(0.5, summary.successRate(), 0.01);
        assertEquals(0.25, summary.refusalRate(), 0.01);
        assertEquals(0.25, summary.failureRate(), 0.01);
    }

    @Test
    @DisplayName("should track cache hits")
    void trackCacheHits() {
        HealMetrics cached = createMetrics("SUCCESS");
        cached.setCacheHit(true);
        collector.record(cached);

        HealMetrics notCached = createMetrics("SUCCESS");
        notCached.setCacheHit(false);
        collector.record(notCached);

        assertEquals(0.5, collector.getCacheHitRate(), 0.01);
    }

    @Test
    @DisplayName("should track false heals")
    void trackFalseHeals() {
        HealMetrics falseHeal = createMetrics("SUCCESS");
        falseHeal.setOutcomeCheckPassed(false);
        collector.record(falseHeal);

        HealMetrics trueHeal = createMetrics("SUCCESS");
        trueHeal.setOutcomeCheckPassed(true);
        collector.record(trueHeal);

        assertEquals(0.5, collector.getFalseHealRate(), 0.01);
    }

    @Test
    @DisplayName("should track LLM costs")
    void trackLlmCosts() {
        HealMetrics metrics1 = createMetrics("SUCCESS");
        metrics1.setInputTokens(100);
        metrics1.setOutputTokens(50);
        metrics1.setLlmCostUsd(0.001);
        collector.record(metrics1);

        HealMetrics metrics2 = createMetrics("SUCCESS");
        metrics2.setInputTokens(200);
        metrics2.setOutputTokens(100);
        metrics2.setLlmCostUsd(0.002);
        collector.record(metrics2);

        HealMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertEquals(300, summary.totalInputTokens());
        assertEquals(150, summary.totalOutputTokens());
        assertEquals(0.003, summary.totalLlmCostUsd(), 0.0001);
    }

    @Test
    @DisplayName("should track per-failure-kind statistics")
    void trackPerFailureKindStats() {
        HealMetrics found = createMetrics("SUCCESS");
        found = new HealMetrics("feature", "scenario", "step", "ELEMENT_NOT_FOUND", "id:submit");
        found.complete("SUCCESS");
        collector.record(found);

        HealMetrics stale = new HealMetrics("feature", "scenario", "step", "STALE_ELEMENT", "id:other");
        stale.complete("FAILED");
        collector.record(stale);

        var elementNotFoundStats = collector.getStatsForFailureKind("ELEMENT_NOT_FOUND");
        assertTrue(elementNotFoundStats.isPresent());
        assertEquals(1, elementNotFoundStats.get().getAttempts());
        assertEquals(1, elementNotFoundStats.get().getSuccesses());
        assertEquals(1.0, elementNotFoundStats.get().getSuccessRate(), 0.01);

        var staleStats = collector.getStatsForFailureKind("STALE_ELEMENT");
        assertTrue(staleStats.isPresent());
        assertEquals(1, staleStats.get().getAttempts());
        assertEquals(0, staleStats.get().getSuccesses());
        assertEquals(0.0, staleStats.get().getSuccessRate(), 0.01);
    }

    @Test
    @DisplayName("should reset all metrics")
    void resetMetrics() {
        collector.record(createMetrics("SUCCESS"));
        collector.record(createMetrics("FAILED"));

        collector.reset();

        HealMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertEquals(0, summary.totalAttempts());
        assertEquals(0, summary.successCount());
        assertEquals(0, summary.failureCount());
    }

    private HealMetrics createMetrics(String result) {
        HealMetrics metrics = new HealMetrics(
                "Test Feature",
                "Test Scenario",
                "Test Step",
                "ELEMENT_NOT_FOUND",
                "id:test"
        );
        metrics.complete(result);
        return metrics;
    }
}
