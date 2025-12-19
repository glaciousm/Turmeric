package io.github.glaciousm.core.engine.stability;

import io.github.glaciousm.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Scores locator stability based on heal history.
 *
 * Lower stability scores indicate locators that frequently break.
 * Used for:
 * - Prioritizing locator recommendations
 * - Identifying fragile page areas
 * - Suggesting locator improvements
 */
public class LocatorStabilityScorer {

    private static final Logger logger = LoggerFactory.getLogger(LocatorStabilityScorer.class);

    // Scoring configuration
    private static final double INITIAL_SCORE = 100.0;
    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 100.0;
    private static final double FAILURE_PENALTY = 15.0;
    private static final double SUCCESS_BONUS = 2.0;
    private static final double HEAL_PENALTY = 10.0;
    private static final double TIME_DECAY_FACTOR = 0.95; // Score decay per week

    private final Map<String, LocatorStats> locatorStats;
    private final Duration historyRetention;
    private final int minEventsForScore;

    public LocatorStabilityScorer() {
        this(Duration.ofDays(90), 5);
    }

    public LocatorStabilityScorer(Duration historyRetention, int minEventsForScore) {
        this.locatorStats = new ConcurrentHashMap<>();
        this.historyRetention = historyRetention;
        this.minEventsForScore = minEventsForScore;
    }

    /**
     * Get stability score for a locator (0-100).
     * Higher scores indicate more stable locators.
     */
    public double getScore(LocatorInfo locator) {
        String key = locatorKey(locator);
        LocatorStats stats = locatorStats.get(key);

        if (stats == null || stats.totalEvents() < minEventsForScore) {
            return INITIAL_SCORE; // Not enough data
        }

        return stats.calculateScore();
    }

    /**
     * Get stability classification.
     */
    public StabilityLevel getStabilityLevel(LocatorInfo locator) {
        double score = getScore(locator);

        if (score >= 90) return StabilityLevel.VERY_STABLE;
        if (score >= 75) return StabilityLevel.STABLE;
        if (score >= 50) return StabilityLevel.MODERATE;
        if (score >= 25) return StabilityLevel.UNSTABLE;
        return StabilityLevel.VERY_UNSTABLE;
    }

    /**
     * Record a successful element find (no healing needed).
     */
    public void recordSuccess(LocatorInfo locator) {
        String key = locatorKey(locator);
        locatorStats.computeIfAbsent(key, k -> new LocatorStats(locator))
                .recordSuccess();
        logger.debug("Recorded success for locator: {}", key);
    }

    /**
     * Record a failure (element not found, healing attempted).
     */
    public void recordFailure(LocatorInfo locator) {
        String key = locatorKey(locator);
        locatorStats.computeIfAbsent(key, k -> new LocatorStats(locator))
                .recordFailure();
        logger.debug("Recorded failure for locator: {}", key);
    }

    /**
     * Record that a locator was healed to a new locator.
     */
    public void recordHeal(LocatorInfo original, LocatorInfo healed) {
        String origKey = locatorKey(original);
        locatorStats.computeIfAbsent(origKey, k -> new LocatorStats(original))
                .recordHeal();

        // The healed locator starts with bonus stability
        String healedKey = locatorKey(healed);
        locatorStats.computeIfAbsent(healedKey, k -> new LocatorStats(healed))
                .recordSuccess(); // Healed locators start well

        logger.debug("Recorded heal: {} -> {}", origKey, healedKey);
    }

    /**
     * Get statistics for a locator.
     */
    public Optional<LocatorStats> getStats(LocatorInfo locator) {
        return Optional.ofNullable(locatorStats.get(locatorKey(locator)));
    }

    /**
     * Get all locators below a score threshold.
     */
    public List<LocatorInfo> getUnstableLocators(double threshold) {
        return locatorStats.values().stream()
                .filter(stats -> stats.totalEvents() >= minEventsForScore)
                .filter(stats -> stats.calculateScore() < threshold)
                .sorted(Comparator.comparingDouble(LocatorStats::calculateScore))
                .map(LocatorStats::getLocator)
                .collect(Collectors.toList());
    }

