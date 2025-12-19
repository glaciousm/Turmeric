package io.github.glaciousm.core.engine;

import io.github.glaciousm.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates stability scores for locators based on healing history.
 * Helps identify brittle locators that should be improved.
 */
public class LocatorStabilityScorer {

    private static final Logger logger = LoggerFactory.getLogger(LocatorStabilityScorer.class);

    // Scoring weights
    private static final double WEIGHT_HEAL_FREQUENCY = 0.35;
    private static final double WEIGHT_CONFIDENCE = 0.25;
    private static final double WEIGHT_SUCCESS_RATE = 0.20;
    private static final double WEIGHT_STRATEGY = 0.20;

    // Thresholds
    private static final int HIGH_HEAL_COUNT_THRESHOLD = 5;
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.75;

    /**
     * Calculate stability scores for a collection of locators based on their heal history.
     *
     * @param healHistory list of heal events with locator info
     * @return stability report with scores for each unique locator
     */
    public StabilityReport calculateStability(List<HealEvent> healHistory) {
        if (healHistory == null || healHistory.isEmpty()) {
            return new StabilityReport(Collections.emptyList(), new StabilitySummary(0, 0, 0, 0, 100.0));
        }

        // Group events by original locator
        Map<String, List<HealEvent>> locatorEvents = healHistory.stream()
                .filter(e -> e.originalLocator() != null && !e.originalLocator().isEmpty())
                .collect(Collectors.groupingBy(HealEvent::originalLocator));

        List<LocatorStability> stabilities = new ArrayList<>();

        for (Map.Entry<String, List<HealEvent>> entry : locatorEvents.entrySet()) {
            String locator = entry.getKey();
            List<HealEvent> events = entry.getValue();

            LocatorStability stability = scoreLocator(locator, events);
            stabilities.add(stability);
        }

        // Sort by score (lowest/worst first)
        stabilities.sort(Comparator.comparingDouble(LocatorStability::score));

        // Calculate summary
        StabilitySummary summary = calculateSummary(stabilities);

        return new StabilityReport(stabilities, summary);
    }

    /**
     * Score a single locator based on its heal events.
     */
    private LocatorStability scoreLocator(String locator, List<HealEvent> events) {
        int healCount = events.size();
        int successCount = (int) events.stream().filter(HealEvent::wasSuccessful).count();
        int failureCount = healCount - successCount;

        double avgConfidence = events.stream()
                .mapToDouble(HealEvent::confidence)
                .average()
                .orElse(0.0);

        // Calculate individual scores (0-100, higher is better/more stable)
        double frequencyScore = calculateFrequencyScore(healCount);
        double confidenceScore = calculateConfidenceScore(avgConfidence);
        double successRateScore = calculateSuccessRateScore(successCount, healCount);
        double strategyScore = calculateStrategyScore(locator);

        // Weighted total score
        double totalScore = (frequencyScore * WEIGHT_HEAL_FREQUENCY)
                + (confidenceScore * WEIGHT_CONFIDENCE)
                + (successRateScore * WEIGHT_SUCCESS_RATE)
                + (strategyScore * WEIGHT_STRATEGY);

        // Determine stability level
        StabilityLevel level = determineLevel(totalScore);

        // Generate recommendations
        List<String> recommendations = generateRecommendations(locator, healCount, avgConfidence, level);

        // Get most recent heal timestamp
        Instant lastHealed = events.stream()
                .map(HealEvent::timestamp)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        // Infer strategy from locator
        LocatorInfo.LocatorStrategy strategy = inferStrategy(locator);

        return new LocatorStability(
                locator,
                totalScore,
                level,
                healCount,
                successCount,
                failureCount,
                avgConfidence,
                lastHealed,
                strategy,
                recommendations
        );
    }

    /**
     * Score based on heal frequency (more heals = less stable).
     */
    private double calculateFrequencyScore(int healCount) {
        if (healCount == 0) return 100.0;
        if (healCount == 1) return 90.0;
        if (healCount == 2) return 75.0;
        if (healCount <= 4) return 50.0;
        if (healCount <= 7) return 25.0;
        return Math.max(0, 100 - (healCount * 10));
    }

    /**
     * Score based on average confidence (higher confidence = more stable).
     */
    private double calculateConfidenceScore(double avgConfidence) {
        return avgConfidence * 100;
    }

    /**
     * Score based on heal success rate.
     */
    private double calculateSuccessRateScore(int successes, int total) {
        if (total == 0) return 100.0;
        return (successes * 100.0) / total;
    }

