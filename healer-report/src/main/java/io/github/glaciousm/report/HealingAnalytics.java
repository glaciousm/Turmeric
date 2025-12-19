package io.github.glaciousm.report;

import io.github.glaciousm.report.model.HealEvent;
import io.github.glaciousm.report.model.HealReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides healing analytics and metrics from heal reports.
 * Calculates trends, identifies patterns, and estimates time savings.
 */
public class HealingAnalytics {

    private static final Logger logger = LoggerFactory.getLogger(HealingAnalytics.class);

    /**
     * Average time (in minutes) to manually fix a broken locator.
     * Based on industry estimates: find element, update locator, test, commit.
     */
    private static final double AVG_MANUAL_FIX_MINUTES = 15.0;

    /**
     * Analytics summary for a set of heal events.
     */
    public record AnalyticsSummary(
            int totalHeals,
            int successfulHeals,
            int failedHeals,
            int refusedHeals,
            double successRate,
            double averageConfidence,
            double totalCostUsd,
            double averageCostPerHeal,
            Duration estimatedTimeSaved,
            List<FrequentLocator> mostFrequentlyHealedLocators,
            List<TrendDataPoint> successRateTrend,
            Map<String, Integer> healsByFeature,
            Map<String, Integer> healsByActionType,
            Map<String, Double> confidenceDistribution
    ) {
        /**
         * Returns the estimated cost savings in USD, assuming developer cost per hour.
         */
        public double getEstimatedCostSavings(double developerHourlyRate) {
            double hoursSaved = estimatedTimeSaved.toMinutes() / 60.0;
            return hoursSaved * developerHourlyRate - totalCostUsd;
        }

        /**
         * Returns ROI percentage (savings / cost * 100).
         */
        public double getROI(double developerHourlyRate) {
            if (totalCostUsd == 0) return 0;
            double savings = getEstimatedCostSavings(developerHourlyRate);
            return (savings / totalCostUsd) * 100;
        }
    }

    /**
     * Represents a frequently healed locator.
     */
    public record FrequentLocator(
            String locator,
            int healCount,
            double averageConfidence,
            List<String> features
    ) {}

    /**
     * Represents a data point in a trend chart.
     */
    public record TrendDataPoint(
            Instant timestamp,
            double successRate,
            int totalHeals,
            int successfulHeals
    ) {}

    /**
     * Analyzes a single report and generates analytics summary.
     */
    public AnalyticsSummary analyzeReport(HealReport report) {
        if (report == null || report.getEvents() == null || report.getEvents().isEmpty()) {
            return createEmptySummary();
        }

        return analyzeEvents(report.getEvents());
    }

    /**
     * Analyzes multiple reports and generates combined analytics.
     */
    public AnalyticsSummary analyzeReports(List<HealReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return createEmptySummary();
        }

        List<HealEvent> allEvents = reports.stream()
                .filter(r -> r != null && r.getEvents() != null)
                .flatMap(r -> r.getEvents().stream())
                .collect(Collectors.toList());

        AnalyticsSummary summary = analyzeEvents(allEvents);

        // Generate trend from multiple reports
        List<TrendDataPoint> trend = generateTrendFromReports(reports);

