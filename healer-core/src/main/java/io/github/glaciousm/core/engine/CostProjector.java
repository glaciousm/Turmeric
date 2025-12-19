package io.github.glaciousm.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Projects LLM costs and provides budgeting features for healing operations.
 * Helps teams plan and control their healing-related expenses.
 */
public class CostProjector {

    private static final Logger logger = LoggerFactory.getLogger(CostProjector.class);

    // Default cost assumptions
    private static final double DEFAULT_DEVELOPER_HOURLY_RATE = 75.0;
    private static final double DEFAULT_AVG_MANUAL_FIX_MINUTES = 15.0;

    // Model pricing (per 1K tokens, approximate as of late 2024)
    private static final Map<String, TokenPricing> MODEL_PRICING = Map.of(
            "gpt-4", new TokenPricing(0.03, 0.06),
            "gpt-4-turbo", new TokenPricing(0.01, 0.03),
            "gpt-3.5-turbo", new TokenPricing(0.0005, 0.0015),
            "claude-3-opus", new TokenPricing(0.015, 0.075),
            "claude-3-sonnet", new TokenPricing(0.003, 0.015),
            "claude-3-haiku", new TokenPricing(0.00025, 0.00125),
            "llama3", new TokenPricing(0.0, 0.0),  // Local/free
            "ollama", new TokenPricing(0.0, 0.0),  // Local/free
            "mock", new TokenPricing(0.0, 0.0)    // Mock provider
    );

    private final double developerHourlyRate;
    private final double avgManualFixMinutes;

    public CostProjector() {
        this(DEFAULT_DEVELOPER_HOURLY_RATE, DEFAULT_AVG_MANUAL_FIX_MINUTES);
    }

    public CostProjector(double developerHourlyRate, double avgManualFixMinutes) {
        this.developerHourlyRate = developerHourlyRate;
        this.avgManualFixMinutes = avgManualFixMinutes;
    }

    /**
     * Calculate comprehensive cost analysis from healing history.
     *
     * @param history list of heal cost records
     * @return cost analysis with projections
     */
    public CostAnalysis analyze(List<HealCostRecord> history) {
        if (history == null || history.isEmpty()) {
            return CostAnalysis.empty();
        }

        // Sort by date
        List<HealCostRecord> sorted = history.stream()
                .sorted(Comparator.comparing(HealCostRecord::timestamp))
                .toList();

        // Calculate actual costs
        double totalCost = sorted.stream()
                .mapToDouble(HealCostRecord::costUsd)
                .sum();

        int totalHeals = sorted.size();
        int successfulHeals = (int) sorted.stream().filter(HealCostRecord::wasSuccessful).count();

        // Calculate time saved (only for successful heals)
        double minutesSaved = successfulHeals * avgManualFixMinutes;
        double hoursSaved = minutesSaved / 60.0;
        double developerCostSaved = hoursSaved * developerHourlyRate;

        // Calculate ROI
        double netSavings = developerCostSaved - totalCost;
        double roi = totalCost > 0 ? (netSavings / totalCost) * 100 : 0;

        // Calculate daily/weekly/monthly costs
        Map<LocalDate, Double> dailyCosts = sorted.stream()
                .collect(Collectors.groupingBy(
                        r -> r.timestamp().atZone(ZoneId.systemDefault()).toLocalDate(),
                        Collectors.summingDouble(HealCostRecord::costUsd)
                ));

        // Project future costs
        CostProjection projection = projectCosts(sorted, dailyCosts);

        // Generate budget recommendations
        List<BudgetRecommendation> recommendations = generateRecommendations(
                totalCost, dailyCosts, projection, roi);

        // Calculate cost breakdown by model
        Map<String, Double> costByModel = sorted.stream()
                .collect(Collectors.groupingBy(
                        HealCostRecord::model,
                        Collectors.summingDouble(HealCostRecord::costUsd)
                ));

        return new CostAnalysis(
                totalCost,
                totalHeals,
                successfulHeals,
                totalCost / Math.max(totalHeals, 1),
                Duration.ofMinutes((long) minutesSaved),
                developerCostSaved,
                netSavings,
                roi,
                dailyCosts,
                costByModel,
                projection,
                recommendations
        );
    }

