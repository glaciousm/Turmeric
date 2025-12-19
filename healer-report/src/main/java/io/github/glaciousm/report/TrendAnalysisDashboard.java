package io.github.glaciousm.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * Generates trend analysis dashboards showing healing patterns over time.
 *
 * Features:
 * - Success rate trends
 * - Confidence score trends
 * - Most frequently healed locators
 * - Time-of-day patterns
 * - Weekly/monthly comparisons
 */
public class TrendAnalysisDashboard {

    private static final Logger logger = LoggerFactory.getLogger(TrendAnalysisDashboard.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ObjectMapper objectMapper;
    private final List<HealEvent> events;

    public TrendAnalysisDashboard() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.events = new ArrayList<>();
    }

    /**
     * Add a heal event for analysis.
     */
    public void addEvent(HealEvent event) {
        events.add(event);
    }

    /**
     * Load events from a JSON file.
     */
    public void loadEvents(Path path) throws IOException {
        if (Files.exists(path)) {
            HealEvent[] loaded = objectMapper.readValue(path.toFile(), HealEvent[].class);
            events.addAll(Arrays.asList(loaded));
            logger.info("Loaded {} events from {}", loaded.length, path);
        }
    }

    /**
     * Generate trend analysis report.
     */
    public TrendReport generateReport(Duration period) {
        Instant cutoff = Instant.now().minus(period);
        List<HealEvent> filtered = events.stream()
                .filter(e -> e.timestamp().isAfter(cutoff))
                .sorted(Comparator.comparing(HealEvent::timestamp))
                .toList();

        if (filtered.isEmpty()) {
            return TrendReport.empty(period);
        }

        // Daily stats
        Map<LocalDate, DailyStats> dailyStats = calculateDailyStats(filtered);

        // Overall trends
        double overallSuccessRate = filtered.stream()
                .mapToDouble(e -> e.success() ? 1.0 : 0.0)
                .average().orElse(0);

        double avgConfidence = filtered.stream()
                .mapToDouble(HealEvent::confidence)
                .average().orElse(0);

        // Most healed locators
        Map<String, Long> locatorCounts = filtered.stream()
                .collect(Collectors.groupingBy(HealEvent::originalLocator, Collectors.counting()));
        List<LocatorTrend> topLocators = locatorCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new LocatorTrend(e.getKey(), e.getValue().intValue(),
                        calculateLocatorSuccessRate(filtered, e.getKey())))
                .toList();

        // Time-of-day patterns
        Map<Integer, HourlyStats> hourlyStats = calculateHourlyStats(filtered);

        // Feature breakdown
        Map<String, FeatureStats> featureStats = calculateFeatureStats(filtered);

        // Trend direction
        TrendDirection successTrend = calculateTrendDirection(dailyStats, s -> s.successRate);
        TrendDirection confidenceTrend = calculateTrendDirection(dailyStats, s -> s.avgConfidence);

