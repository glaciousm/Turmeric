package io.github.glaciousm.report;

import io.github.glaciousm.core.engine.calibration.ConfidenceCalibrator;
import io.github.glaciousm.core.engine.metrics.HealMetricsCollector;
import io.github.glaciousm.core.engine.trust.TrustLevel;
import io.github.glaciousm.core.engine.trust.TrustLevelManager;
import io.github.glaciousm.core.feedback.FeedbackApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Trust Dashboard generates comprehensive trust metrics visualizations.
 * Provides HTML dashboard with trust level status, trends, and recommendations.
 */
public class TrustDashboard {

    private static final Logger logger = LoggerFactory.getLogger(TrustDashboard.class);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final TrustLevelManager trustManager;
    private final HealMetricsCollector metricsCollector;
    private final ConfidenceCalibrator calibrator;
    private final FeedbackApi feedbackApi;

    public TrustDashboard(
            TrustLevelManager trustManager,
            HealMetricsCollector metricsCollector,
            ConfidenceCalibrator calibrator,
            FeedbackApi feedbackApi) {
        this.trustManager = trustManager;
        this.metricsCollector = metricsCollector;
        this.calibrator = calibrator;
        this.feedbackApi = feedbackApi;
    }

    /**
     * Generate dashboard data.
     */
    public DashboardData generateDashboard() {
        DashboardData data = new DashboardData();
        data.generatedAt = Instant.now();

        // Trust level information
        if (trustManager != null) {
            data.currentTrustLevel = trustManager.getCurrentLevel();
            data.trustLevelName = data.currentTrustLevel.name();
            data.trustLevelDescription = getTrustLevelDescription(data.currentTrustLevel);
            data.consecutiveSuccesses = trustManager.getConsecutiveSuccesses();
            data.consecutiveFailures = trustManager.getConsecutiveFailures();
            data.totalSuccesses = trustManager.getTotalSuccesses();
            data.totalFailures = trustManager.getTotalFailures();
            data.successRate = calculateSuccessRate(data.totalSuccesses, data.totalFailures);
            data.nextLevelRequirements = getNextLevelRequirements(data.currentTrustLevel, data.consecutiveSuccesses);
        }

        // Metrics information
        if (metricsCollector != null) {
            // Note: HealMetricsCollector doesn't have getMetrics(), using direct methods
            // These are placeholder values - the actual implementation would need
            // to add these methods to HealMetricsCollector or use available ones
            data.totalHeals = 0; // Would need getTotalAttempts() method
            data.successfulHeals = 0; // Would need getSuccessCount() method
            data.refusedHeals = 0; // Would need getRefusalCount() method
            data.failedHeals = 0; // Would need getFailureCount() method
            data.averageConfidence = 0.0; // Would need to be calculated
            data.averageLatencyMs = metricsCollector.getAverageLatency();
            data.totalLlmCost = metricsCollector.getTotalLlmCostUsd();
            data.falseHealRate = metricsCollector.getFalseHealRate();
        }

        // Calibration information
        if (calibrator != null) {
            ConfidenceCalibrator.CalibrationStats calStats = calibrator.getStats();
            data.calibrationSamples = calStats.totalSamples();
            data.calibrationError = calStats.calibrationError();
            data.isWellCalibrated = calStats.wellCalibrated();
            data.recommendedThreshold = calibrator.getRecommendedThreshold(0.9);
            data.calibrationCurve = calibrator.getCalibrationCurve();
        }

        // Feedback information
        if (feedbackApi != null) {
            FeedbackApi.FeedbackStats fbStats = feedbackApi.getStats();
            data.feedbackTotal = fbStats.total();
            data.feedbackPositive = fbStats.positive();
            data.feedbackNegative = fbStats.negative();
            data.feedbackCorrections = fbStats.corrections();
            data.userAccuracyRate = fbStats.getAccuracyRate();
        }

        // Generate recommendations
        data.recommendations = generateRecommendations(data);

        return data;
    }

