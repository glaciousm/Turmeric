package io.github.glaciousm.core.engine.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Collects and aggregates metrics about healing operations.
 *
 * Tracks:
 * - Success/failure/refusal rates
 * - Latency (P50, P90, P99)
 * - LLM costs
 * - Cache hit rates
 * - False heal rates
 * - Per-failure-kind statistics
 */
public class HealMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(HealMetricsCollector.class);

    // Counters
    private final AtomicInteger totalAttempts = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger refusalCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger falseHealCount = new AtomicInteger(0);

    // Token and cost tracking
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    private final DoubleAdder totalLlmCostUsd = new DoubleAdder();

    // Latency tracking (in milliseconds)
    private final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

    // Per-failure-kind statistics
    private final Map<String, FailureKindStats> failureKindStats = new ConcurrentHashMap<>();

    // Recent metrics for windowed calculations
    private final Deque<HealMetrics> recentMetrics = new LinkedList<>();
    private static final int MAX_RECENT_METRICS = 1000;

    // Session tracking
    private final Instant sessionStart = Instant.now();
    private volatile Instant lastHealTime;

    public HealMetricsCollector() {
    }

    /**
     * Record a completed heal attempt.
     */
    public void record(HealMetrics metrics) {
        totalAttempts.incrementAndGet();
        lastHealTime = Instant.now();

        // Record result
        switch (metrics.getResult()) {
            case "SUCCESS" -> {
                successCount.incrementAndGet();
                if (metrics.isFalseHeal()) {
                    falseHealCount.incrementAndGet();
                }
            }
            case "REFUSED" -> refusalCount.incrementAndGet();
            case "FAILED" -> failureCount.incrementAndGet();
        }

        // Cache hits
        if (metrics.isCacheHit()) {
            cacheHits.incrementAndGet();
        }

        // Token and cost tracking
        totalInputTokens.addAndGet(metrics.getInputTokens());
        totalOutputTokens.addAndGet(metrics.getOutputTokens());
        totalLlmCostUsd.add(metrics.getLlmCostUsd());

        // Latency tracking
        latencies.add(metrics.getDurationMs());

        // Per-failure-kind stats
        if (metrics.getFailureKind() != null) {
            failureKindStats.computeIfAbsent(metrics.getFailureKind(), k -> new FailureKindStats())
                    .record(metrics);
        }

        // Keep recent metrics for windowed calculations
        synchronized (recentMetrics) {
            recentMetrics.addLast(metrics);
            while (recentMetrics.size() > MAX_RECENT_METRICS) {
                recentMetrics.removeFirst();
            }
        }

        logger.debug("Recorded metrics: result={}, duration={}ms, cacheHit={}",
                metrics.getResult(), metrics.getDurationMs(), metrics.isCacheHit());
    }

    /**
     * Mark a previously successful heal as a false heal.
     */
    public void recordFalseHeal() {
        falseHealCount.incrementAndGet();
        logger.warn("False heal recorded");
    }

    /**
     * Get the overall success rate.
     */
    public double getSuccessRate() {
        int total = totalAttempts.get();
        return total > 0 ? (double) successCount.get() / total : 0.0;
    }

    /**
     * Get the refusal rate.
     */
    public double getRefusalRate() {
        int total = totalAttempts.get();
        return total > 0 ? (double) refusalCount.get() / total : 0.0;
    }

    /**
     * Get the failure rate.
     */
    public double getFailureRate() {
        int total = totalAttempts.get();
        return total > 0 ? (double) failureCount.get() / total : 0.0;
    }

    /**
     * Get the false heal rate (among successes).
     */
    public double getFalseHealRate() {
        int successes = successCount.get();
        return successes > 0 ? (double) falseHealCount.get() / successes : 0.0;
    }

    /**
     * Get the cache hit rate.
     */
    public double getCacheHitRate() {
        int total = totalAttempts.get();
        return total > 0 ? (double) cacheHits.get() / total : 0.0;
    }

    /**
     * Get P50 (median) latency in milliseconds.
     */
    public long getP50Latency() {
        return getPercentileLatency(50);
    }

    /**
     * Get P90 latency in milliseconds.
     */
    public long getP90Latency() {
        return getPercentileLatency(90);
    }

    /**
     * Get P99 latency in milliseconds.
     */
    public long getP99Latency() {
        return getPercentileLatency(99);
    }

    /**
     * Get a specific percentile latency.
     */
    public long getPercentileLatency(int percentile) {
        List<Long> sorted;
        synchronized (latencies) {
            if (latencies.isEmpty()) {
                return 0;
            }
            sorted = new ArrayList<>(latencies);
        }
        Collections.sort(sorted);

        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    /**
     * Get average latency in milliseconds.
     */
    public double getAverageLatency() {
        synchronized (latencies) {
            if (latencies.isEmpty()) {
                return 0;
            }
            return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }

    /**
     * Get total LLM cost in USD.
     */
    public double getTotalLlmCostUsd() {
        return totalLlmCostUsd.sum();
    }

    /**
     * Get total input tokens.
     */
    public long getTotalInputTokens() {
        return totalInputTokens.get();
    }

    /**
     * Get total output tokens.
     */
    public long getTotalOutputTokens() {
        return totalOutputTokens.get();
    }

    /**
     * Get statistics for a specific failure kind.
     */
    public Optional<FailureKindStats> getStatsForFailureKind(String failureKind) {
        return Optional.ofNullable(failureKindStats.get(failureKind));
    }

    /**
     * Get all failure kind statistics.
     */
    public Map<String, FailureKindStats> getAllFailureKindStats() {
        return Collections.unmodifiableMap(failureKindStats);
    }

    /**
     * Get an aggregated summary of all metrics.
     */
    public MetricsSummary getSummary() {
        return new MetricsSummary(
                totalAttempts.get(),
                successCount.get(),
                refusalCount.get(),
                failureCount.get(),
                falseHealCount.get(),
                cacheHits.get(),
                getSuccessRate(),
                getRefusalRate(),
                getFailureRate(),
                getFalseHealRate(),
                getCacheHitRate(),
                getAverageLatency(),
                getP50Latency(),
                getP90Latency(),
                getP99Latency(),
                totalInputTokens.get(),
                totalOutputTokens.get(),
                getTotalLlmCostUsd(),
                Duration.between(sessionStart, Instant.now())
        );
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        totalAttempts.set(0);
        successCount.set(0);
        refusalCount.set(0);
        failureCount.set(0);
        cacheHits.set(0);
        falseHealCount.set(0);
        totalInputTokens.set(0);
        totalOutputTokens.set(0);
        synchronized (latencies) {
            latencies.clear();
        }
        failureKindStats.clear();
        synchronized (recentMetrics) {
            recentMetrics.clear();
        }
        logger.info("Metrics reset");
    }

    /**
     * Statistics for a specific failure kind.
     */
    public static class FailureKindStats {
        private final AtomicInteger attempts = new AtomicInteger(0);
        private final AtomicInteger successes = new AtomicInteger(0);
        private final AtomicInteger refusals = new AtomicInteger(0);
        private final AtomicInteger failures = new AtomicInteger(0);

        void record(HealMetrics metrics) {
            attempts.incrementAndGet();
            switch (metrics.getResult()) {
                case "SUCCESS" -> successes.incrementAndGet();
                case "REFUSED" -> refusals.incrementAndGet();
                case "FAILED" -> failures.incrementAndGet();
            }
        }

        public int getAttempts() { return attempts.get(); }
        public int getSuccesses() { return successes.get(); }
        public int getRefusals() { return refusals.get(); }
        public int getFailures() { return failures.get(); }

        public double getSuccessRate() {
            int total = attempts.get();
            return total > 0 ? (double) successes.get() / total : 0.0;
        }
    }

    /**
     * Aggregated metrics summary.
     */
    public record MetricsSummary(
            int totalAttempts,
            int successCount,
            int refusalCount,
            int failureCount,
            int falseHealCount,
            int cacheHits,
            double successRate,
            double refusalRate,
            double failureRate,
            double falseHealRate,
            double cacheHitRate,
            double avgLatencyMs,
            long p50LatencyMs,
            long p90LatencyMs,
            long p99LatencyMs,
            long totalInputTokens,
            long totalOutputTokens,
            double totalLlmCostUsd,
            Duration sessionDuration
    ) {
        @Override
        public String toString() {
            return String.format("""
                    Metrics Summary:
                      Total Attempts: %d (Success: %d, Refused: %d, Failed: %d)
                      Success Rate: %.1f%%, False Heal Rate: %.1f%%
                      Cache Hit Rate: %.1f%%
                      Latency: avg=%.0fms, P50=%dms, P90=%dms, P99=%dms
                      LLM Cost: $%.4f (input: %d, output: %d tokens)
                      Session Duration: %s
                    """,
                    totalAttempts, successCount, refusalCount, failureCount,
                    successRate * 100, falseHealRate * 100,
                    cacheHitRate * 100,
                    avgLatencyMs, p50LatencyMs, p90LatencyMs, p99LatencyMs,
                    totalLlmCostUsd, totalInputTokens, totalOutputTokens,
                    formatDuration(sessionDuration)
            );
        }

        private String formatDuration(Duration duration) {
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            if (hours > 0) {
                return String.format("%dh %dm %ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        }
    }
}