    /**
     * Score based on locator strategy (some strategies are inherently more stable).
     */
    private double calculateStrategyScore(String locator) {
        LocatorInfo.LocatorStrategy strategy = inferStrategy(locator);

        return switch (strategy) {
            case ID -> 90.0;  // IDs are generally stable
            case NAME -> 85.0;
            case CSS -> {
                // CSS with data-testid or aria-label is very stable
                if (locator.contains("data-testid") || locator.contains("aria-label")) {
                    yield 95.0;
                }
                // CSS with classes can be less stable
                if (locator.contains(".") && !locator.contains("[")) {
                    yield 60.0;
                }
                yield 70.0;
            }
            case XPATH -> {
                // Absolute XPath is very unstable
                if (locator.startsWith("/html") || locator.contains("/div/div/div")) {
                    yield 20.0;
                }
                // XPath with position is unstable
                if (locator.contains("[position()") || locator.matches(".*\\[\\d+\\].*")) {
                    yield 30.0;
                }
                // XPath with text or attributes is more stable
                if (locator.contains("@") || locator.contains("text()")) {
                    yield 60.0;
                }
                yield 40.0;
            }
            case CLASS_NAME -> 50.0;  // Class names can change
            case LINK_TEXT, PARTIAL_LINK_TEXT -> 55.0;  // Text can change
            case TAG_NAME -> 30.0;  // Very unstable alone
        };
    }

    /**
     * Determine stability level from score.
     */
    private StabilityLevel determineLevel(double score) {
        if (score >= 90) return StabilityLevel.VERY_STABLE;
        if (score >= 75) return StabilityLevel.STABLE;
        if (score >= 50) return StabilityLevel.MODERATE;
        if (score >= 25) return StabilityLevel.UNSTABLE;
        return StabilityLevel.VERY_UNSTABLE;
    }

    /**
     * Generate recommendations for improving locator stability.
     */
    private List<String> generateRecommendations(String locator, int healCount,
                                                   double avgConfidence, StabilityLevel level) {
        List<String> recommendations = new ArrayList<>();

        if (level == StabilityLevel.VERY_STABLE) {
            return recommendations;
        }

        LocatorInfo.LocatorStrategy strategy = inferStrategy(locator);

        // High heal count recommendation
        if (healCount >= HIGH_HEAL_COUNT_THRESHOLD) {
            recommendations.add("This locator has been healed " + healCount +
                    " times. Consider adding a stable data-testid attribute.");
        }

        // Strategy-specific recommendations
        switch (strategy) {
            case XPATH -> {
                if (locator.startsWith("/html") || locator.contains("/div/div")) {
                    recommendations.add("Replace absolute XPath with relative XPath using unique attributes.");
                }
                if (locator.matches(".*\\[\\d+\\].*")) {
                    recommendations.add("Avoid positional XPath indices. Use unique identifiers instead.");
                }
                recommendations.add("Consider using CSS selector or adding data-testid attribute for better stability.");
            }
            case CLASS_NAME -> {
                recommendations.add("Class names can change with UI updates. Consider using data-testid instead.");
            }
            case CSS -> {
                if (!locator.contains("data-testid") && !locator.contains("aria-")) {
                    recommendations.add("Add data-testid or aria-label attribute for more stable selection.");
                }
                if (locator.split("\\.").length > 3) {
                    recommendations.add("CSS selector uses multiple classes. Simplify with a unique identifier.");
                }
            }
            case TAG_NAME -> {
                recommendations.add("Tag name alone is very brittle. Add unique attributes or use data-testid.");
            }
            default -> {
                // Generic recommendation
            }
        }

        // Low confidence recommendation
        if (avgConfidence < LOW_CONFIDENCE_THRESHOLD) {
            recommendations.add(String.format("Low average confidence (%.0f%%). " +
                    "Element may be ambiguous - ensure unique identifiers.", avgConfidence * 100));
        }

        return recommendations;
    }

    /**
     * Infer locator strategy from the locator string.
     */
    private LocatorInfo.LocatorStrategy inferStrategy(String locator) {
        if (locator == null || locator.isEmpty()) {
            return LocatorInfo.LocatorStrategy.CSS;
        }

        // Check for XPath patterns
        if (locator.startsWith("//") || locator.startsWith("(//") || locator.startsWith("/html")) {
            return LocatorInfo.LocatorStrategy.XPATH;
        }

        // Check for ID pattern (#id without spaces)
        if (locator.startsWith("#") && !locator.contains(" ") && !locator.contains("[")) {
            return LocatorInfo.LocatorStrategy.ID;
        }

        // Check for class name pattern (.class without spaces or other selectors)
        if (locator.startsWith(".") && !locator.contains(" ") &&
            !locator.contains("[") && !locator.contains("#")) {
            return LocatorInfo.LocatorStrategy.CLASS_NAME;
        }

        // Check for By.id, By.name patterns from Selenium toString
        if (locator.startsWith("By.id:")) {
            return LocatorInfo.LocatorStrategy.ID;
        }
        if (locator.startsWith("By.name:")) {
            return LocatorInfo.LocatorStrategy.NAME;
        }
        if (locator.startsWith("By.xpath:")) {
            return LocatorInfo.LocatorStrategy.XPATH;
        }
        if (locator.startsWith("By.cssSelector:") || locator.startsWith("By.css:")) {
            return LocatorInfo.LocatorStrategy.CSS;
        }
        if (locator.startsWith("By.className:")) {
            return LocatorInfo.LocatorStrategy.CLASS_NAME;
        }
        if (locator.startsWith("By.linkText:")) {
            return LocatorInfo.LocatorStrategy.LINK_TEXT;
        }
        if (locator.startsWith("By.partialLinkText:")) {
            return LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT;
        }
        if (locator.startsWith("By.tagName:")) {
            return LocatorInfo.LocatorStrategy.TAG_NAME;
        }

        // Default to CSS
        return LocatorInfo.LocatorStrategy.CSS;
    }