    /**
     * Generate HTML dashboard.
     */
    public String generateHtmlDashboard() {
        DashboardData data = generateDashboard();
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Intent Healer Trust Dashboard</title>\n");
        html.append("<style>\n").append(getDashboardStyles()).append("</style>\n");
        html.append("<script>\n").append(getChartScript()).append("</script>\n");
        html.append("</head>\n<body>\n");

        html.append("<div class=\"dashboard\">\n");

        // Header
        html.append("<header>\n");
        html.append("<h1>🛡️ Intent Healer Trust Dashboard</h1>\n");
        html.append("<p class=\"timestamp\">Generated: ").append(TIME_FORMAT.format(data.generatedAt)).append("</p>\n");
        html.append("</header>\n");

        // Trust Level Card (prominent)
        html.append("<section class=\"trust-level-section\">\n");
        html.append("<div class=\"trust-card trust-").append(data.currentTrustLevel.name().toLowerCase()).append("\">\n");
        html.append("<div class=\"trust-badge\">").append(getTrustBadge(data.currentTrustLevel)).append("</div>\n");
        html.append("<h2>").append(data.trustLevelName).append("</h2>\n");
        html.append("<p>").append(data.trustLevelDescription).append("</p>\n");
        html.append("<div class=\"trust-stats\">\n");
        html.append("<span>✓ ").append(data.consecutiveSuccesses).append(" consecutive successes</span>\n");
        html.append("<span>✗ ").append(data.consecutiveFailures).append(" consecutive failures</span>\n");
        html.append("</div>\n");
        if (data.nextLevelRequirements != null) {
            html.append("<p class=\"next-level\">").append(data.nextLevelRequirements).append("</p>\n");
        }
        html.append("</div>\n");
        html.append("</section>\n");

        // Metrics Grid
        html.append("<section class=\"metrics-grid\">\n");
        appendMetricCard(html, "Success Rate", String.format("%.1f%%", data.successRate * 100),
                data.successRate > 0.8 ? "good" : data.successRate > 0.6 ? "warning" : "bad");
        appendMetricCard(html, "Total Heals", String.valueOf(data.totalHeals), "neutral");
        appendMetricCard(html, "Avg Confidence", String.format("%.2f", data.averageConfidence), "neutral");
        appendMetricCard(html, "Avg Latency", String.format("%.0fms", data.averageLatencyMs), "neutral");
        appendMetricCard(html, "LLM Cost", String.format("$%.4f", data.totalLlmCost), "neutral");
        appendMetricCard(html, "False Heal Rate", String.format("%.1f%%", data.falseHealRate * 100),
                data.falseHealRate < 0.1 ? "good" : data.falseHealRate < 0.2 ? "warning" : "bad");
        html.append("</section>\n");

        // Calibration Section
        html.append("<section class=\"calibration-section\">\n");
        html.append("<h2>Confidence Calibration</h2>\n");
        html.append("<div class=\"calibration-info\">\n");
        html.append("<p><strong>Status:</strong> ");
        html.append(data.isWellCalibrated ? "✅ Well Calibrated" : "⚠️ Needs Calibration");
        html.append("</p>\n");
        html.append("<p><strong>Samples:</strong> ").append(data.calibrationSamples).append("</p>\n");
        html.append("<p><strong>Error:</strong> ").append(String.format("%.2f%%", data.calibrationError * 100)).append("</p>\n");
        html.append("<p><strong>Recommended Threshold:</strong> ").append(String.format("%.2f", data.recommendedThreshold)).append("</p>\n");
        html.append("</div>\n");

        // Calibration curve chart placeholder
        if (data.calibrationCurve != null && !data.calibrationCurve.isEmpty()) {
            html.append("<div class=\"calibration-chart\">\n");
            html.append("<canvas id=\"calibrationChart\"></canvas>\n");
            html.append("</div>\n");
            html.append("<script>drawCalibrationChart(").append(formatCalibrationData(data.calibrationCurve)).append(");</script>\n");
        }
        html.append("</section>\n");

        // Feedback Section
        html.append("<section class=\"feedback-section\">\n");
        html.append("<h2>User Feedback</h2>\n");
        html.append("<div class=\"feedback-stats\">\n");
        html.append("<div class=\"feedback-item positive\"><span>").append(data.feedbackPositive).append("</span><label>Positive</label></div>\n");
        html.append("<div class=\"feedback-item negative\"><span>").append(data.feedbackNegative).append("</span><label>Negative</label></div>\n");
        html.append("<div class=\"feedback-item corrections\"><span>").append(data.feedbackCorrections).append("</span><label>Corrections</label></div>\n");
        html.append("</div>\n");
        html.append("<p><strong>User-Reported Accuracy:</strong> ").append(String.format("%.1f%%", data.userAccuracyRate * 100)).append("</p>\n");
        html.append("</section>\n");

        // Recommendations
        if (!data.recommendations.isEmpty()) {
            html.append("<section class=\"recommendations-section\">\n");
            html.append("<h2>Recommendations</h2>\n");
            html.append("<ul>\n");
            for (Recommendation rec : data.recommendations) {
                html.append("<li class=\"rec-").append(rec.severity().toLowerCase()).append("\">\n");
                html.append("<strong>").append(rec.title()).append("</strong><br>\n");
                html.append(rec.description()).append("\n");
                html.append("</li>\n");
            }
            html.append("</ul>\n");
            html.append("</section>\n");
        }

        // Trust Level Progress
        html.append("<section class=\"progress-section\">\n");
        html.append("<h2>Trust Level Progression</h2>\n");
        html.append("<div class=\"trust-progression\">\n");
        for (TrustLevel level : TrustLevel.values()) {
            String cls = level == data.currentTrustLevel ? "current" :
                         level.ordinal() < data.currentTrustLevel.ordinal() ? "achieved" : "pending";
            html.append("<div class=\"level-marker ").append(cls).append("\">\n");
            html.append("<span class=\"level-name\">").append(level.name()).append("</span>\n");
            html.append("<span class=\"level-icon\">").append(getTrustBadge(level)).append("</span>\n");
            html.append("</div>\n");
        }
        html.append("</div>\n");
        html.append("</section>\n");

        html.append("</div>\n"); // dashboard
        html.append("</body>\n</html>");

        return html.toString();
    }

