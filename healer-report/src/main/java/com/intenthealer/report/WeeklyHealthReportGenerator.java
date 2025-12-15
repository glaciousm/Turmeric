package com.intenthealer.report;

import com.intenthealer.report.model.HealEvent;
import com.intenthealer.report.model.HealReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates weekly health reports summarizing healing activity,
 * trends, and recommendations.
 */
public class WeeklyHealthReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyHealthReportGenerator.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter WEEK_FORMAT = DateTimeFormatter.ofPattern("'Week of' MMM d, yyyy");

    /**
     * Generate a weekly health report from heal events.
     */
    public WeeklyHealthReport generateReport(List<HealEvent> events, Instant weekStart, Instant weekEnd) {
        WeeklyHealthReport report = new WeeklyHealthReport();
        report.setWeekStart(weekStart);
        report.setWeekEnd(weekEnd);
        report.setGeneratedAt(Instant.now());

        // Filter events for the week
        List<HealEvent> weekEvents = events.stream()
                .filter(e -> e.getTimestamp() != null)
                .filter(e -> !e.getTimestamp().isBefore(weekStart) && e.getTimestamp().isBefore(weekEnd))
                .toList();

        report.setTotalHealAttempts(weekEvents.size());

        // Calculate outcomes
        Map<String, Long> outcomes = weekEvents.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getOutcome() != null ? e.getOutcome() : "UNKNOWN",
                        Collectors.counting()
                ));

        report.setSuccessCount(outcomes.getOrDefault("SUCCESS", 0L).intValue());
        report.setRefusedCount(outcomes.getOrDefault("REFUSED", 0L).intValue());
        report.setFailedCount(outcomes.getOrDefault("FAILED", 0L).intValue());

        // Calculate success rate
        if (report.getTotalHealAttempts() > 0) {
            report.setSuccessRate((double) report.getSuccessCount() / report.getTotalHealAttempts());
        }

        // Calculate costs
        double totalCost = weekEvents.stream()
                .filter(e -> e.getLlmCostUsd() != null)
                .mapToDouble(HealEvent::getLlmCostUsd)
                .sum();
        report.setTotalLlmCost(totalCost);

        // Calculate latency stats
        DoubleSummaryStatistics latencyStats = weekEvents.stream()
                .filter(e -> e.getLlmLatencyMs() != null)
                .mapToDouble(HealEvent::getLlmLatencyMs)
                .summaryStatistics();

        if (latencyStats.getCount() > 0) {
            report.setAverageLatencyMs(latencyStats.getAverage());
            report.setMaxLatencyMs(latencyStats.getMax());
            report.setMinLatencyMs(latencyStats.getMin());
        }

        // Analyze by day
        report.setDailyBreakdown(analyzeDailyBreakdown(weekEvents, weekStart));

        // Find most healed elements
        report.setMostHealedElements(findMostHealedElements(weekEvents, 10));

        // Find problematic tests
        report.setProblematicTests(findProblematicTests(weekEvents, 5));

        // Calculate trend vs previous week
        calculateTrend(report, events, weekStart);

        // Generate recommendations
        report.setRecommendations(generateRecommendations(report, weekEvents));

        return report;
    }

    /**
     * Generate report for the current week.
     */
    public WeeklyHealthReport generateCurrentWeekReport(List<HealEvent> events) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusWeeks(1);

        return generateReport(
                events,
                weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant()
        );
    }

    /**
     * Generate report for the previous week.
     */
    public WeeklyHealthReport generatePreviousWeekReport(List<HealEvent> events) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate weekEnd = weekStart.plusWeeks(1);

        return generateReport(
                events,
                weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant()
        );
    }

    /**
     * Generate HTML report.
     */
    public String generateHtmlReport(WeeklyHealthReport report) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Weekly Heal Health Report</title>\n");
        html.append("<style>\n");
        html.append(getReportStyles());
        html.append("</style>\n</head>\n<body>\n");

        // Header
        html.append("<div class=\"report-container\">\n");
        html.append("<h1>Weekly Heal Health Report</h1>\n");
        html.append("<p class=\"subtitle\">")
                .append(formatWeekRange(report.getWeekStart(), report.getWeekEnd()))
                .append("</p>\n");

        // Summary cards
        html.append("<div class=\"summary-cards\">\n");
        appendSummaryCard(html, "Total Heals", String.valueOf(report.getTotalHealAttempts()), "neutral");
        appendSummaryCard(html, "Success Rate", String.format("%.1f%%", report.getSuccessRate() * 100),
                report.getSuccessRate() > 0.8 ? "good" : report.getSuccessRate() > 0.6 ? "warning" : "bad");
        appendSummaryCard(html, "LLM Cost", String.format("$%.4f", report.getTotalLlmCost()), "neutral");
        appendSummaryCard(html, "Avg Latency", String.format("%.0fms", report.getAverageLatencyMs()), "neutral");
        html.append("</div>\n");

        // Trend indicator
        if (report.getTrendVsPreviousWeek() != null) {
            String trendClass = report.getTrendVsPreviousWeek() >= 0 ? "trend-up" : "trend-down";
            String trendIcon = report.getTrendVsPreviousWeek() >= 0 ? "&#9650;" : "&#9660;";
            html.append("<div class=\"trend ").append(trendClass).append("\">\n");
            html.append("<span>").append(trendIcon).append(" ");
            html.append(String.format("%.1f%% vs previous week", Math.abs(report.getTrendVsPreviousWeek()) * 100));
            html.append("</span>\n</div>\n");
        }

        // Daily breakdown
        html.append("<h2>Daily Breakdown</h2>\n");
        html.append("<table class=\"data-table\">\n");
        html.append("<tr><th>Day</th><th>Attempts</th><th>Success</th><th>Refused</th><th>Failed</th><th>Rate</th></tr>\n");
        for (DailyStats day : report.getDailyBreakdown()) {
            html.append("<tr>");
            html.append("<td>").append(day.getDate().format(DATE_FORMAT)).append("</td>");
            html.append("<td>").append(day.getAttempts()).append("</td>");
            html.append("<td class=\"success\">").append(day.getSuccesses()).append("</td>");
            html.append("<td class=\"warning\">").append(day.getRefused()).append("</td>");
            html.append("<td class=\"error\">").append(day.getFailed()).append("</td>");
            html.append("<td>").append(String.format("%.0f%%", day.getSuccessRate() * 100)).append("</td>");
            html.append("</tr>\n");
        }
        html.append("</table>\n");

        // Most healed elements
        if (!report.getMostHealedElements().isEmpty()) {
            html.append("<h2>Most Frequently Healed Elements</h2>\n");
            html.append("<table class=\"data-table\">\n");
            html.append("<tr><th>Element</th><th>Count</th><th>Success Rate</th></tr>\n");
            for (ElementStats element : report.getMostHealedElements()) {
                html.append("<tr>");
                html.append("<td><code>").append(truncate(element.getLocator(), 50)).append("</code></td>");
                html.append("<td>").append(element.getHealCount()).append("</td>");
                html.append("<td>").append(String.format("%.0f%%", element.getSuccessRate() * 100)).append("</td>");
                html.append("</tr>\n");
            }
            html.append("</table>\n");
        }

        // Problematic tests
        if (!report.getProblematicTests().isEmpty()) {
            html.append("<h2>Tests Needing Attention</h2>\n");
            html.append("<ul class=\"problem-list\">\n");
            for (TestStats test : report.getProblematicTests()) {
                html.append("<li><strong>").append(test.getTestName()).append("</strong>");
                html.append(" - ").append(test.getHealCount()).append(" heals, ");
                html.append(String.format("%.0f%% success", test.getSuccessRate() * 100));
                html.append("</li>\n");
            }
            html.append("</ul>\n");
        }

        // Recommendations
        if (!report.getRecommendations().isEmpty()) {
            html.append("<h2>Recommendations</h2>\n");
            html.append("<ul class=\"recommendations\">\n");
            for (String rec : report.getRecommendations()) {
                html.append("<li>").append(rec).append("</li>\n");
            }
            html.append("</ul>\n");
        }

        html.append("<p class=\"footer\">Generated: ").append(report.getGeneratedAt()).append("</p>\n");
        html.append("</div>\n</body>\n</html>");

        return html.toString();
    }

    /**
     * Save report to file.
     */
    public void saveReport(WeeklyHealthReport report, Path htmlPath, Path jsonPath) throws IOException {
        if (htmlPath != null) {
            Files.writeString(htmlPath, generateHtmlReport(report));
            logger.info("HTML report saved to: {}", htmlPath);
        }

        // JSON would require Jackson serialization
        if (jsonPath != null) {
            // Simplified JSON output
            String json = "{\n" +
                    "  \"weekStart\": \"" + report.getWeekStart() + "\",\n" +
                    "  \"weekEnd\": \"" + report.getWeekEnd() + "\",\n" +
                    "  \"totalAttempts\": " + report.getTotalHealAttempts() + ",\n" +
                    "  \"successRate\": " + report.getSuccessRate() + ",\n" +
                    "  \"totalCost\": " + report.getTotalLlmCost() + "\n" +
                    "}";
            Files.writeString(jsonPath, json);
            logger.info("JSON report saved to: {}", jsonPath);
        }
    }

    // Private helpers

    private List<DailyStats> analyzeDailyBreakdown(List<HealEvent> events, Instant weekStart) {
        List<DailyStats> daily = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.ofInstant(weekStart, ZoneId.systemDefault()).plusDays(i);
            Instant dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);

            List<HealEvent> dayEvents = events.stream()
                    .filter(e -> e.getTimestamp() != null)
                    .filter(e -> !e.getTimestamp().isBefore(dayStart) && e.getTimestamp().isBefore(dayEnd))
                    .toList();

            DailyStats stats = new DailyStats();
            stats.setDate(date);
            stats.setAttempts(dayEvents.size());
            stats.setSuccesses((int) dayEvents.stream().filter(e -> "SUCCESS".equals(e.getOutcome())).count());
            stats.setRefused((int) dayEvents.stream().filter(e -> "REFUSED".equals(e.getOutcome())).count());
            stats.setFailed((int) dayEvents.stream().filter(e -> "FAILED".equals(e.getOutcome())).count());

            daily.add(stats);
        }

        return daily;
    }

    private List<ElementStats> findMostHealedElements(List<HealEvent> events, int limit) {
        Map<String, List<HealEvent>> byLocator = events.stream()
                .filter(e -> e.getOriginalLocator() != null)
                .collect(Collectors.groupingBy(HealEvent::getOriginalLocator));

        return byLocator.entrySet().stream()
                .map(entry -> {
                    ElementStats stats = new ElementStats();
                    stats.setLocator(entry.getKey());
                    stats.setHealCount(entry.getValue().size());
                    long successes = entry.getValue().stream()
                            .filter(e -> "SUCCESS".equals(e.getOutcome())).count();
                    stats.setSuccessRate((double) successes / entry.getValue().size());
                    return stats;
                })
                .sorted((a, b) -> Integer.compare(b.getHealCount(), a.getHealCount()))
                .limit(limit)
                .toList();
    }

    private List<TestStats> findProblematicTests(List<HealEvent> events, int limit) {
        Map<String, List<HealEvent>> byTest = events.stream()
                .filter(e -> e.getStepText() != null)
                .collect(Collectors.groupingBy(HealEvent::getStepText));

        return byTest.entrySet().stream()
                .map(entry -> {
                    TestStats stats = new TestStats();
                    stats.setTestName(entry.getKey());
                    stats.setHealCount(entry.getValue().size());
                    long successes = entry.getValue().stream()
                            .filter(e -> "SUCCESS".equals(e.getOutcome())).count();
                    stats.setSuccessRate((double) successes / entry.getValue().size());
                    return stats;
                })
                .filter(s -> s.getHealCount() >= 3 || s.getSuccessRate() < 0.5)
                .sorted((a, b) -> {
                    // Sort by lowest success rate, then by most heals
                    int cmp = Double.compare(a.getSuccessRate(), b.getSuccessRate());
                    return cmp != 0 ? cmp : Integer.compare(b.getHealCount(), a.getHealCount());
                })
                .limit(limit)
                .toList();
    }

    private void calculateTrend(WeeklyHealthReport report, List<HealEvent> allEvents, Instant weekStart) {
        Instant prevWeekStart = weekStart.minus(7, ChronoUnit.DAYS);
        Instant prevWeekEnd = weekStart;

        List<HealEvent> prevWeekEvents = allEvents.stream()
                .filter(e -> e.getTimestamp() != null)
                .filter(e -> !e.getTimestamp().isBefore(prevWeekStart) && e.getTimestamp().isBefore(prevWeekEnd))
                .toList();

        if (!prevWeekEvents.isEmpty()) {
            long prevSuccesses = prevWeekEvents.stream()
                    .filter(e -> "SUCCESS".equals(e.getOutcome())).count();
            double prevRate = (double) prevSuccesses / prevWeekEvents.size();

            report.setTrendVsPreviousWeek(report.getSuccessRate() - prevRate);
        }
    }

    private List<String> generateRecommendations(WeeklyHealthReport report, List<HealEvent> events) {
        List<String> recommendations = new ArrayList<>();

        // Low success rate
        if (report.getSuccessRate() < 0.7) {
            recommendations.add("Success rate is below 70%. Consider reviewing frequently failing heals and updating locators.");
        }

        // High refusal rate
        double refusalRate = report.getTotalHealAttempts() > 0
                ? (double) report.getRefusedCount() / report.getTotalHealAttempts() : 0;
        if (refusalRate > 0.3) {
            recommendations.add("High refusal rate (>30%). Consider increasing confidence threshold or reviewing guardrails.");
        }

        // High costs
        if (report.getTotalLlmCost() > 10) {
            recommendations.add("LLM costs are high. Consider enabling caching or using a more cost-effective model.");
        }

        // Frequently healed elements
        if (!report.getMostHealedElements().isEmpty()) {
            ElementStats top = report.getMostHealedElements().get(0);
            if (top.getHealCount() > 10) {
                recommendations.add("Element '" + truncate(top.getLocator(), 30) +
                        "' has been healed " + top.getHealCount() + " times. Consider fixing the locator permanently.");
            }
        }

        // Negative trend
        if (report.getTrendVsPreviousWeek() != null && report.getTrendVsPreviousWeek() < -0.1) {
            recommendations.add("Success rate has dropped significantly from last week. Investigate recent UI changes.");
        }

        // High latency
        if (report.getAverageLatencyMs() > 5000) {
            recommendations.add("Average LLM latency is high. Consider using a faster model or increasing cache TTL.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Health metrics look good! Continue monitoring for any changes.");
        }

        return recommendations;
    }

    private String formatWeekRange(Instant start, Instant end) {
        LocalDate startDate = LocalDate.ofInstant(start, ZoneId.systemDefault());
        LocalDate endDate = LocalDate.ofInstant(end, ZoneId.systemDefault()).minusDays(1);
        return startDate.format(DATE_FORMAT) + " to " + endDate.format(DATE_FORMAT);
    }

    private void appendSummaryCard(StringBuilder html, String title, String value, String type) {
        html.append("<div class=\"card card-").append(type).append("\">\n");
        html.append("<div class=\"card-title\">").append(title).append("</div>\n");
        html.append("<div class=\"card-value\">").append(value).append("</div>\n");
        html.append("</div>\n");
    }

    private String getReportStyles() {
        return """
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
            .report-container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            h1 { color: #333; margin-bottom: 5px; }
            h2 { color: #555; margin-top: 30px; border-bottom: 2px solid #eee; padding-bottom: 10px; }
            .subtitle { color: #666; margin-top: 0; }
            .summary-cards { display: flex; gap: 20px; margin: 20px 0; }
            .card { flex: 1; padding: 20px; border-radius: 8px; text-align: center; }
            .card-neutral { background: #f8f9fa; border: 1px solid #dee2e6; }
            .card-good { background: #d4edda; border: 1px solid #c3e6cb; }
            .card-warning { background: #fff3cd; border: 1px solid #ffeeba; }
            .card-bad { background: #f8d7da; border: 1px solid #f5c6cb; }
            .card-title { font-size: 14px; color: #666; }
            .card-value { font-size: 28px; font-weight: bold; color: #333; }
            .trend { text-align: center; padding: 10px; margin: 10px 0; border-radius: 4px; }
            .trend-up { background: #d4edda; color: #155724; }
            .trend-down { background: #f8d7da; color: #721c24; }
            .data-table { width: 100%; border-collapse: collapse; margin: 15px 0; }
            .data-table th, .data-table td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
            .data-table th { background: #f8f9fa; font-weight: 600; }
            .data-table code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
            .success { color: #28a745; }
            .warning { color: #ffc107; }
            .error { color: #dc3545; }
            .problem-list, .recommendations { padding-left: 20px; }
            .problem-list li, .recommendations li { margin: 10px 0; line-height: 1.6; }
            .footer { color: #999; font-size: 12px; margin-top: 30px; text-align: center; }
            """;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    // Inner types

    public static class WeeklyHealthReport {
        private Instant weekStart;
        private Instant weekEnd;
        private Instant generatedAt;
        private int totalHealAttempts;
        private int successCount;
        private int refusedCount;
        private int failedCount;
        private double successRate;
        private double totalLlmCost;
        private double averageLatencyMs;
        private double maxLatencyMs;
        private double minLatencyMs;
        private Double trendVsPreviousWeek;
        private List<DailyStats> dailyBreakdown = new ArrayList<>();
        private List<ElementStats> mostHealedElements = new ArrayList<>();
        private List<TestStats> problematicTests = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();

        // Getters and setters
        public Instant getWeekStart() { return weekStart; }
        public void setWeekStart(Instant weekStart) { this.weekStart = weekStart; }
        public Instant getWeekEnd() { return weekEnd; }
        public void setWeekEnd(Instant weekEnd) { this.weekEnd = weekEnd; }
        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
        public int getTotalHealAttempts() { return totalHealAttempts; }
        public void setTotalHealAttempts(int totalHealAttempts) { this.totalHealAttempts = totalHealAttempts; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getRefusedCount() { return refusedCount; }
        public void setRefusedCount(int refusedCount) { this.refusedCount = refusedCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public double getTotalLlmCost() { return totalLlmCost; }
        public void setTotalLlmCost(double totalLlmCost) { this.totalLlmCost = totalLlmCost; }
        public double getAverageLatencyMs() { return averageLatencyMs; }
        public void setAverageLatencyMs(double averageLatencyMs) { this.averageLatencyMs = averageLatencyMs; }
        public double getMaxLatencyMs() { return maxLatencyMs; }
        public void setMaxLatencyMs(double maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }
        public double getMinLatencyMs() { return minLatencyMs; }
        public void setMinLatencyMs(double minLatencyMs) { this.minLatencyMs = minLatencyMs; }
        public Double getTrendVsPreviousWeek() { return trendVsPreviousWeek; }
        public void setTrendVsPreviousWeek(Double trendVsPreviousWeek) { this.trendVsPreviousWeek = trendVsPreviousWeek; }
        public List<DailyStats> getDailyBreakdown() { return dailyBreakdown; }
        public void setDailyBreakdown(List<DailyStats> dailyBreakdown) { this.dailyBreakdown = dailyBreakdown; }
        public List<ElementStats> getMostHealedElements() { return mostHealedElements; }
        public void setMostHealedElements(List<ElementStats> mostHealedElements) { this.mostHealedElements = mostHealedElements; }
        public List<TestStats> getProblematicTests() { return problematicTests; }
        public void setProblematicTests(List<TestStats> problematicTests) { this.problematicTests = problematicTests; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }

    public static class DailyStats {
        private LocalDate date;
        private int attempts;
        private int successes;
        private int refused;
        private int failed;

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
        public int getSuccesses() { return successes; }
        public void setSuccesses(int successes) { this.successes = successes; }
        public int getRefused() { return refused; }
        public void setRefused(int refused) { this.refused = refused; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        public double getSuccessRate() {
            return attempts > 0 ? (double) successes / attempts : 0;
        }
    }

    public static class ElementStats {
        private String locator;
        private int healCount;
        private double successRate;

        public String getLocator() { return locator; }
        public void setLocator(String locator) { this.locator = locator; }
        public int getHealCount() { return healCount; }
        public void setHealCount(int healCount) { this.healCount = healCount; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }

    public static class TestStats {
        private String testName;
        private int healCount;
        private double successRate;

        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }
        public int getHealCount() { return healCount; }
        public void setHealCount(int healCount) { this.healCount = healCount; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }
}