    /**
     * Calculate summary statistics.
     */
    private StabilitySummary calculateSummary(List<LocatorStability> stabilities) {
        if (stabilities.isEmpty()) {
            return new StabilitySummary(0, 0, 0, 0, 100.0);
        }

        int total = stabilities.size();
        int stable = 0;
        int moderate = 0;
        int unstable = 0;

        for (LocatorStability s : stabilities) {
            switch (s.level()) {
                case VERY_STABLE, STABLE -> stable++;
                case MODERATE -> moderate++;
                case UNSTABLE, VERY_UNSTABLE -> unstable++;
            }
        }

        double avgScore = stabilities.stream()
                .mapToDouble(LocatorStability::score)
                .average()
                .orElse(0.0);

        return new StabilitySummary(total, stable, moderate, unstable, avgScore);
    }

    /**
     * Heal event record for stability calculation.
     */
    public record HealEvent(
            String originalLocator,
            String healedLocator,
            double confidence,
            boolean wasSuccessful,
            Instant timestamp
    ) {}

    /**
     * Stability level enum.
     */
    public enum StabilityLevel {
        VERY_STABLE("Very Stable", "Rarely needs healing"),
        STABLE("Stable", "Occasionally healed"),
        MODERATE("Moderate", "Regular healing required"),
        UNSTABLE("Unstable", "Frequently healed"),
        VERY_UNSTABLE("Very Unstable", "Constantly breaking");

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
     * Stability information for a single locator.
     */
    public record LocatorStability(
            String locator,
            double score,
            StabilityLevel level,
            int healCount,
            int successCount,
            int failureCount,
            double averageConfidence,
            Instant lastHealed,
            LocatorInfo.LocatorStrategy strategy,
            List<String> recommendations
    ) {
        /**
         * Get a color code for UI display.
         */
        public String getColorCode() {
            return switch (level) {
                case VERY_STABLE -> "#4CAF50";  // Green
                case STABLE -> "#8BC34A";       // Light green
                case MODERATE -> "#FFC107";     // Yellow
                case UNSTABLE -> "#FF9800";     // Orange
                case VERY_UNSTABLE -> "#f44336"; // Red
            };
        }

        /**
         * Get the success rate percentage.
         */
        public double getSuccessRate() {
            return healCount > 0 ? (successCount * 100.0) / healCount : 100.0;
        }
    }

    /**
     * Summary statistics for all analyzed locators.
     */
    public record StabilitySummary(
            int totalLocators,
            int stableCount,
            int moderateCount,
            int unstableCount,
            double averageScore
    ) {
        /**
         * Get stability percentage (stable + moderate as percentage of total).
         */
        public double getStabilityPercentage() {
            return totalLocators > 0
                    ? ((stableCount + moderateCount) * 100.0) / totalLocators
                    : 100.0;
        }

        /**
         * Get overall health rating.
         */
        public String getHealthRating() {
            if (averageScore >= 80) return "Excellent";
            if (averageScore >= 60) return "Good";
            if (averageScore >= 40) return "Fair";
            if (averageScore >= 20) return "Poor";
            return "Critical";
        }
    }

    /**
     * Complete stability report.
     */
    public record StabilityReport(
            List<LocatorStability> locatorStabilities,
            StabilitySummary summary
    ) {
        /**
         * Get the most unstable locators (worst first).
         */
        public List<LocatorStability> getMostUnstable(int limit) {
            return locatorStabilities.stream()
                    .sorted(Comparator.comparingDouble(LocatorStability::score))
                    .limit(limit)
                    .toList();
        }

        /**
         * Get locators that need attention (UNSTABLE or VERY_UNSTABLE).
         */
        public List<LocatorStability> getLocatorsNeedingAttention() {
            return locatorStabilities.stream()
                    .filter(s -> s.level() == StabilityLevel.UNSTABLE
                            || s.level() == StabilityLevel.VERY_UNSTABLE)
                    .toList();
        }

        /**
         * Get all recommendations aggregated.
         */
        public List<String> getAllRecommendations() {
            return locatorStabilities.stream()
                    .flatMap(s -> s.recommendations().stream())
                    .distinct()
                    .toList();
        }
    }
}