    /**
     * Save dashboard to file.
     */
    public void saveDashboard(Path outputPath) throws IOException {
        String html = generateHtmlDashboard();
        Files.writeString(outputPath, html);
        logger.info("Trust dashboard saved to: {}", outputPath);
    }

    // Helper methods

    private String getTrustLevelDescription(TrustLevel level) {
        return switch (level) {
            case L0_SHADOW -> "Shadow mode - All heals are logged but not applied. Building initial confidence.";
            case L1_MANUAL -> "Manual mode - Heals require explicit user approval before application.";
            case L2_SAFE -> "Safe mode - Auto-apply safe heals, require approval for others.";
            case L3_AUTO -> "Auto mode - High-confidence heals are auto-applied with guardrails.";
            case L4_SILENT -> "Silent mode - All qualifying heals are auto-applied without interruption.";
        };
    }

    private String getTrustBadge(TrustLevel level) {
        return switch (level) {
            case L0_SHADOW -> "👁️";
            case L1_MANUAL -> "✋";
            case L2_SAFE -> "💡";
            case L3_AUTO -> "🛡️";
            case L4_SILENT -> "⚡";
        };
    }

    private String getNextLevelRequirements(TrustLevel current, int consecutiveSuccesses) {
        int required = switch (current) {
            case L0_SHADOW -> 5;
            case L1_MANUAL -> 10;
            case L2_SAFE -> 15;
            case L3_AUTO -> 20;
            case L4_SILENT -> 0; // Already at max
        };

        if (required == 0) {
            return "Maximum trust level achieved!";
        }

        int remaining = required - consecutiveSuccesses;
        if (remaining <= 0) {
            return "Ready for promotion on next evaluation";
        }
        return String.format("Need %d more consecutive successes for next level", remaining);
    }

    private double calculateSuccessRate(int successes, int failures) {
        int total = successes + failures;
        return total > 0 ? (double) successes / total : 0;
    }

    private List<Recommendation> generateRecommendations(DashboardData data) {
        List<Recommendation> recommendations = new ArrayList<>();

        // Low trust level warning
        if (data.currentTrustLevel == TrustLevel.L0_SHADOW || data.currentTrustLevel == TrustLevel.L1_MANUAL) {
            recommendations.add(new Recommendation(
                    "INFO",
                    "Build Trust",
                    "Continue running tests with healing enabled to build trust. Review heals carefully to provide feedback."
            ));
        }

        // High failure rate
        if (data.falseHealRate > 0.2) {
            recommendations.add(new Recommendation(
                    "WARNING",
                    "High False Heal Rate",
                    "False heal rate is above 20%. Consider increasing confidence threshold or reviewing problematic locators."
            ));
        }

        // Poor calibration
        if (!data.isWellCalibrated && data.calibrationSamples > 50) {
            recommendations.add(new Recommendation(
                    "WARNING",
                    "Calibration Needed",
                    "LLM confidence scores are not well-calibrated. Provide more feedback to improve accuracy predictions."
            ));
        }

        // Low user feedback
        if (data.feedbackTotal < 20 && data.totalHeals > 50) {
            recommendations.add(new Recommendation(
                    "INFO",
                    "Provide Feedback",
                    "User feedback is limited. Providing feedback on heals helps improve future accuracy."
            ));
        }

        // High consecutive failures
        if (data.consecutiveFailures >= 3) {
            recommendations.add(new Recommendation(
                    "ERROR",
                    "Consecutive Failures",
                    "Multiple consecutive failures detected. Review recent heals and consider adjusting configuration."
            ));
        }

        // Ready for promotion
        if (data.currentTrustLevel != TrustLevel.L4_SILENT && data.successRate > 0.9 && data.consecutiveSuccesses >= 10) {
            recommendations.add(new Recommendation(
                    "SUCCESS",
                    "Ready for Promotion",
                    "Trust metrics look excellent! Consider promoting to the next trust level."
            ));
        }

        return recommendations;
    }