    /**
     * Get top N most unstable locators.
     */
    public List<StabilityReport> getMostUnstable(int limit) {
        return locatorStats.values().stream()
                .filter(stats -> stats.totalEvents() >= minEventsForScore)
                .sorted(Comparator.comparingDouble(LocatorStats::calculateScore))
                .limit(limit)
                .map(stats -> new StabilityReport(
                        stats.getLocator(),
                        stats.calculateScore(),
                        getStabilityLevel(stats.getLocator()),
                        stats.successCount,
                        stats.failureCount,
                        stats.healCount,
                        stats.lastEventTime
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get overall stability statistics.
     */
    public OverallStabilityStats getOverallStats() {
        int total = 0;
        int veryStable = 0;
        int stable = 0;
        int moderate = 0;
        int unstable = 0;
        int veryUnstable = 0;
        double totalScore = 0;

        for (LocatorStats stats : locatorStats.values()) {
            if (stats.totalEvents() < minEventsForScore) continue;

            total++;
            double score = stats.calculateScore();
            totalScore += score;

            StabilityLevel level = score >= 90 ? StabilityLevel.VERY_STABLE :
                                   score >= 75 ? StabilityLevel.STABLE :
                                   score >= 50 ? StabilityLevel.MODERATE :
                                   score >= 25 ? StabilityLevel.UNSTABLE :
                                   StabilityLevel.VERY_UNSTABLE;

            switch (level) {
                case VERY_STABLE -> veryStable++;
                case STABLE -> stable++;
                case MODERATE -> moderate++;
                case UNSTABLE -> unstable++;
                case VERY_UNSTABLE -> veryUnstable++;
            }
        }

        return new OverallStabilityStats(
                total,
                veryStable,
                stable,
                moderate,
                unstable,
                veryUnstable,
                total > 0 ? totalScore / total : INITIAL_SCORE
        );
    }

    /**
     * Cleanup old entries.
     */
    public int cleanup() {
        Instant threshold = Instant.now().minus(historyRetention);
        int removed = 0;

        Iterator<Map.Entry<String, LocatorStats>> it = locatorStats.entrySet().iterator();
        while (it.hasNext()) {
            LocatorStats stats = it.next().getValue();
            if (stats.lastEventTime.isBefore(threshold)) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            logger.info("Cleaned up {} old locator statistics", removed);
        }

        return removed;
    }

    /**
     * Apply time decay to all scores.
     */
    public void applyTimeDecay() {
        for (LocatorStats stats : locatorStats.values()) {
            stats.applyDecay();
        }
        logger.debug("Applied time decay to {} locator scores", locatorStats.size());
    }

    private String locatorKey(LocatorInfo locator) {
        return locator.getStrategy() + ":" + locator.getValue();
    }

    /**
     * Statistics for a single locator.
     */
    public static class LocatorStats {
        private final LocatorInfo locator;
        private int successCount;
        private int failureCount;
        private int healCount;
        private double accumulatedScore;
        private Instant lastEventTime;
        private Instant firstEventTime;

        public LocatorStats(LocatorInfo locator) {
            this.locator = locator;
            this.successCount = 0;
            this.failureCount = 0;
            this.healCount = 0;
            this.accumulatedScore = INITIAL_SCORE;
            this.lastEventTime = Instant.now();
            this.firstEventTime = Instant.now();
        }

        public synchronized void recordSuccess() {
            successCount++;
            accumulatedScore = Math.min(MAX_SCORE, accumulatedScore + SUCCESS_BONUS);
            lastEventTime = Instant.now();
        }

        public synchronized void recordFailure() {
            failureCount++;
            accumulatedScore = Math.max(MIN_SCORE, accumulatedScore - FAILURE_PENALTY);
            lastEventTime = Instant.now();
        }

        public synchronized void recordHeal() {
            healCount++;
            accumulatedScore = Math.max(MIN_SCORE, accumulatedScore - HEAL_PENALTY);
            lastEventTime = Instant.now();
        }

        public synchronized void applyDecay() {
            long weeksInactive = Duration.between(lastEventTime, Instant.now()).toDays() / 7;
            if (weeksInactive > 0) {
                accumulatedScore = accumulatedScore * Math.pow(TIME_DECAY_FACTOR, weeksInactive);
            }
        }

        public synchronized double calculateScore() {
            // Base score from accumulated history
            double score = accumulatedScore;

            // Additional factors
            int total = totalEvents();
            if (total > 0) {
                // Success ratio bonus/penalty
                double successRatio = (double) successCount / total;
                score = score * (0.5 + successRatio * 0.5);

                // Recent failure penalty
                if (failureCount > 0 && Duration.between(lastEventTime, Instant.now()).toDays() < 7) {
                    score = score * 0.9;
                }
            }

            return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
        }

        public int totalEvents() {
            return successCount + failureCount;
        }

        public LocatorInfo getLocator() { return locator; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public int getHealCount() { return healCount; }
        public Instant getLastEventTime() { return lastEventTime; }
        public Instant getFirstEventTime() { return firstEventTime; }
    }

    /**
     * Stability level classification.
     */
    public enum StabilityLevel {
        VERY_STABLE("Very Stable", "Locator rarely needs healing"),
        STABLE("Stable", "Locator occasionally needs healing"),
        MODERATE("Moderate", "Locator sometimes needs healing"),
        UNSTABLE("Unstable", "Locator frequently needs healing"),
        VERY_UNSTABLE("Very Unstable", "Locator constantly needs healing");

        private final String displayName;
        private final String description;

        StabilityLevel(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Stability report for a single locator.
     */
    public record StabilityReport(
            LocatorInfo locator,
            double score,
            StabilityLevel level,
            int successes,
            int failures,
            int heals,
            Instant lastEvent
    ) {}

    /**
     * Overall stability statistics.
     */
    public record OverallStabilityStats(
            int totalLocators,
            int veryStable,
            int stable,
            int moderate,
            int unstable,
            int veryUnstable,
            double averageScore
    ) {
        public double getStabilityPercentage() {
            if (totalLocators == 0) return 100.0;
            return (double) (veryStable + stable) / totalLocators * 100.0;
        }
    }
}