    /**
     * Project future costs based on historical data.
     */
    private CostProjection projectCosts(List<HealCostRecord> history,
                                         Map<LocalDate, Double> dailyCosts) {
        if (dailyCosts.isEmpty()) {
            return new CostProjection(0, 0, 0, 0, 0, 0, TrendDirection.STABLE);
        }

        // Calculate average daily cost
        double avgDailyCost = dailyCosts.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        // Get the date range
        LocalDate earliest = dailyCosts.keySet().stream().min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate latest = dailyCosts.keySet().stream().max(LocalDate::compareTo).orElse(LocalDate.now());
        long daysCovered = ChronoUnit.DAYS.between(earliest, latest) + 1;

        // Calculate daily rate (accounting for days with no heals)
        double dailyRate = daysCovered > 0
                ? dailyCosts.values().stream().mapToDouble(Double::doubleValue).sum() / daysCovered
                : avgDailyCost;

        // Calculate trend (compare first half to second half)
        TrendDirection trend = calculateTrend(dailyCosts);

        // Apply trend factor to projections
        double trendFactor = switch (trend) {
            case INCREASING -> 1.2;
            case DECREASING -> 0.8;
            case STABLE -> 1.0;
        };

        double projectedWeeklyCost = dailyRate * 7 * trendFactor;
        double projectedMonthlyCost = dailyRate * 30 * trendFactor;
        double projectedQuarterlyCost = dailyRate * 90 * trendFactor;
        double projectedYearlyCost = dailyRate * 365 * trendFactor;

        // Calculate confidence interval (simple standard deviation based)
        double variance = dailyCosts.values().stream()
                .mapToDouble(v -> Math.pow(v - avgDailyCost, 2))
                .average()
                .orElse(0);
        double stdDev = Math.sqrt(variance);
        double confidenceMargin = stdDev * 1.96; // 95% confidence

        return new CostProjection(
                dailyRate,
                projectedWeeklyCost,
                projectedMonthlyCost,
                projectedQuarterlyCost,
                projectedYearlyCost,
                confidenceMargin * 30, // Monthly margin
                trend
        );
    }

    /**
     * Calculate cost trend direction.
     */
    private TrendDirection calculateTrend(Map<LocalDate, Double> dailyCosts) {
        if (dailyCosts.size() < 4) {
            return TrendDirection.STABLE;
        }

        List<Map.Entry<LocalDate, Double>> sorted = dailyCosts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        int midpoint = sorted.size() / 2;

        double firstHalfAvg = sorted.subList(0, midpoint).stream()
                .mapToDouble(Map.Entry::getValue)
                .average()
                .orElse(0);

        double secondHalfAvg = sorted.subList(midpoint, sorted.size()).stream()
                .mapToDouble(Map.Entry::getValue)
                .average()
                .orElse(0);

        double percentChange = firstHalfAvg > 0
                ? ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100
                : 0;

        if (percentChange > 15) return TrendDirection.INCREASING;
        if (percentChange < -15) return TrendDirection.DECREASING;
        return TrendDirection.STABLE;
    }

    /**
     * Generate budget recommendations.
     */
    private List<BudgetRecommendation> generateRecommendations(
            double totalCost, Map<LocalDate, Double> dailyCosts,
            CostProjection projection, double roi) {

        List<BudgetRecommendation> recommendations = new ArrayList<>();

        // ROI-based recommendations
        if (roi < 0) {
            recommendations.add(new BudgetRecommendation(
                    BudgetRecommendation.Priority.HIGH,
                    "Negative ROI",
                    "LLM costs exceed developer time savings. Consider: " +
                    "(1) Using a cheaper model, (2) Fixing frequently-healed locators, " +
                    "(3) Increasing cache TTL to reduce API calls."
            ));
        } else if (roi < 100) {
            recommendations.add(new BudgetRecommendation(
                    BudgetRecommendation.Priority.MEDIUM,
                    "Low ROI",
                    "ROI is positive but could be improved. Review most frequently " +
                    "healed locators and consider updating them permanently."
            ));
        }

        // Trend-based recommendations
        if (projection.trend() == TrendDirection.INCREASING) {
            recommendations.add(new BudgetRecommendation(
                    BudgetRecommendation.Priority.MEDIUM,
                    "Rising Costs",
                    "LLM costs are trending upward. This may indicate increasing " +
                    "locator instability or more frequent test runs."
            ));
        }

        // Cost optimization recommendations
        if (projection.projectedMonthlyCost() > 50) {
            recommendations.add(new BudgetRecommendation(
                    BudgetRecommendation.Priority.LOW,
                    "Consider Local LLM",
                    String.format("Projected monthly cost is $%.2f. Consider using " +
                            "Ollama with a local model to reduce costs.", projection.projectedMonthlyCost())
            ));
        }

        // Suggested budget based on projections
        double suggestedMonthlyBudget = projection.projectedMonthlyCost() * 1.2; // 20% buffer
        recommendations.add(new BudgetRecommendation(
                BudgetRecommendation.Priority.INFO,
                "Suggested Monthly Budget",
                String.format("Based on current usage, consider setting a monthly budget " +
                        "of $%.2f (projected $%.2f + 20%% buffer).",
                        suggestedMonthlyBudget, projection.projectedMonthlyCost())
        ));

        return recommendations;
    }