    private void appendMetricCard(StringBuilder html, String title, String value, String type) {
        html.append("<div class=\"metric-card metric-").append(type).append("\">\n");
        html.append("<div class=\"metric-value\">").append(value).append("</div>\n");
        html.append("<div class=\"metric-title\">").append(title).append("</div>\n");
        html.append("</div>\n");
    }

    private String formatCalibrationData(List<ConfidenceCalibrator.CalibrationPoint> points) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(",");
            ConfidenceCalibrator.CalibrationPoint p = points.get(i);
            sb.append(String.format("{x:%.2f,y:%.2f,n:%d}",
                    p.predictedProbability(), p.actualFrequency(), p.sampleCount()));
        }
        sb.append("]");
        return sb.toString();
    }

    private String getDashboardStyles() {
        return """
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0f2f5; color: #333; }
            .dashboard { max-width: 1400px; margin: 0 auto; padding: 20px; }
            header { text-align: center; margin-bottom: 30px; }
            header h1 { font-size: 2.5em; color: #1a73e8; }
            .timestamp { color: #666; margin-top: 5px; }

            .trust-level-section { margin-bottom: 30px; }
            .trust-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 16px; text-align: center; box-shadow: 0 10px 30px rgba(102,126,234,0.3); }
            .trust-card.trust-l0_shadow { background: linear-gradient(135deg, #636e72 0%, #2d3436 100%); }
            .trust-card.trust-l1_manual { background: linear-gradient(135deg, #f39c12 0%, #e74c3c 100%); }
            .trust-card.trust-l2_suggest { background: linear-gradient(135deg, #3498db 0%, #2980b9 100%); }
            .trust-card.trust-l3_auto_safe { background: linear-gradient(135deg, #27ae60 0%, #2ecc71 100%); }
            .trust-card.trust-l4_silent { background: linear-gradient(135deg, #9b59b6 0%, #8e44ad 100%); }
            .trust-badge { font-size: 4em; margin-bottom: 10px; }
            .trust-card h2 { font-size: 2em; margin-bottom: 10px; }
            .trust-stats { margin-top: 20px; display: flex; justify-content: center; gap: 30px; }
            .trust-stats span { background: rgba(255,255,255,0.2); padding: 8px 16px; border-radius: 20px; }
            .next-level { margin-top: 15px; opacity: 0.9; font-style: italic; }

            .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 20px; margin-bottom: 30px; }
            .metric-card { background: white; padding: 25px; border-radius: 12px; text-align: center; box-shadow: 0 2px 10px rgba(0,0,0,0.08); }
            .metric-value { font-size: 2.2em; font-weight: bold; margin-bottom: 5px; }
            .metric-title { color: #666; font-size: 0.9em; }
            .metric-good .metric-value { color: #27ae60; }
            .metric-warning .metric-value { color: #f39c12; }
            .metric-bad .metric-value { color: #e74c3c; }
            .metric-neutral .metric-value { color: #3498db; }

            section { background: white; padding: 25px; border-radius: 12px; margin-bottom: 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.08); }
            section h2 { color: #333; margin-bottom: 20px; border-bottom: 2px solid #eee; padding-bottom: 10px; }

            .calibration-info p { margin: 8px 0; }
            .calibration-chart { margin-top: 20px; height: 300px; }

            .feedback-stats { display: flex; gap: 30px; margin-bottom: 20px; }
            .feedback-item { text-align: center; padding: 15px 25px; border-radius: 8px; }
            .feedback-item span { display: block; font-size: 2em; font-weight: bold; }
            .feedback-item label { color: #666; }
            .feedback-item.positive { background: #d4edda; color: #155724; }
            .feedback-item.negative { background: #f8d7da; color: #721c24; }
            .feedback-item.corrections { background: #fff3cd; color: #856404; }

            .recommendations-section ul { list-style: none; }
            .recommendations-section li { padding: 15px; margin: 10px 0; border-radius: 8px; border-left: 4px solid; }
            .rec-info { background: #e7f3ff; border-color: #3498db; }
            .rec-warning { background: #fff3cd; border-color: #f39c12; }
            .rec-error { background: #f8d7da; border-color: #e74c3c; }
            .rec-success { background: #d4edda; border-color: #27ae60; }

            .trust-progression { display: flex; justify-content: space-between; align-items: center; padding: 20px 0; }
            .level-marker { text-align: center; padding: 15px; flex: 1; position: relative; }
            .level-marker::after { content: '→'; position: absolute; right: -10px; top: 50%; transform: translateY(-50%); color: #ccc; }
            .level-marker:last-child::after { display: none; }
            .level-name { display: block; font-size: 0.8em; color: #999; margin-top: 5px; }
            .level-icon { font-size: 2em; display: block; }
            .level-marker.achieved .level-icon { opacity: 0.5; }
            .level-marker.current { background: #e7f3ff; border-radius: 8px; }
            .level-marker.current .level-name { color: #1a73e8; font-weight: bold; }
            .level-marker.pending { opacity: 0.4; }
            """;
    }

    private String getChartScript() {
        return """
            function drawCalibrationChart(data) {
                const canvas = document.getElementById('calibrationChart');
                if (!canvas) return;
                const ctx = canvas.getContext('2d');
                const w = canvas.width = canvas.parentElement.offsetWidth;
                const h = canvas.height = 300;
                const padding = 50;

                ctx.fillStyle = '#f8f9fa';
                ctx.fillRect(0, 0, w, h);

                // Draw diagonal (perfect calibration)
                ctx.strokeStyle = '#ddd';
                ctx.setLineDash([5, 5]);
                ctx.beginPath();
                ctx.moveTo(padding, h - padding);
                ctx.lineTo(w - padding, padding);
                ctx.stroke();
                ctx.setLineDash([]);

                // Draw axes
                ctx.strokeStyle = '#333';
                ctx.beginPath();
                ctx.moveTo(padding, padding);
                ctx.lineTo(padding, h - padding);
                ctx.lineTo(w - padding, h - padding);
                ctx.stroke();

                // Labels
                ctx.fillStyle = '#666';
                ctx.font = '12px sans-serif';
                ctx.textAlign = 'center';
                ctx.fillText('Predicted Probability', w/2, h - 10);
                ctx.save();
                ctx.translate(15, h/2);
                ctx.rotate(-Math.PI/2);
                ctx.fillText('Actual Frequency', 0, 0);
                ctx.restore();

                // Plot points
                ctx.fillStyle = '#3498db';
                data.forEach(p => {
                    const x = padding + (p.x * (w - 2*padding));
                    const y = h - padding - (p.y * (h - 2*padding));
                    const r = Math.min(15, Math.max(5, p.n / 5));
                    ctx.beginPath();
                    ctx.arc(x, y, r, 0, Math.PI * 2);
                    ctx.fill();
                });
            }
            """;
    }

    // Inner types

    public static class DashboardData {
        public Instant generatedAt;

        // Trust
        public TrustLevel currentTrustLevel;
        public String trustLevelName;
        public String trustLevelDescription;
        public int consecutiveSuccesses;
        public int consecutiveFailures;
        public int totalSuccesses;
        public int totalFailures;
        public double successRate;
        public String nextLevelRequirements;

        // Metrics
        public int totalHeals;
        public int successfulHeals;
        public int refusedHeals;
        public int failedHeals;
        public double averageConfidence;
        public double averageLatencyMs;
        public double totalLlmCost;
        public double falseHealRate;

        // Calibration
        public int calibrationSamples;
        public double calibrationError;
        public boolean isWellCalibrated;
        public double recommendedThreshold;
        public List<ConfidenceCalibrator.CalibrationPoint> calibrationCurve;

        // Feedback
        public int feedbackTotal;
        public int feedbackPositive;
        public int feedbackNegative;
        public int feedbackCorrections;
        public double userAccuracyRate;

        public List<Recommendation> recommendations = new ArrayList<>();
    }

    public record Recommendation(String severity, String title, String description) {}
}
