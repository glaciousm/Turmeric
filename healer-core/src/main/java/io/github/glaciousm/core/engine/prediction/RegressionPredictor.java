package io.github.glaciousm.core.engine.prediction;

import io.github.glaciousm.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Predicts potential heal failures before they occur.
 *
 * Uses historical patterns to identify:
 * - Locators likely to fail soon
 * - Pages with high failure probability
 * - Time-based failure patterns (deployments, etc.)
 * - Confidence decay indicators
 */
public class RegressionPredictor {

    private static final Logger logger = LoggerFactory.getLogger(RegressionPredictor.class);

    private final Map<String, LocatorHistory> locatorHistories;
    private final Map<String, PageHistory> pageHistories;
    private final List<FailureEvent> recentFailures;
    private final PredictionConfig config;

    public RegressionPredictor() {
        this(PredictionConfig.defaults());
    }

    public RegressionPredictor(PredictionConfig config) {
        this.config = config;
        this.locatorHistories = new ConcurrentHashMap<>();
        this.pageHistories = new ConcurrentHashMap<>();
        this.recentFailures = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Record a heal event for learning.
     */
    public void recordHealEvent(HealEvent event) {
        String locatorKey = locatorKey(event.originalLocator());
        String pageKey = event.pageUrl();

        // Update locator history
        locatorHistories.computeIfAbsent(locatorKey, k -> new LocatorHistory(event.originalLocator()))
                .recordEvent(event);

        // Update page history
        pageHistories.computeIfAbsent(pageKey, k -> new PageHistory(pageKey))
                .recordEvent(event);

        // Track recent failures
        if (!event.success()) {
            recentFailures.add(new FailureEvent(event, Instant.now()));
            trimRecentFailures();
        }
    }

    /**
     * Predict risk for a specific locator.
     */
    public RiskPrediction predictLocatorRisk(LocatorInfo locator) {
        String key = locatorKey(locator);
        LocatorHistory history = locatorHistories.get(key);

        if (history == null) {
            return RiskPrediction.unknown(locator);
        }

        double riskScore = calculateLocatorRiskScore(history);
        RiskLevel level = classifyRisk(riskScore);
        List<String> factors = identifyRiskFactors(history);

        return new RiskPrediction(
                locator,
                riskScore,
                level,
                factors,
                history.getLastFailureTime(),
                history.getPredictedTimeToFailure()
        );
    }

    /**
     * Get all high-risk locators.
     */
    public List<RiskPrediction> getHighRiskLocators() {
        return locatorHistories.values().stream()
                .map(h -> predictLocatorRisk(h.getLocator()))
                .filter(p -> p.level() == RiskLevel.HIGH || p.level() == RiskLevel.CRITICAL)
                .sorted(Comparator.comparingDouble(RiskPrediction::riskScore).reversed())
                .limit(20)
                .toList();
    }

    /**
     * Predict risk for a page.
     */
    public PageRiskPrediction predictPageRisk(String pageUrl) {
        PageHistory history = pageHistories.get(pageUrl);

        if (history == null) {
            return PageRiskPrediction.unknown(pageUrl);
        }

        double riskScore = calculatePageRiskScore(history);
        RiskLevel level = classifyRisk(riskScore);
        List<String> riskyLocators = history.getMostFailedLocators(5);

        return new PageRiskPrediction(
                pageUrl,
                riskScore,
                level,
                riskyLocators,
                history.getTotalFailures(),
                history.getRecentFailureRate()
        );
    }

    /**
     * Detect deployment-related failure patterns.
     */
    public DeploymentPattern detectDeploymentPattern() {
        if (recentFailures.size() < 5) {
            return DeploymentPattern.none();
        }

        // Look for failure spikes
        Map<LocalDate, Long> failuresByDate = recentFailures.stream()
                .collect(Collectors.groupingBy(
                        f -> f.timestamp().atZone(ZoneId.systemDefault()).toLocalDate(),
                        Collectors.counting()
                ));

        // Calculate average and detect spikes
        double avgFailures = failuresByDate.values().stream()
                .mapToLong(Long::longValue)
                .average().orElse(0);

        List<LocalDate> spikesDates = failuresByDate.entrySet().stream()
                .filter(e -> e.getValue() > avgFailures * 2)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (spikesDates.isEmpty()) {
            return DeploymentPattern.none();
        }

        // Detect weekly pattern
        boolean weeklyPattern = detectWeeklyPattern(spikesDates);

        return new DeploymentPattern(
                true,
                spikesDates,
                weeklyPattern,
                avgFailures,
                estimateNextSpike(spikesDates, weeklyPattern)
        );
    }

    /**
     * Generate predictions report.
     */
    public PredictionReport generateReport() {
        List<RiskPrediction> highRiskLocators = getHighRiskLocators();

        List<PageRiskPrediction> highRiskPages = pageHistories.values().stream()
                .map(h -> predictPageRisk(h.getPageUrl()))
                .filter(p -> p.level() == RiskLevel.HIGH || p.level() == RiskLevel.CRITICAL)
                .sorted(Comparator.comparingDouble(PageRiskPrediction::riskScore).reversed())
                .limit(10)
                .toList();

        DeploymentPattern deploymentPattern = detectDeploymentPattern();

        // Calculate overall health
        double overallHealth = calculateOverallHealth(highRiskLocators, highRiskPages);

        return new PredictionReport(
                Instant.now(),
                overallHealth,
                highRiskLocators,
                highRiskPages,
                deploymentPattern,
                generateRecommendations(highRiskLocators, highRiskPages, deploymentPattern)
        );
    }

    /**
     * Check if a heal is likely to fail.
     */
    public HealPrediction predictHealOutcome(LocatorInfo original, LocatorInfo proposed, double confidence) {
        RiskPrediction originalRisk = predictLocatorRisk(original);
        String proposedKey = locatorKey(proposed);
        LocatorHistory proposedHistory = locatorHistories.get(proposedKey);

        // Factors that increase failure risk
        List<String> riskFactors = new ArrayList<>();
        double failureProbability = 0.0;

        // Low confidence
        if (confidence < config.confidenceThreshold()) {
            failureProbability += 0.3;
            riskFactors.add("Low confidence score: " + String.format("%.1f%%", confidence * 100));
        }

        // Original has high failure rate
        if (originalRisk.level() == RiskLevel.HIGH || originalRisk.level() == RiskLevel.CRITICAL) {
            failureProbability += 0.2;
            riskFactors.add("Original locator has high failure history");
        }

        // Proposed locator has failed before
        if (proposedHistory != null && proposedHistory.getFailureRate() > 0.3) {
            failureProbability += 0.3;
            riskFactors.add("Proposed locator has previous failures");
        }

        // Recent deployment spike
        DeploymentPattern deploymentPattern = detectDeploymentPattern();
        if (deploymentPattern.detected() && isNearSpike(deploymentPattern)) {
            failureProbability += 0.15;
            riskFactors.add("Recent deployment activity detected");
        }

        // Cap at 0.95
        failureProbability = Math.min(0.95, failureProbability);

        boolean shouldWarn = failureProbability > config.warningThreshold();

        return new HealPrediction(
                original,
                proposed,
                1.0 - failureProbability,
                failureProbability,
                shouldWarn,
                riskFactors
        );
    }

    // Private helper methods

    private double calculateLocatorRiskScore(LocatorHistory history) {
        double score = 0.0;

        // Failure rate factor
        score += history.getFailureRate() * 40;

        // Recent failure factor
        if (history.getLastFailureTime() != null) {
            long daysSinceFailure = Duration.between(history.getLastFailureTime(), Instant.now()).toDays();
            if (daysSinceFailure < 7) {
                score += (7 - daysSinceFailure) * 5;
            }
        }

        // Failure trend factor
        if (history.isFailureRateIncreasing()) {
            score += 20;
        }

        // Confidence decay factor
        if (history.getAverageConfidence() < 0.7) {
            score += (0.7 - history.getAverageConfidence()) * 30;
        }

        return Math.min(100, score);
    }

    private double calculatePageRiskScore(PageHistory history) {
        double score = 0.0;

        // Unique failed locators
        score += Math.min(30, history.getUniqueFailedLocators() * 3);

        // Recent failure rate
        score += history.getRecentFailureRate() * 40;

        // Total failures
        score += Math.min(20, history.getTotalFailures() * 0.5);

        return Math.min(100, score);
    }

    private RiskLevel classifyRisk(double score) {
        if (score >= 80) return RiskLevel.CRITICAL;
        if (score >= 60) return RiskLevel.HIGH;
        if (score >= 40) return RiskLevel.MEDIUM;
        if (score >= 20) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }

    private List<String> identifyRiskFactors(LocatorHistory history) {
        List<String> factors = new ArrayList<>();

        if (history.getFailureRate() > 0.5) {
            factors.add("High failure rate: " + String.format("%.1f%%", history.getFailureRate() * 100));
        }

        if (history.getLastFailureTime() != null) {
            long daysSince = Duration.between(history.getLastFailureTime(), Instant.now()).toDays();
            if (daysSince < 3) {
                factors.add("Recent failure: " + daysSince + " days ago");
            }
        }

        if (history.isFailureRateIncreasing()) {
            factors.add("Failure rate is increasing");
        }

        if (history.getAverageConfidence() < 0.7) {
            factors.add("Low average confidence: " + String.format("%.1f%%", history.getAverageConfidence() * 100));
        }

        if (history.getTotalHeals() > 10) {
            factors.add("Frequently healed: " + history.getTotalHeals() + " times");
        }

        return factors;
    }

    private boolean detectWeeklyPattern(List<LocalDate> spikeDates) {
        if (spikeDates.size() < 2) return false;

        // Check if spikes occur on same day of week
        Map<Integer, Long> dayOfWeekCounts = spikeDates.stream()
                .collect(Collectors.groupingBy(d -> d.getDayOfWeek().getValue(), Collectors.counting()));

        return dayOfWeekCounts.values().stream().anyMatch(c -> c >= 2);
    }

    private LocalDate estimateNextSpike(List<LocalDate> spikeDates, boolean weeklyPattern) {
        if (spikeDates.isEmpty()) return null;

        LocalDate lastSpike = spikeDates.get(spikeDates.size() - 1);

        if (weeklyPattern) {
            return lastSpike.plusWeeks(1);
        }

        // Estimate based on average interval
        if (spikeDates.size() >= 2) {
            long avgDays = 0;
            for (int i = 1; i < spikeDates.size(); i++) {
                avgDays += Duration.between(
                        spikeDates.get(i - 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        spikeDates.get(i).atStartOfDay(ZoneId.systemDefault()).toInstant()
                ).toDays();
            }
            avgDays /= (spikeDates.size() - 1);
            return lastSpike.plusDays(avgDays);
        }

        return lastSpike.plusDays(7); // Default to week
    }

    private boolean isNearSpike(DeploymentPattern pattern) {
        if (pattern.estimatedNextSpike() == null) return false;

        LocalDate today = LocalDate.now();
        long daysToSpike = Duration.between(
                today.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                pattern.estimatedNextSpike().atStartOfDay(ZoneId.systemDefault()).toInstant()
        ).toDays();

        return Math.abs(daysToSpike) <= 2;
    }

    private double calculateOverallHealth(List<RiskPrediction> locatorRisks, List<PageRiskPrediction> pageRisks) {
        if (locatorRisks.isEmpty() && pageRisks.isEmpty()) {
            return 100.0;
        }

        double locatorHealth = 100 - (locatorRisks.size() * 5);
        double pageHealth = 100 - (pageRisks.size() * 10);

        return Math.max(0, (locatorHealth + pageHealth) / 2);
    }

    private List<String> generateRecommendations(List<RiskPrediction> locatorRisks,
                                                  List<PageRiskPrediction> pageRisks,
                                                  DeploymentPattern pattern) {
        List<String> recommendations = new ArrayList<>();

        if (!locatorRisks.isEmpty()) {
            recommendations.add("Review " + locatorRisks.size() + " high-risk locators for potential refactoring");
        }

        if (!pageRisks.isEmpty()) {
            recommendations.add("Focus testing on " + pageRisks.size() + " high-risk pages");
        }

        if (pattern.detected() && pattern.weeklyPattern()) {
            recommendations.add("Consider scheduling test runs after weekly deployments (" +
                    pattern.spikeDates().get(0).getDayOfWeek() + ")");
        }

        if (locatorRisks.stream().anyMatch(r -> r.riskFactors().contains("Low average confidence"))) {
            recommendations.add("Consider using more stable locator strategies (data-testid, etc.)");
        }

        return recommendations;
    }

    private void trimRecentFailures() {
        Instant threshold = Instant.now().minus(Duration.ofDays(30));
        recentFailures.removeIf(f -> f.timestamp().isBefore(threshold));
    }

    private String locatorKey(LocatorInfo locator) {
        return locator.getStrategy() + ":" + locator.getValue();
    }

    // Inner classes for history tracking

    private static class LocatorHistory {
        private final LocatorInfo locator;
        private int totalHeals = 0;
        private int totalFailures = 0;
        private double confidenceSum = 0;
        private Instant lastFailureTime;
        private final List<Double> recentFailureRates = new ArrayList<>();

        public LocatorHistory(LocatorInfo locator) {
            this.locator = locator;
        }

        public void recordEvent(HealEvent event) {
            totalHeals++;
            confidenceSum += event.confidence();
            if (!event.success()) {
                totalFailures++;
                lastFailureTime = Instant.now();
            }

            // Track failure rate over time
            recentFailureRates.add(getFailureRate());
            if (recentFailureRates.size() > 10) {
                recentFailureRates.remove(0);
            }
        }

        public LocatorInfo getLocator() { return locator; }
        public int getTotalHeals() { return totalHeals; }
        public Instant getLastFailureTime() { return lastFailureTime; }

        public double getFailureRate() {
            return totalHeals == 0 ? 0 : (double) totalFailures / totalHeals;
        }

        public double getAverageConfidence() {
            return totalHeals == 0 ? 0 : confidenceSum / totalHeals;
        }

        public boolean isFailureRateIncreasing() {
            if (recentFailureRates.size() < 3) return false;
            int size = recentFailureRates.size();
            double recent = recentFailureRates.subList(size - 3, size).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            double older = recentFailureRates.subList(0, Math.min(3, size - 3)).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            return recent > older * 1.2;
        }

        public Duration getPredictedTimeToFailure() {
            if (lastFailureTime == null || totalFailures < 2) return null;
            // Rough estimate based on failure frequency
            long avgDaysBetweenFailures = Duration.between(
                    locator.hashCode() > 0 ? Instant.now().minus(Duration.ofDays(30)) : Instant.now(),
                    Instant.now()
            ).toDays() / Math.max(1, totalFailures);
            return Duration.ofDays(avgDaysBetweenFailures);
        }
    }

    private static class PageHistory {
        private final String pageUrl;
        private int totalFailures = 0;
        private final Map<String, Integer> locatorFailures = new HashMap<>();
        private final List<Instant> recentFailureTimes = new ArrayList<>();

        public PageHistory(String pageUrl) {
            this.pageUrl = pageUrl;
        }

        public void recordEvent(HealEvent event) {
            if (!event.success()) {
                totalFailures++;
                String locatorKey = event.originalLocator().getStrategy() + ":" + event.originalLocator().getValue();
                locatorFailures.merge(locatorKey, 1, Integer::sum);
                recentFailureTimes.add(Instant.now());

                // Trim old entries
                Instant threshold = Instant.now().minus(Duration.ofDays(7));
                recentFailureTimes.removeIf(t -> t.isBefore(threshold));
            }
        }

        public String getPageUrl() { return pageUrl; }
        public int getTotalFailures() { return totalFailures; }
        public int getUniqueFailedLocators() { return locatorFailures.size(); }

        public double getRecentFailureRate() {
            // Failures per day in last week
            return recentFailureTimes.size() / 7.0;
        }

        public List<String> getMostFailedLocators(int limit) {
            return locatorFailures.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    // Records

    public record HealEvent(
            LocatorInfo originalLocator,
            LocatorInfo healedLocator,
            String pageUrl,
            double confidence,
            boolean success,
            Instant timestamp
    ) {}

    public record FailureEvent(HealEvent event, Instant timestamp) {}

    public record RiskPrediction(
            LocatorInfo locator,
            double riskScore,
            RiskLevel level,
            List<String> riskFactors,
            Instant lastFailure,
            Duration predictedTimeToFailure
    ) {
        public static RiskPrediction unknown(LocatorInfo locator) {
            return new RiskPrediction(locator, 0, RiskLevel.MINIMAL, List.of("No history"), null, null);
        }
    }

    public record PageRiskPrediction(
            String pageUrl,
            double riskScore,
            RiskLevel level,
            List<String> riskyLocators,
            int totalFailures,
            double recentFailureRate
    ) {
        public static PageRiskPrediction unknown(String pageUrl) {
            return new PageRiskPrediction(pageUrl, 0, RiskLevel.MINIMAL, List.of(), 0, 0);
        }
    }

    public record DeploymentPattern(
            boolean detected,
            List<LocalDate> spikeDates,
            boolean weeklyPattern,
            double avgFailuresPerDay,
            LocalDate estimatedNextSpike
    ) {
        public static DeploymentPattern none() {
            return new DeploymentPattern(false, List.of(), false, 0, null);
        }
    }

    public record HealPrediction(
            LocatorInfo originalLocator,
            LocatorInfo proposedLocator,
            double successProbability,
            double failureProbability,
            boolean shouldWarn,
            List<String> riskFactors
    ) {}

    public record PredictionReport(
            Instant generatedAt,
            double overallHealth,
            List<RiskPrediction> highRiskLocators,
            List<PageRiskPrediction> highRiskPages,
            DeploymentPattern deploymentPattern,
            List<String> recommendations
    ) {}

    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }

    public record PredictionConfig(
            double confidenceThreshold,
            double warningThreshold,
            int historyDays
    ) {
        public static PredictionConfig defaults() {
            return new PredictionConfig(0.75, 0.4, 30);
        }
    }
}
