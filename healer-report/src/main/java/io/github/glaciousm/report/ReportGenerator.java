package io.github.glaciousm.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.glaciousm.core.config.ReportConfig;
import io.github.glaciousm.core.engine.LocatorRecommender;
import io.github.glaciousm.core.engine.LocatorRecommender.LocatorAnalysis;
import io.github.glaciousm.core.engine.LocatorRecommender.Recommendation;
import io.github.glaciousm.core.engine.LocatorRecommender.Severity;
import io.github.glaciousm.core.model.LocatorInfo;
import io.github.glaciousm.report.HealingAnalytics;
import io.github.glaciousm.report.HealingAnalytics.AnalyticsSummary;
import io.github.glaciousm.report.HealingAnalytics.FrequentLocator;
import io.github.glaciousm.report.VisualDiffGenerator;
import io.github.glaciousm.report.model.HealEvent;
import io.github.glaciousm.report.model.HealReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates JSON and HTML reports from healing events.
 */
public class ReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final ReportConfig config;
    private final ObjectMapper objectMapper;
    private final LocatorRecommender locatorRecommender;
    private final HealingAnalytics healingAnalytics;
    private final VisualDiffGenerator visualDiffGenerator;
    private HealReport currentReport;

    public ReportGenerator(ReportConfig config) {
        this.config = config != null ? config : new ReportConfig();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.locatorRecommender = new LocatorRecommender();
        this.healingAnalytics = new HealingAnalytics();
        this.visualDiffGenerator = new VisualDiffGenerator();
    }

    public ReportGenerator() {
        this(new ReportConfig());
    }

    /**
     * Start a new report.
     */
    public void startReport() {
        currentReport = new HealReport();
        currentReport.setTimestamp(Instant.now());
    }

    /**
     * Add a healing event to the current report.
     */
    public void addEvent(HealEvent event) {
        if (currentReport == null) {
            startReport();
        }
        currentReport.addEvent(event);
    }

    /**
     * Finish and write the report.
     */
    public void finishReport() throws IOException {
        if (currentReport == null) {
            logger.warn("No report to finish");
            return;
        }

        // Calculate duration
        long durationMs = Instant.now().toEpochMilli() - currentReport.getTimestamp().toEpochMilli();
        currentReport.setDurationMs(durationMs);

        // Ensure output directory exists
        Path outputDir = Path.of(config.getOutputDir());
        Files.createDirectories(outputDir);

        String timestamp = LocalDateTime.ofInstant(currentReport.getTimestamp(), ZoneId.systemDefault())
                .format(TIMESTAMP_FORMAT);

        // Write JSON report
        if (config.isJsonEnabled()) {
            writeJsonReport(outputDir, timestamp);
        }

        // Write HTML report
        if (config.isHtmlEnabled()) {
            writeHtmlReport(outputDir, timestamp);
        }

        logger.info("Reports written to: {}", outputDir);
        currentReport = null;
    }

    private void writeJsonReport(Path outputDir, String timestamp) throws IOException {
        File jsonFile = outputDir.resolve("healer-report-" + timestamp + ".json").toFile();
        objectMapper.writeValue(jsonFile, currentReport);
        logger.info("JSON report written: {}", jsonFile.getAbsolutePath());
    }

    private void writeHtmlReport(Path outputDir, String timestamp) throws IOException {
        File htmlFile = outputDir.resolve("healer-report-" + timestamp + ".html").toFile();

        try (FileWriter writer = new FileWriter(htmlFile)) {
            writer.write(generateHtml());
        }
        logger.info("HTML report written: {}", htmlFile.getAbsolutePath());
    }

    private String generateHtml() {
        StringBuilder html = new StringBuilder();

        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Intent Healer Report</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; padding: 20px; background: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; }
                    h1 { color: #333; margin-bottom: 20px; }
                    h2 { color: #555; margin: 20px 0 10px; border-bottom: 2px solid #ddd; padding-bottom: 5px; }
                    .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; margin-bottom: 30px; }
                    .stat { background: white; padding: 15px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .stat-value { font-size: 2em; font-weight: bold; color: #2196F3; }
                    .stat-label { color: #666; font-size: 0.9em; }
                    .event { background: white; margin-bottom: 15px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); overflow: hidden; }
                    .event-header { padding: 15px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
                    .event-header:hover { background: #f9f9f9; }
                    .event-body { padding: 15px; border-top: 1px solid #eee; display: none; }
                    .event.expanded .event-body { display: block; }
                    .status { padding: 4px 8px; border-radius: 4px; font-size: 0.85em; font-weight: bold; }
                    .status-success { background: #4CAF50; color: white; }
                    .status-refused { background: #FF9800; color: white; }
                    .status-failed { background: #f44336; color: white; }
                    .confidence { color: #666; }
                    .reasoning { background: #f5f5f5; padding: 10px; border-radius: 4px; margin-top: 10px; }
                    pre { background: #263238; color: #aed581; padding: 10px; border-radius: 4px; overflow-x: auto; }
                    /* Visual Evidence Styles */
                    .visual-evidence { margin-top: 15px; padding: 15px; background: #f8f9fa; border-radius: 8px; border: 1px solid #e9ecef; }
                    .visual-evidence h4 { margin: 0 0 10px 0; color: #495057; font-size: 1em; }
                    .visual-evidence-stats { margin-bottom: 10px; }
                    .diff-percentage { padding: 4px 8px; border-radius: 4px; font-size: 0.85em; font-weight: bold; }
                    .change-significant { background: #fff3cd; color: #856404; }
                    .change-minimal { background: #d4edda; color: #155724; }
                    .screenshot-comparison { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }
                    .screenshot-panel { text-align: center; }
                    .screenshot-label { font-weight: bold; margin-bottom: 5px; color: #495057; font-size: 0.9em; }
                    .screenshot-img { max-width: 100%; height: auto; border: 1px solid #dee2e6; border-radius: 4px; cursor: pointer; transition: transform 0.2s; }
                    .screenshot-img:hover { transform: scale(1.02); box-shadow: 0 4px 8px rgba(0,0,0,0.15); }
                    @media (max-width: 768px) { .screenshot-comparison { grid-template-columns: 1fr; } }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Intent Healer Report</h1>
            """);

        // Summary section
        HealReport.ReportSummary summary = currentReport.getSummary();
        html.append("""
                    <h2>Summary</h2>
                    <div class="summary">
                        <div class="stat">
                            <div class="stat-value">%d</div>
                            <div class="stat-label">Heal Attempts</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #4CAF50">%d</div>
                            <div class="stat-label">Successes</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #FF9800">%d</div>
                            <div class="stat-label">Refused</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #f44336">%d</div>
                            <div class="stat-label">Failed</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value">$%.4f</div>
                            <div class="stat-label">LLM Cost</div>
                        </div>
                    </div>
            """.formatted(
                summary.getHealAttempts(),
                summary.getHealSuccesses(),
                summary.getHealRefusals(),
                summary.getHealFailures(),
                summary.getTotalLlmCostUsd()
        ));

        // Events section
        html.append("<h2>Healing Events</h2>");

        for (HealEvent event : currentReport.getEvents()) {
            String statusClass = switch (event.getResult().getStatus()) {
                case "SUCCESS" -> "status-success";
                case "REFUSED" -> "status-refused";
                default -> "status-failed";
            };

            // Generate visual evidence section if screenshots are available
            String visualEvidenceHtml = generateVisualEvidenceHtml(event);

            html.append("""
                    <div class="event" onclick="this.classList.toggle('expanded')">
                        <div class="event-header">
                            <div>
                                <strong>%s</strong>
                                <span class="confidence">(%.0f%% confidence)</span>
                            </div>
                            <span class="status %s">%s</span>
                        </div>
                        <div class="event-body">
                            <p><strong>Feature:</strong> %s</p>
                            <p><strong>Scenario:</strong> %s</p>
                            <p><strong>Failure:</strong> %s - %s</p>
                            <div class="reasoning">
                                <strong>Reasoning:</strong> %s
                            </div>
                            %s
                        </div>
                    </div>
                """.formatted(
                    escapeHtml(event.getStep()),
                    event.getDecision() != null ? event.getDecision().getConfidence() * 100 : 0,
                    statusClass,
                    event.getResult().getStatus(),
                    escapeHtml(event.getFeature()),
                    escapeHtml(event.getScenario()),
                    escapeHtml(event.getFailure() != null ? event.getFailure().getExceptionType() : "Unknown"),
                    escapeHtml(event.getFailure() != null ? event.getFailure().getMessage() : ""),
                    escapeHtml(event.getDecision() != null ? event.getDecision().getReasoning() : "N/A"),
                    visualEvidenceHtml
            ));
        }

        // Analytics section
        html.append(generateAnalyticsHtml());

        // Locator Recommendations section
        html.append(generateLocatorRecommendationsHtml());

        // Trend Charts section (requires multiple reports)
        html.append(generateTrendChartsHtml());

        html.append("""
                </div>
                <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
                <script>
                    // Initialize charts if data is available
                    document.addEventListener('DOMContentLoaded', function() {
                        initializeTrendCharts();
                    });
                </script>
            </body>
            </html>
            """);

        return html.toString();
    }

    /**
     * Generates trend charts section with interactive Chart.js visualizations.
     */
    private String generateTrendChartsHtml() {
        if (currentReport == null || currentReport.getEvents() == null || currentReport.getEvents().size() < 2) {
            return "";
        }

        AnalyticsSummary analytics = healingAnalytics.analyzeReport(currentReport);
        StringBuilder html = new StringBuilder();

        html.append("""
                    <h2>Trend Analysis</h2>
                    <div class="charts-container">
            """);

        // Confidence distribution chart
        if (!analytics.confidenceDistribution().isEmpty()) {
            html.append("""
                        <div class="chart-card">
                            <h3>Confidence Distribution</h3>
                            <canvas id="confidenceChart" width="400" height="200"></canvas>
                        </div>
                """);
        }

        // Heals by action type chart
        if (!analytics.healsByActionType().isEmpty()) {
            html.append("""
                        <div class="chart-card">
                            <h3>Heals by Action Type</h3>
                            <canvas id="actionTypeChart" width="400" height="200"></canvas>
                        </div>
                """);
        }

        // Success rate gauge
        html.append("""
                        <div class="chart-card">
                            <h3>Success Rate</h3>
                            <canvas id="successRateChart" width="400" height="200"></canvas>
                        </div>
                    </div>
            """);

        // Add chart initialization script
        html.append(generateChartScripts(analytics));

        // Add CSS for charts
        html.append("""
                    <style>
                        .charts-container {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
                            gap: 20px;
                            margin-top: 20px;
                        }
                        .chart-card {
                            background: white;
                            padding: 20px;
                            border-radius: 8px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        }
                        .chart-card h3 {
                            margin: 0 0 15px 0;
                            font-size: 1em;
                            color: #333;
                        }
                        @media (max-width: 768px) {
                            .charts-container {
                                grid-template-columns: 1fr;
                            }
                        }
                    </style>
                """);

        return html.toString();
    }

    /**
     * Generates JavaScript for initializing Chart.js charts.
     */
    private String generateChartScripts(AnalyticsSummary analytics) {
        StringBuilder js = new StringBuilder();
        js.append("<script>\n");
        js.append("function initializeTrendCharts() {\n");

        // Confidence distribution chart
        if (!analytics.confidenceDistribution().isEmpty()) {
            js.append("  // Confidence Distribution Chart\n");
            js.append("  var confCtx = document.getElementById('confidenceChart');\n");
            js.append("  if (confCtx) {\n");
            js.append("    new Chart(confCtx, {\n");
            js.append("      type: 'bar',\n");
            js.append("      data: {\n");
            js.append("        labels: [");
            js.append(analytics.confidenceDistribution().keySet().stream()
                    .map(s -> "'" + s + "'")
                    .reduce((a, b) -> a + "," + b).orElse(""));
            js.append("],\n");
            js.append("        datasets: [{\n");
            js.append("          label: 'Distribution %',\n");
            js.append("          data: [");
            js.append(analytics.confidenceDistribution().values().stream()
                    .map(v -> String.format("%.1f", v))
                    .reduce((a, b) -> a + "," + b).orElse(""));
            js.append("],\n");
            js.append("          backgroundColor: ['#ef5350', '#ff7043', '#ffca28', '#66bb6a', '#4caf50'],\n");
            js.append("          borderWidth: 0\n");
            js.append("        }]\n");
            js.append("      },\n");
            js.append("      options: {\n");
            js.append("        responsive: true,\n");
            js.append("        plugins: { legend: { display: false } },\n");
            js.append("        scales: { y: { beginAtZero: true, max: 100, ticks: { callback: function(v) { return v + '%'; } } } }\n");
            js.append("      }\n");
            js.append("    });\n");
            js.append("  }\n\n");
        }

        // Action type chart
        if (!analytics.healsByActionType().isEmpty()) {
            js.append("  // Action Type Chart\n");
            js.append("  var actionCtx = document.getElementById('actionTypeChart');\n");
            js.append("  if (actionCtx) {\n");
            js.append("    new Chart(actionCtx, {\n");
            js.append("      type: 'doughnut',\n");
            js.append("      data: {\n");
            js.append("        labels: [");
            js.append(analytics.healsByActionType().keySet().stream()
                    .map(s -> "'" + s + "'")
                    .reduce((a, b) -> a + "," + b).orElse(""));
            js.append("],\n");
            js.append("        datasets: [{\n");
            js.append("          data: [");
            js.append(analytics.healsByActionType().values().stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b).orElse(""));
            js.append("],\n");
            js.append("          backgroundColor: ['#2196F3', '#4CAF50', '#FF9800', '#9C27B0', '#00BCD4', '#E91E63', '#795548', '#607D8B']\n");
            js.append("        }]\n");
            js.append("      },\n");
            js.append("      options: {\n");
            js.append("        responsive: true,\n");
            js.append("        plugins: { legend: { position: 'right' } }\n");
            js.append("      }\n");
            js.append("    });\n");
            js.append("  }\n\n");
        }

        // Success rate gauge
        js.append("  // Success Rate Chart\n");
        js.append("  var successCtx = document.getElementById('successRateChart');\n");
        js.append("  if (successCtx) {\n");
        double successRate = analytics.successRate();
        js.append("    new Chart(successCtx, {\n");
        js.append("      type: 'doughnut',\n");
        js.append("      data: {\n");
        js.append("        labels: ['Success', 'Other'],\n");
        js.append("        datasets: [{\n");
        js.append(String.format("          data: [%.1f, %.1f],\n", successRate, 100 - successRate));
        js.append("          backgroundColor: ['#4CAF50', '#e0e0e0'],\n");
        js.append("          borderWidth: 0\n");
        js.append("        }]\n");
        js.append("      },\n");
        js.append("      options: {\n");
        js.append("        responsive: true,\n");
        js.append("        circumference: 180,\n");
        js.append("        rotation: -90,\n");
        js.append("        cutout: '70%',\n");
        js.append("        plugins: {\n");
        js.append("          legend: { display: false },\n");
        js.append("          tooltip: { enabled: false }\n");
        js.append("        }\n");
        js.append("      },\n");
        js.append("      plugins: [{\n");
        js.append("        afterDraw: function(chart) {\n");
        js.append("          var ctx = chart.ctx;\n");
        js.append("          ctx.save();\n");
        js.append("          ctx.textAlign = 'center';\n");
        js.append("          ctx.font = 'bold 28px Arial';\n");
        js.append("          ctx.fillStyle = '#4CAF50';\n");
        js.append(String.format("          ctx.fillText('%.1f%%', chart.width/2, chart.height - 30);\n", successRate));
        js.append("          ctx.restore();\n");
        js.append("        }\n");
        js.append("      }]\n");
        js.append("    });\n");
        js.append("  }\n");

        js.append("}\n");
        js.append("</script>\n");

        return js.toString();
    }

    /**
     * Generates the analytics section of the HTML report.
     */
    private String generateAnalyticsHtml() {
        if (currentReport == null || currentReport.getEvents() == null || currentReport.getEvents().isEmpty()) {
            return "";
        }

        AnalyticsSummary analytics = healingAnalytics.analyzeReport(currentReport);
        StringBuilder html = new StringBuilder();

        // Default hourly rate for ROI calculation
        double hourlyRate = 75.0;

        html.append("""
                    <h2>Healing Analytics</h2>
                    <div class="analytics-container">
                        <div class="summary">
                            <div class="stat">
                                <div class="stat-value">%.1f%%</div>
                                <div class="stat-label">Success Rate</div>
                            </div>
                            <div class="stat">
                                <div class="stat-value">%.0f%%</div>
                                <div class="stat-label">Avg Confidence</div>
                            </div>
                            <div class="stat">
                                <div class="stat-value">%s</div>
                                <div class="stat-label">Time Saved</div>
                            </div>
                            <div class="stat">
                                <div class="stat-value" style="color: #4CAF50">$%.2f</div>
                                <div class="stat-label">Est. Savings</div>
                            </div>
                            <div class="stat">
                                <div class="stat-value">%.0f%%</div>
                                <div class="stat-label">ROI</div>
                            </div>
                        </div>
                """.formatted(
                analytics.successRate(),
                analytics.averageConfidence() * 100,
                formatDuration(analytics.estimatedTimeSaved()),
                analytics.getEstimatedCostSavings(hourlyRate),
                analytics.getROI(hourlyRate)
        ));

        // Confidence distribution
        if (!analytics.confidenceDistribution().isEmpty()) {
            html.append("""
                        <div class="analytics-section">
                            <h3>Confidence Distribution</h3>
                            <div class="confidence-chart">
                    """);
            for (Map.Entry<String, Double> entry : analytics.confidenceDistribution().entrySet()) {
                html.append("""
                                <div class="chart-bar">
                                    <div class="bar-label">%s</div>
                                    <div class="bar-container">
                                        <div class="bar-fill" style="width: %.1f%%"></div>
                                    </div>
                                    <div class="bar-value">%.1f%%</div>
                                </div>
                        """.formatted(entry.getKey(), entry.getValue(), entry.getValue()));
            }
            html.append("""
                            </div>
                        </div>
                    """);
        }

        // Heals by action type
        if (!analytics.healsByActionType().isEmpty()) {
            html.append("""
                        <div class="analytics-section">
                            <h3>Heals by Action Type</h3>
                            <div class="pie-chart-container">
                                <table class="data-table">
                                    <thead><tr><th>Action Type</th><th>Count</th><th>Percentage</th></tr></thead>
                                    <tbody>
                    """);
            int total = analytics.healsByActionType().values().stream().mapToInt(Integer::intValue).sum();
            for (Map.Entry<String, Integer> entry : analytics.healsByActionType().entrySet()) {
                double pct = total > 0 ? (entry.getValue() * 100.0) / total : 0;
                html.append("""
                                        <tr>
                                            <td>%s</td>
                                            <td>%d</td>
                                            <td>%.1f%%</td>
                                        </tr>
                        """.formatted(escapeHtml(entry.getKey()), entry.getValue(), pct));
            }
            html.append("""
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    """);
        }

        // Most frequently healed locators
        if (!analytics.mostFrequentlyHealedLocators().isEmpty()) {
            html.append("""
                        <div class="analytics-section">
                            <h3>Most Frequently Healed Locators</h3>
                            <p class="section-note">These locators break often and should be improved:</p>
                            <table class="data-table">
                                <thead><tr><th>Locator</th><th>Heal Count</th><th>Avg Confidence</th></tr></thead>
                                <tbody>
                    """);
            for (FrequentLocator loc : analytics.mostFrequentlyHealedLocators()) {
                html.append("""
                                    <tr>
                                        <td><code>%s</code></td>
                                        <td>%d</td>
                                        <td>%.0f%%</td>
                                    </tr>
                        """.formatted(
                        escapeHtml(truncateLocator(loc.locator(), 60)),
                        loc.healCount(),
                        loc.averageConfidence() * 100
                ));
            }
            html.append("""
                                </tbody>
                            </table>
                        </div>
                    """);
        }

        // Heals by feature
        if (!analytics.healsByFeature().isEmpty() && analytics.healsByFeature().size() > 1) {
            html.append("""
                        <div class="analytics-section">
                            <h3>Heals by Feature</h3>
                            <table class="data-table">
                                <thead><tr><th>Feature</th><th>Heal Count</th></tr></thead>
                                <tbody>
                    """);
            analytics.healsByFeature().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> html.append("""
                                        <tr>
                                            <td>%s</td>
                                            <td>%d</td>
                                        </tr>
                            """.formatted(escapeHtml(entry.getKey()), entry.getValue())));
            html.append("""
                                </tbody>
                            </table>
                        </div>
                    """);
        }

        html.append("</div>");

        // Add CSS for analytics
        html.append("""
                    <style>
                        .analytics-container { margin-top: 20px; }
                        .analytics-section {
                            background: white; padding: 15px; border-radius: 8px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-top: 15px;
                        }
                        .analytics-section h3 { margin-bottom: 10px; }
                        .section-note { color: #666; font-size: 0.9em; margin-bottom: 10px; }
                        .confidence-chart { display: flex; flex-direction: column; gap: 8px; }
                        .chart-bar { display: flex; align-items: center; gap: 10px; }
                        .bar-label { width: 60px; font-size: 0.85em; }
                        .bar-container { flex: 1; background: #eee; border-radius: 4px; height: 20px; }
                        .bar-fill { background: #2196F3; height: 100%; border-radius: 4px; transition: width 0.3s; }
                        .bar-value { width: 50px; text-align: right; font-size: 0.85em; }
                        .data-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                        .data-table th, .data-table td { padding: 8px; text-align: left; border-bottom: 1px solid #eee; }
                        .data-table th { background: #f5f5f5; font-weight: 600; }
                        .data-table code { background: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 0.85em; }
                    </style>
                """);

        return html.toString();
    }

    /**
     * Formats a Duration as a human-readable string.
     */
    private String formatDuration(java.time.Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (remainingMinutes == 0) {
            return hours + "h";
        }
        return hours + "h " + remainingMinutes + "m";
    }

    /**
     * Truncates a locator string for display.
     */
    private String truncateLocator(String locator, int maxLen) {
        if (locator == null || locator.length() <= maxLen) return locator;
        return locator.substring(0, maxLen - 3) + "...";
    }

    /**
     * Generates the locator recommendations section of the HTML report.
     */
    private String generateLocatorRecommendationsHtml() {
        StringBuilder html = new StringBuilder();

        // Collect all locators from events
        List<LocatorInfo> locators = new ArrayList<>();
        for (HealEvent event : currentReport.getEvents()) {
            // Get healed locator from result
            String healedLocator = event.getHealedLocator();
            if (healedLocator != null && !healedLocator.isEmpty()) {
                LocatorInfo.LocatorStrategy strategy = inferStrategy(healedLocator);
                locators.add(new LocatorInfo(strategy, healedLocator));
            }
            // Get original locator from failure
            String originalLocator = event.getOriginalLocator();
            if (originalLocator != null && !originalLocator.isEmpty()) {
                LocatorInfo.LocatorStrategy strategy = inferStrategy(originalLocator);
                locators.add(new LocatorInfo(strategy, originalLocator));
            }
        }

        if (locators.isEmpty()) {
            return "";
        }

        LocatorAnalysis analysis = locatorRecommender.analyzeHealedLocators(locators);

        html.append("""
                    <h2>Locator Stability Analysis</h2>
                    <div class="recommendations-container">
                        <div class="summary">
                            <div class="stat">
                                <div class="stat-value">%d</div>
                                <div class="stat-label">Locators Analyzed</div>
                            </div>
                            <div class="stat">
                                <div class="stat-value" style="color: #4CAF50">%d</div>
                                <div class="stat-label">Stable</div>
                            </div>
                            <div class="stat">
                                <div class="stat-value" style="color: #FF9800">%d</div>
                                <div class="stat-label">Brittle</div>
                            </div>
                            <div class="stat">
                                <div class="stat-value">%.0f%%</div>
                                <div class="stat-label">Stability Score</div>
                            </div>
                            <div class="stat">
                                <div class="stat-value">%s</div>
                                <div class="stat-label">Rating</div>
                            </div>
                        </div>
                """.formatted(
                analysis.totalLocatorsAnalyzed(),
                analysis.stableLocators(),
                analysis.brittleLocators(),
                analysis.getStabilityScore(),
                analysis.getStabilityRating()
        ));

        // Top recommendations
        if (!analysis.topRecommendations().isEmpty()) {
            html.append("""
                        <div class="top-recommendations">
                            <h3>Top Recommendations</h3>
                            <ul class="recommendation-list">
                    """);
            for (String rec : analysis.topRecommendations()) {
                html.append("<li>").append(escapeHtml(rec)).append("</li>");
            }
            html.append("""
                            </ul>
                        </div>
                    """);
        }

        // Issues breakdown
        if (!analysis.issuesByType().isEmpty()) {
            html.append("""
                        <div class="issues-breakdown">
                            <h3>Issues by Type</h3>
                            <table class="issues-table">
                                <thead><tr><th>Issue</th><th>Count</th></tr></thead>
                                <tbody>
                    """);
            for (Map.Entry<String, Integer> entry : analysis.issuesByType().entrySet()) {
                html.append("<tr><td>").append(escapeHtml(entry.getKey()))
                        .append("</td><td>").append(entry.getValue()).append("</td></tr>");
            }
            html.append("""
                                </tbody>
                            </table>
                        </div>
                    """);
        }

        // Detailed recommendations (limit to most important)
        List<Recommendation> criticalRecs = analysis.recommendations().stream()
                .filter(r -> r.severity() == Severity.CRITICAL || r.severity() == Severity.WARNING)
                .limit(10)
                .toList();

        if (!criticalRecs.isEmpty()) {
            html.append("""
                        <div class="detailed-recommendations">
                            <h3>Detailed Recommendations</h3>
                    """);

            for (Recommendation rec : criticalRecs) {
                String severityClass = rec.severity() == Severity.CRITICAL ? "severity-critical" : "severity-warning";
                html.append("""
                            <div class="recommendation %s">
                                <div class="rec-header">
                                    <span class="severity-badge %s">%s</span>
                                    <strong>%s</strong>
                                </div>
                                <div class="rec-body">
                                    <p><strong>Locator:</strong> <code>%s</code></p>
                                    <p><strong>Suggestion:</strong> %s</p>
                                    <p><strong>Example:</strong> <code>%s</code></p>
                                </div>
                            </div>
                        """.formatted(
                        severityClass,
                        severityClass,
                        rec.severity().name(),
                        escapeHtml(rec.issue()),
                        escapeHtml(rec.locator()),
                        escapeHtml(rec.suggestion()),
                        escapeHtml(rec.exampleFix())
                ));
            }

            html.append("</div>");
        }

        html.append("</div>");

        // Add CSS for recommendations
        html.append("""
                    <style>
                        .recommendations-container { margin-top: 20px; }
                        .top-recommendations, .issues-breakdown, .detailed-recommendations {
                            background: white; padding: 15px; border-radius: 8px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-top: 15px;
                        }
                        .recommendation-list { margin: 10px 0; padding-left: 20px; }
                        .recommendation-list li { margin: 5px 0; }
                        .issues-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                        .issues-table th, .issues-table td {
                            padding: 8px; text-align: left; border-bottom: 1px solid #eee;
                        }
                        .issues-table th { background: #f5f5f5; }
                        .recommendation { padding: 15px; margin: 10px 0; border-radius: 8px; border-left: 4px solid; }
                        .severity-critical { border-color: #f44336; background: #ffebee; }
                        .severity-warning { border-color: #FF9800; background: #fff3e0; }
                        .rec-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
                        .severity-badge {
                            padding: 2px 8px; border-radius: 4px; font-size: 0.75em;
                            font-weight: bold; color: white;
                        }
                        .severity-badge.severity-critical { background: #f44336; }
                        .severity-badge.severity-warning { background: #FF9800; }
                        .rec-body p { margin: 5px 0; }
                        .rec-body code {
                            background: #f5f5f5; padding: 2px 6px; border-radius: 4px;
                            font-family: monospace; font-size: 0.9em;
                        }
                    </style>
                """);

        return html.toString();
    }

    /**
     * Generates the visual evidence section for a single heal event.
     * Shows before/after screenshots and visual diff when available.
     */
    private String generateVisualEvidenceHtml(HealEvent event) {
        if (event.getArtifacts() == null || !event.getArtifacts().hasVisualEvidence()) {
            return "";
        }

        HealEvent.ArtifactInfo artifacts = event.getArtifacts();
        String beforeImg = artifacts.getBeforeScreenshotBase64();
        String afterImg = artifacts.getAfterScreenshotBase64();
        String diffImg = artifacts.getDiffScreenshotBase64();
        Double diffPct = artifacts.getDiffPercentage();

        StringBuilder html = new StringBuilder();

        html.append("""
                <div class="visual-evidence">
                    <h4>Visual Evidence</h4>
                    <div class="visual-evidence-stats">
            """);

        if (diffPct != null) {
            String changeClass = diffPct > 5.0 ? "change-significant" : "change-minimal";
            html.append("""
                        <span class="diff-percentage %s">%.1f%% visual change</span>
                """.formatted(changeClass, diffPct));
        }

        html.append("""
                    </div>
                    <div class="screenshot-comparison">
                        <div class="screenshot-panel">
                            <div class="screenshot-label">Before Healing</div>
                            <img src="%s" alt="Before healing" class="screenshot-img" />
                        </div>
                        <div class="screenshot-panel">
                            <div class="screenshot-label">After Healing</div>
                            <img src="%s" alt="After healing" class="screenshot-img" />
                        </div>
            """.formatted(
                beforeImg.startsWith("data:") ? beforeImg : "data:image/png;base64," + beforeImg,
                afterImg.startsWith("data:") ? afterImg : "data:image/png;base64," + afterImg
        ));

        // Add diff image if available
        if (diffImg != null && !diffImg.isEmpty()) {
            html.append("""
                        <div class="screenshot-panel">
                            <div class="screenshot-label">Difference</div>
                            <img src="%s" alt="Visual difference" class="screenshot-img" />
                        </div>
                """.formatted(
                    diffImg.startsWith("data:") ? diffImg : "data:image/png;base64," + diffImg
            ));
        }

        html.append("""
                    </div>
                </div>
            """);

        return html.toString();
    }

    /**
     * Returns CSS styles for visual evidence display.
     * Called during HTML generation to include necessary styles.
     */
    private String getVisualEvidenceStyles() {
        return """
                .visual-evidence {
                    margin-top: 15px;
                    padding: 15px;
                    background: #f8f9fa;
                    border-radius: 8px;
                    border: 1px solid #e9ecef;
                }
                .visual-evidence h4 {
                    margin: 0 0 10px 0;
                    color: #495057;
                    font-size: 1em;
                }
                .visual-evidence-stats {
                    margin-bottom: 10px;
                }
                .diff-percentage {
                    padding: 4px 8px;
                    border-radius: 4px;
                    font-size: 0.85em;
                    font-weight: bold;
                }
                .change-significant {
                    background: #fff3cd;
                    color: #856404;
                }
                .change-minimal {
                    background: #d4edda;
                    color: #155724;
                }
                .screenshot-comparison {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 15px;
                }
                .screenshot-panel {
                    text-align: center;
                }
                .screenshot-label {
                    font-weight: bold;
                    margin-bottom: 5px;
                    color: #495057;
                    font-size: 0.9em;
                }
                .screenshot-img {
                    max-width: 100%;
                    height: auto;
                    border: 1px solid #dee2e6;
                    border-radius: 4px;
                    cursor: pointer;
                    transition: transform 0.2s;
                }
                .screenshot-img:hover {
                    transform: scale(1.02);
                    box-shadow: 0 4px 8px rgba(0,0,0,0.15);
                }
                @media (max-width: 768px) {
                    .screenshot-comparison {
                        grid-template-columns: 1fr;
                    }
                }
            """;
    }

    /**
     * Infers the locator strategy from the locator value.
     */
    private LocatorInfo.LocatorStrategy inferStrategy(String locator) {
        if (locator == null || locator.isEmpty()) {
            return LocatorInfo.LocatorStrategy.CSS;
        }
        if (locator.startsWith("//") || locator.startsWith("(//") || locator.contains("/")) {
            return LocatorInfo.LocatorStrategy.XPATH;
        }
        if (locator.startsWith("#") && !locator.contains(" ")) {
            return LocatorInfo.LocatorStrategy.ID;
        }
        if (locator.startsWith(".") && !locator.contains(" ") && !locator.contains("[")) {
            return LocatorInfo.LocatorStrategy.CLASS_NAME;
        }
        return LocatorInfo.LocatorStrategy.CSS;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Get the current report (for testing).
     */
    public HealReport getCurrentReport() {
        return currentReport;
    }

    /**
     * Generate HTML report from a directory of JSON reports.
     */
    public void generateHtmlFromDirectory(String inputDir, String outputPath) throws IOException {
        Path dirPath = Path.of(inputDir);
        if (!Files.exists(dirPath)) {
            throw new IOException("Input directory does not exist: " + inputDir);
        }

        // Load all JSON reports
        HealReport combinedReport = new HealReport();
        combinedReport.setTimestamp(Instant.now());

        Files.walk(dirPath)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> p.getFileName().toString().startsWith("heal"))
                .forEach(p -> {
                    try {
                        HealReport report = objectMapper.readValue(p.toFile(), HealReport.class);
                        for (HealEvent event : report.getEvents()) {
                            combinedReport.addEvent(event);
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to read report: {}", p, e);
                    }
                });

        // Generate combined HTML
        HealReport originalReport = currentReport;
        currentReport = combinedReport;
        String html = generateHtml();
        currentReport = originalReport;

        Files.writeString(Path.of(outputPath), html);
        logger.info("Combined HTML report written: {}", outputPath);
    }

    /**
     * Load a single report from a JSON file.
     */
    public HealReport loadReport(String path) throws IOException {
        return objectMapper.readValue(new File(path), HealReport.class);
    }
}