        return new TrendReport(
                period,
                filtered.size(),
                overallSuccessRate,
                avgConfidence,
                successTrend,
                confidenceTrend,
                new ArrayList<>(dailyStats.values()),
                topLocators,
                new ArrayList<>(hourlyStats.values()),
                new ArrayList<>(featureStats.values())
        );
    }

    /**
     * Generate HTML dashboard.
     */
    public String generateHtmlDashboard(TrendReport report) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Intent Healer - Trend Analysis</title>
                    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }
                        .container { max-width: 1400px; margin: 0 auto; padding: 20px; }
                        h1 { color: #333; margin-bottom: 20px; }
                        .dashboard-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
                        .card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
                        .card h2 { font-size: 14px; color: #666; margin-bottom: 10px; text-transform: uppercase; }
                        .stat-value { font-size: 36px; font-weight: bold; color: #333; }
                        .stat-trend { font-size: 14px; margin-top: 5px; }
                        .trend-up { color: #4caf50; }
                        .trend-down { color: #f44336; }
                        .trend-stable { color: #ff9800; }
                        .chart-container { height: 300px; }
                        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                        th, td { padding: 10px; text-align: left; border-bottom: 1px solid #eee; }
                        th { font-weight: 600; color: #666; }
                        .progress-bar { background: #e0e0e0; border-radius: 4px; height: 8px; overflow: hidden; }
                        .progress-fill { height: 100%; background: #4caf50; border-radius: 4px; }
                        .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 12px; }
                        .badge-success { background: #e8f5e9; color: #2e7d32; }
                        .badge-warning { background: #fff3e0; color: #ef6c00; }
                    </style>
                </head>
                <body>
                <div class="container">
                    <h1>🔄 Intent Healer - Trend Analysis</h1>
                    <p style="color: #666; margin-bottom: 20px;">Analysis period: %s</p>

                    <div class="dashboard-grid">
                """.formatted(report.period().toString()));

        // Summary cards
        html.append(generateSummaryCards(report));

        // Success rate chart
        html.append("""
                <div class="card" style="grid-column: span 2;">
                    <h2>Success Rate Trend</h2>
                    <div class="chart-container">
                        <canvas id="successChart"></canvas>
                    </div>
                </div>
                """);

        // Confidence chart
        html.append("""
                <div class="card" style="grid-column: span 2;">
                    <h2>Confidence Score Trend</h2>
                    <div class="chart-container">
                        <canvas id="confidenceChart"></canvas>
                    </div>
                </div>
                """);

        // Top healed locators table
        html.append(generateTopLocatorsTable(report));

        // Hourly distribution
        html.append("""
                <div class="card">
                    <h2>Heals by Hour</h2>
                    <div class="chart-container">
                        <canvas id="hourlyChart"></canvas>
                    </div>
                </div>
                """);

        // Feature breakdown
        html.append(generateFeatureBreakdown(report));

        html.append("</div>"); // End dashboard-grid

        // Chart.js scripts
        html.append(generateChartScripts(report));

        html.append("""
                </div>
                </body>
                </html>
                """);

        return html.toString();
    }

    /**
     * Save dashboard to file.
     */
    public Path saveDashboard(TrendReport report, Path outputDir) throws IOException {
        String html = generateHtmlDashboard(report);
        Path outputPath = outputDir.resolve("trend-analysis-" +
                LocalDate.now().format(DATE_FORMAT) + ".html");
        Files.createDirectories(outputDir);
        Files.writeString(outputPath, html);
        logger.info("Trend analysis dashboard saved to {}", outputPath);
        return outputPath;
    }

    private Map<LocalDate, DailyStats> calculateDailyStats(List<HealEvent> events) {
        Map<LocalDate, List<HealEvent>> byDate = events.stream()
                .collect(Collectors.groupingBy(e ->
                        e.timestamp().atZone(ZoneId.systemDefault()).toLocalDate()));

        Map<LocalDate, DailyStats> stats = new TreeMap<>();
        for (var entry : byDate.entrySet()) {
            List<HealEvent> dayEvents = entry.getValue();
            long successes = dayEvents.stream().filter(HealEvent::success).count();
            double avgConf = dayEvents.stream().mapToDouble(HealEvent::confidence).average().orElse(0);

            stats.put(entry.getKey(), new DailyStats(
                    entry.getKey(),
                    dayEvents.size(),
                    (int) successes,
                    dayEvents.size() - (int) successes,
                    dayEvents.isEmpty() ? 0 : (double) successes / dayEvents.size() * 100,
                    avgConf * 100
            ));
        }
        return stats;
    }

    private Map<Integer, HourlyStats> calculateHourlyStats(List<HealEvent> events) {
        Map<Integer, List<HealEvent>> byHour = events.stream()
                .collect(Collectors.groupingBy(e ->
                        e.timestamp().atZone(ZoneId.systemDefault()).getHour()));

        Map<Integer, HourlyStats> stats = new TreeMap<>();
        for (int hour = 0; hour < 24; hour++) {
            List<HealEvent> hourEvents = byHour.getOrDefault(hour, List.of());
            long successes = hourEvents.stream().filter(HealEvent::success).count();

            stats.put(hour, new HourlyStats(
                    hour,
                    hourEvents.size(),
                    (int) successes,
                    hourEvents.isEmpty() ? 0 : (double) successes / hourEvents.size() * 100
            ));
        }
        return stats;
    }

    private Map<String, FeatureStats> calculateFeatureStats(List<HealEvent> events) {
        Map<String, List<HealEvent>> byFeature = events.stream()
                .collect(Collectors.groupingBy(HealEvent::featureName));

        Map<String, FeatureStats> stats = new HashMap<>();
        for (var entry : byFeature.entrySet()) {
            List<HealEvent> featureEvents = entry.getValue();
            long successes = featureEvents.stream().filter(HealEvent::success).count();
            double avgConf = featureEvents.stream().mapToDouble(HealEvent::confidence).average().orElse(0);

            stats.put(entry.getKey(), new FeatureStats(
                    entry.getKey(),
                    featureEvents.size(),
                    (int) successes,
                    featureEvents.size() - (int) successes,
                    featureEvents.isEmpty() ? 0 : (double) successes / featureEvents.size() * 100,
                    avgConf * 100
            ));
        }
        return stats;
    }

    private double calculateLocatorSuccessRate(List<HealEvent> events, String locator) {
        List<HealEvent> locatorEvents = events.stream()
                .filter(e -> e.originalLocator().equals(locator))
                .toList();
        if (locatorEvents.isEmpty()) return 0;
        return locatorEvents.stream().filter(HealEvent::success).count() * 100.0 / locatorEvents.size();
    }

    private TrendDirection calculateTrendDirection(Map<LocalDate, DailyStats> stats,
                                                    java.util.function.Function<DailyStats, Double> extractor) {
        if (stats.size() < 2) return TrendDirection.STABLE;

        List<DailyStats> sorted = stats.values().stream()
                .sorted(Comparator.comparing(DailyStats::date))
                .toList();

        // Compare first half average to second half average
        int mid = sorted.size() / 2;
        double firstHalf = sorted.subList(0, mid).stream()
                .mapToDouble(s -> extractor.apply(s)).average().orElse(0);
        double secondHalf = sorted.subList(mid, sorted.size()).stream()
                .mapToDouble(s -> extractor.apply(s)).average().orElse(0);

        double diff = secondHalf - firstHalf;
        if (Math.abs(diff) < 2) return TrendDirection.STABLE;
        return diff > 0 ? TrendDirection.UP : TrendDirection.DOWN;
    }

    private String generateSummaryCards(TrendReport report) {
        String successTrendClass = switch (report.successTrend()) {
            case UP -> "trend-up";
            case DOWN -> "trend-down";
            case STABLE -> "trend-stable";
        };
        String successTrendIcon = switch (report.successTrend()) {
            case UP -> "↑";
            case DOWN -> "↓";
            case STABLE -> "→";
        };

        return """
                <div class="card">
                    <h2>Total Heals</h2>
                    <div class="stat-value">%d</div>
                </div>
                <div class="card">
                    <h2>Success Rate</h2>
                    <div class="stat-value">%.1f%%</div>
                    <div class="stat-trend %s">%s %s</div>
                </div>
                <div class="card">
                    <h2>Avg Confidence</h2>
                    <div class="stat-value">%.1f%%</div>
                </div>
                <div class="card">
                    <h2>Unique Locators</h2>
                    <div class="stat-value">%d</div>
                </div>
                """.formatted(
                report.totalEvents(),
                report.overallSuccessRate() * 100,
                successTrendClass, successTrendIcon, report.successTrend().name().toLowerCase(),
                report.avgConfidence() * 100,
                report.topLocators().size()
        );
    }

    private String generateTopLocatorsTable(TrendReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <div class="card" style="grid-column: span 2;">
                    <h2>Most Healed Locators</h2>
                    <table>
                        <tr><th>Locator</th><th>Heals</th><th>Success Rate</th></tr>
                """);

        for (LocatorTrend locator : report.topLocators()) {
            String badge = locator.successRate() >= 80 ? "badge-success" : "badge-warning";
            sb.append("""
                    <tr>
                        <td><code>%s</code></td>
                        <td>%d</td>
                        <td>
                            <span class="badge %s">%.1f%%</span>
                            <div class="progress-bar"><div class="progress-fill" style="width: %.1f%%"></div></div>
                        </td>
                    </tr>
                    """.formatted(
                    truncate(locator.locator(), 60),
                    locator.healCount(),
                    badge,
                    locator.successRate(),
                    locator.successRate()
            ));
        }

        sb.append("</table></div>");
        return sb.toString();
    }

    private String generateFeatureBreakdown(TrendReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <div class="card">
                    <h2>By Feature</h2>
                    <table>
                        <tr><th>Feature</th><th>Heals</th><th>Success</th></tr>
                """);

        report.featureStats().stream()
                .sorted(Comparator.comparingInt(FeatureStats::totalHeals).reversed())
                .limit(8)
                .forEach(f -> sb.append("""
                        <tr>
                            <td>%s</td>
                            <td>%d</td>
                            <td>%.1f%%</td>
                        </tr>
                        """.formatted(truncate(f.featureName(), 30), f.totalHeals(), f.successRate())));

        sb.append("</table></div>");
        return sb.toString();
    }

    private String generateChartScripts(TrendReport report) {
        // Prepare data for charts
        List<String> dates = report.dailyStats().stream()
                .map(d -> "'" + d.date().format(DATE_FORMAT) + "'")
                .toList();
        List<String> successRates = report.dailyStats().stream()
                .map(d -> String.format("%.1f", d.successRate()))
                .toList();
        List<String> confidences = report.dailyStats().stream()
                .map(d -> String.format("%.1f", d.avgConfidence()))
                .toList();
        List<String> hourlyCounts = report.hourlyStats().stream()
                .map(h -> String.valueOf(h.totalHeals()))
                .toList();

        return """
                <script>
                // Success Rate Chart
                new Chart(document.getElementById('successChart'), {
                    type: 'line',
                    data: {
                        labels: [%s],
                        datasets: [{
                            label: 'Success Rate (%%)',
                            data: [%s],
                            borderColor: '#4caf50',
                            backgroundColor: 'rgba(76, 175, 80, 0.1)',
                            fill: true,
                            tension: 0.4
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        scales: { y: { min: 0, max: 100 } }
                    }
                });

                // Confidence Chart
                new Chart(document.getElementById('confidenceChart'), {
                    type: 'line',
                    data: {
                        labels: [%s],
                        datasets: [{
                            label: 'Avg Confidence (%%)',
                            data: [%s],
                            borderColor: '#2196f3',
                            backgroundColor: 'rgba(33, 150, 243, 0.1)',
                            fill: true,
                            tension: 0.4
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        scales: { y: { min: 0, max: 100 } }
                    }
                });

                // Hourly Chart
                new Chart(document.getElementById('hourlyChart'), {
                    type: 'bar',
                    data: {
                        labels: ['00','01','02','03','04','05','06','07','08','09','10','11','12','13','14','15','16','17','18','19','20','21','22','23'],
                        datasets: [{
                            label: 'Heals',
                            data: [%s],
                            backgroundColor: 'rgba(156, 39, 176, 0.6)'
                        }]
                    },
                    options: { responsive: true, maintainAspectRatio: false }
                });
                </script>
                """.formatted(
                String.join(",", dates),
                String.join(",", successRates),
                String.join(",", dates),
                String.join(",", confidences),
                String.join(",", hourlyCounts)
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    // Records
    public record HealEvent(
            Instant timestamp,
            String featureName,
            String scenarioName,
            String stepText,
            String originalLocator,
            String healedLocator,
            double confidence,
            boolean success
    ) {}

    public record TrendReport(
            Duration period,
            int totalEvents,
            double overallSuccessRate,
            double avgConfidence,
            TrendDirection successTrend,
            TrendDirection confidenceTrend,
            List<DailyStats> dailyStats,
            List<LocatorTrend> topLocators,
            List<HourlyStats> hourlyStats,
            List<FeatureStats> featureStats
    ) {
        public static TrendReport empty(Duration period) {
            return new TrendReport(period, 0, 0, 0, TrendDirection.STABLE, TrendDirection.STABLE,
                    List.of(), List.of(), List.of(), List.of());
        }
    }

    public record DailyStats(
            LocalDate date,
            int totalHeals,
            int successes,
            int failures,
            double successRate,
            double avgConfidence
    ) {}

    public record HourlyStats(
            int hour,
            int totalHeals,
            int successes,
            double successRate
    ) {}

    public record FeatureStats(
            String featureName,
            int totalHeals,
            int successes,
            int failures,
            double successRate,
            double avgConfidence
    ) {}

    public record LocatorTrend(
            String locator,
            int healCount,
            double successRate
    ) {}

    public enum TrendDirection {
        UP, DOWN, STABLE
    }
}