    /**
     * Check if current spending is within budget.
     *
     * @param currentCost current total cost
     * @param budget budget limit
     * @return budget status
     */
    public BudgetStatus checkBudget(double currentCost, double budget) {
        double percentUsed = budget > 0 ? (currentCost / budget) * 100 : 0;
        double remaining = budget - currentCost;

        BudgetStatus.Level level;
        if (percentUsed >= 100) {
            level = BudgetStatus.Level.EXCEEDED;
        } else if (percentUsed >= 90) {
            level = BudgetStatus.Level.CRITICAL;
        } else if (percentUsed >= 75) {
            level = BudgetStatus.Level.WARNING;
        } else {
            level = BudgetStatus.Level.OK;
        }

        return new BudgetStatus(currentCost, budget, remaining, percentUsed, level);
    }

    /**
     * Estimate cost for a single heal operation.
     *
     * @param model the LLM model name
     * @param inputTokens estimated input tokens
     * @param outputTokens estimated output tokens
     * @return estimated cost in USD
     */
    public double estimateHealCost(String model, int inputTokens, int outputTokens) {
        TokenPricing pricing = MODEL_PRICING.getOrDefault(
                model.toLowerCase(),
                new TokenPricing(0.001, 0.002) // Default pricing
        );

        return (inputTokens / 1000.0 * pricing.inputPer1K())
                + (outputTokens / 1000.0 * pricing.outputPer1K());
    }

    /**
     * Get model pricing information.
     */
    public static Map<String, TokenPricing> getModelPricing() {
        return MODEL_PRICING;
    }

    // Record classes

    public record HealCostRecord(
            Instant timestamp,
            String model,
            int inputTokens,
            int outputTokens,
            double costUsd,
            boolean wasSuccessful
    ) {}

    public record TokenPricing(
            double inputPer1K,
            double outputPer1K
    ) {
        public double estimateCost(int inputTokens, int outputTokens) {
            return (inputTokens / 1000.0 * inputPer1K)
                    + (outputTokens / 1000.0 * outputPer1K);
        }
    }

    public enum TrendDirection {
        INCREASING("Increasing"),
        DECREASING("Decreasing"),
        STABLE("Stable");

        private final String displayName;

        TrendDirection(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public record CostProjection(
            double dailyRate,
            double projectedWeeklyCost,
            double projectedMonthlyCost,
            double projectedQuarterlyCost,
            double projectedYearlyCost,
            double monthlyConfidenceMargin,
            TrendDirection trend
    ) {
        public String getMonthlyRange() {
            double low = Math.max(0, projectedMonthlyCost - monthlyConfidenceMargin);
            double high = projectedMonthlyCost + monthlyConfidenceMargin;
            return String.format("$%.2f - $%.2f", low, high);
        }
    }

    public record CostAnalysis(
            double totalCost,
            int totalHeals,
            int successfulHeals,
            double averageCostPerHeal,
            Duration timeSaved,
            double developerCostSaved,
            double netSavings,
            double roi,
            Map<LocalDate, Double> dailyCosts,
            Map<String, Double> costByModel,
            CostProjection projection,
            List<BudgetRecommendation> recommendations
    ) {
        public static CostAnalysis empty() {
            return new CostAnalysis(
                    0, 0, 0, 0,
                    Duration.ZERO, 0, 0, 0,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    new CostProjection(0, 0, 0, 0, 0, 0, TrendDirection.STABLE),
                    Collections.emptyList()
            );
        }

        public String getSuccessRate() {
            return totalHeals > 0
                    ? String.format("%.1f%%", (successfulHeals * 100.0) / totalHeals)
                    : "N/A";
        }

        public String getFormattedTimeSaved() {
            long minutes = timeSaved.toMinutes();
            if (minutes < 60) {
                return minutes + " minutes";
            }
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            return String.format("%d hours %d minutes", hours, remainingMinutes);
        }

        public boolean isPositiveRoi() {
            return roi > 0;
        }
    }

    public record BudgetRecommendation(
            Priority priority,
            String title,
            String description
    ) {
        public enum Priority {
            HIGH, MEDIUM, LOW, INFO
        }
    }

    public record BudgetStatus(
            double currentCost,
            double budget,
            double remaining,
            double percentUsed,
            Level level
    ) {
        public enum Level {
            OK("Within budget"),
            WARNING("Approaching limit"),
            CRITICAL("Near limit"),
            EXCEEDED("Over budget");

            private final String description;

            Level(String description) {
                this.description = description;
            }

            public String getDescription() { return description; }
        }

        public boolean isOverBudget() {
            return level == Level.EXCEEDED;
        }

        public String getFormattedStatus() {
            return String.format("$%.2f / $%.2f (%.1f%%)", currentCost, budget, percentUsed);
        }
    }
}