        return new AnalyticsSummary(
                summary.totalHeals(),
                summary.successfulHeals(),
                summary.failedHeals(),
                summary.refusedHeals(),
                summary.successRate(),
                summary.averageConfidence(),
                summary.totalCostUsd(),
                summary.averageCostPerHeal(),
                summary.estimatedTimeSaved(),
                summary.mostFrequentlyHealedLocators(),
                trend,
                summary.healsByFeature(),
                summary.healsByActionType(),
                summary.confidenceDistribution()
        );
    }

    /**
     * Analyzes a list of heal events.
     */
    private AnalyticsSummary analyzeEvents(List<HealEvent> events) {
        int total = events.size();
        int successful = 0;
        int failed = 0;
        int refused = 0;
        double totalConfidence = 0;
        double totalCost = 0;

        Map<String, List<HealEvent>> locatorEvents = new HashMap<>();
        Map<String, Integer> healsByFeature = new HashMap<>();
        Map<String, Integer> healsByActionType = new HashMap<>();
        Map<String, Integer> confidenceBuckets = new LinkedHashMap<>();

        // Initialize confidence buckets
        confidenceBuckets.put("0-20%", 0);
        confidenceBuckets.put("20-40%", 0);
        confidenceBuckets.put("40-60%", 0);
        confidenceBuckets.put("60-80%", 0);
        confidenceBuckets.put("80-100%", 0);

        for (HealEvent event : events) {
            String status = event.getResult() != null ? event.getResult().getStatus() : "UNKNOWN";

            switch (status) {
                case "SUCCESS" -> successful++;
                case "FAILED" -> failed++;
                case "REFUSED" -> refused++;
            }

            // Confidence
            double confidence = event.getDecision() != null ? event.getDecision().getConfidence() : 0;
            totalConfidence += confidence;

            // Update confidence distribution
            String bucket = getConfidenceBucket(confidence);
            confidenceBuckets.merge(bucket, 1, Integer::sum);

            // Cost
            Double eventCost = event.getLlmCostUsd();
            if (eventCost != null) {
                totalCost += eventCost;
            }

            // Track locators
            String locator = event.getOriginalLocator();
            if (locator != null && !locator.isEmpty()) {
                locatorEvents.computeIfAbsent(locator, k -> new ArrayList<>()).add(event);
            }

            // Track by feature
            String feature = event.getFeature();
            if (feature != null && !feature.isEmpty()) {
                healsByFeature.merge(feature, 1, Integer::sum);
            }

            // Track by action type (inferred from step text)
            String actionType = inferActionType(event.getStep());
            healsByActionType.merge(actionType, 1, Integer::sum);
        }

        double successRate = total > 0 ? (successful * 100.0) / total : 0;
        double avgConfidence = total > 0 ? totalConfidence / total : 0;
        double avgCost = total > 0 ? totalCost / total : 0;

        // Calculate time saved (only count successful heals)
        Duration timeSaved = Duration.ofMinutes((long) (successful * AVG_MANUAL_FIX_MINUTES));

        // Get most frequently healed locators
        List<FrequentLocator> frequentLocators = getFrequentLocators(locatorEvents);

        // Convert confidence buckets to distribution
        Map<String, Double> confidenceDistribution = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : confidenceBuckets.entrySet()) {
            confidenceDistribution.put(entry.getKey(), total > 0 ? (entry.getValue() * 100.0) / total : 0);
        }

        return new AnalyticsSummary(
                total,
                successful,
                failed,
                refused,
                successRate,
                avgConfidence,
                totalCost,
                avgCost,
                timeSaved,
                frequentLocators,
                Collections.emptyList(), // Trend calculated separately for multiple reports
                healsByFeature,
                healsByActionType,
                confidenceDistribution
        );
    }

    private String getConfidenceBucket(double confidence) {
        if (confidence < 0.2) return "0-20%";
        if (confidence < 0.4) return "20-40%";
        if (confidence < 0.6) return "40-60%";
        if (confidence < 0.8) return "60-80%";
        return "80-100%";
    }

    private List<FrequentLocator> getFrequentLocators(Map<String, List<HealEvent>> locatorEvents) {
        return locatorEvents.entrySet().stream()
                .filter(e -> e.getValue().size() > 1) // Only include locators healed more than once
                .map(e -> {
                    List<HealEvent> events = e.getValue();
                    double avgConf = events.stream()
                            .filter(ev -> ev.getDecision() != null)
                            .mapToDouble(ev -> ev.getDecision().getConfidence())
                            .average()
                            .orElse(0);
                    List<String> features = events.stream()
                            .map(HealEvent::getFeature)
                            .filter(f -> f != null && !f.isEmpty())
                            .distinct()
                            .collect(Collectors.toList());
                    return new FrequentLocator(e.getKey(), events.size(), avgConf, features);
                })
                .sorted((a, b) -> Integer.compare(b.healCount(), a.healCount()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<TrendDataPoint> generateTrendFromReports(List<HealReport> reports) {
        return reports.stream()
                .filter(r -> r != null && r.getTimestamp() != null)
                .sorted(Comparator.comparing(HealReport::getTimestamp))
                .map(r -> {
                    List<HealEvent> events = r.getEvents() != null ? r.getEvents() : Collections.emptyList();
                    int total = events.size();
                    int successful = (int) events.stream()
                            .filter(e -> e.getResult() != null && "SUCCESS".equals(e.getResult().getStatus()))
                            .count();
                    double rate = total > 0 ? (successful * 100.0) / total : 0;
                    return new TrendDataPoint(r.getTimestamp(), rate, total, successful);
                })
                .collect(Collectors.toList());
    }

    private String inferActionType(String stepText) {
        if (stepText == null || stepText.isEmpty()) {
            return "Unknown";
        }

        String lower = stepText.toLowerCase();

        if (lower.contains("click") || lower.contains("press") || lower.contains("tap")) {
            return "Click";
        }
        if (lower.contains("type") || lower.contains("enter") || lower.contains("input") || lower.contains("fill")) {
            return "Input";
        }
        if (lower.contains("select") || lower.contains("choose") || lower.contains("pick")) {
            return "Select";
        }
        if (lower.contains("scroll")) {
            return "Scroll";
        }
        if (lower.contains("hover") || lower.contains("mouse over")) {
            return "Hover";
        }
        if (lower.contains("verify") || lower.contains("assert") || lower.contains("check") || lower.contains("see") || lower.contains("should")) {
            return "Assertion";
        }
        if (lower.contains("wait")) {
            return "Wait";
        }
        if (lower.contains("navigate") || lower.contains("go to") || lower.contains("open")) {
            return "Navigation";
        }

        return "Other";
    }

    private AnalyticsSummary createEmptySummary() {
        return new AnalyticsSummary(
                0, 0, 0, 0, 0, 0, 0, 0,
                Duration.ZERO,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    /**
     * Formats analytics summary as human-readable text.
     */
    public String formatAsText(AnalyticsSummary summary, double developerHourlyRate) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Healing Analytics Summary ===\n\n");

        sb.append("Overview:\n");
        sb.append(String.format("  Total Heals: %d%n", summary.totalHeals()));
        sb.append(String.format("  Successful: %d (%.1f%%)%n", summary.successfulHeals(), summary.successRate()));
        sb.append(String.format("  Failed: %d%n", summary.failedHeals()));
        sb.append(String.format("  Refused: %d%n", summary.refusedHeals()));
        sb.append("\n");

        sb.append("Confidence:\n");
        sb.append(String.format("  Average: %.1f%%%n", summary.averageConfidence() * 100));
        if (!summary.confidenceDistribution().isEmpty()) {
            sb.append("  Distribution:\n");
            for (Map.Entry<String, Double> entry : summary.confidenceDistribution().entrySet()) {
                sb.append(String.format("    %s: %.1f%%%n", entry.getKey(), entry.getValue()));
            }
        }
        sb.append("\n");

        sb.append("Cost:\n");
        sb.append(String.format("  Total LLM Cost: $%.4f%n", summary.totalCostUsd()));
        sb.append(String.format("  Average per Heal: $%.4f%n", summary.averageCostPerHeal()));
        sb.append("\n");

        sb.append("Time Savings:\n");
        long minutes = summary.estimatedTimeSaved().toMinutes();
        sb.append(String.format("  Estimated Time Saved: %d hours %d minutes%n", minutes / 60, minutes % 60));
        sb.append(String.format("  Estimated Cost Savings (at $%.0f/hr): $%.2f%n",
                developerHourlyRate, summary.getEstimatedCostSavings(developerHourlyRate)));
        sb.append(String.format("  ROI: %.0f%%%n", summary.getROI(developerHourlyRate)));
        sb.append("\n");

        if (!summary.mostFrequentlyHealedLocators().isEmpty()) {
            sb.append("Most Frequently Healed Locators:\n");
            for (FrequentLocator loc : summary.mostFrequentlyHealedLocators()) {
                sb.append(String.format("  - %s (%d times, %.0f%% avg confidence)%n",
                        truncate(loc.locator(), 50), loc.healCount(), loc.averageConfidence() * 100));
            }
            sb.append("\n");
        }

        if (!summary.healsByFeature().isEmpty()) {
            sb.append("Heals by Feature:\n");
            summary.healsByFeature().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> sb.append(String.format("  - %s: %d%n", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        if (!summary.healsByActionType().isEmpty()) {
            sb.append("Heals by Action Type:\n");
            summary.healsByActionType().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("  - %s: %d%n", e.getKey(), e.getValue())));
        }

        return sb.toString();
    }

    private String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
}
