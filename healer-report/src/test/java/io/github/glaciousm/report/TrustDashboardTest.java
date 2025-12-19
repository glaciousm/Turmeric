package io.github.glaciousm.report;

import io.github.glaciousm.core.engine.calibration.ConfidenceCalibrator;
import io.github.glaciousm.core.engine.metrics.HealMetricsCollector;
import io.github.glaciousm.core.engine.trust.TrustLevel;
import io.github.glaciousm.core.engine.trust.TrustLevelManager;
import io.github.glaciousm.core.feedback.FeedbackApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for the TrustDashboard class.
 */
@ExtendWith(MockitoExtension.class)
class TrustDashboardTest {

    @Mock
    private TrustLevelManager trustManager;

    @Mock
    private HealMetricsCollector metricsCollector;

    @Mock
    private ConfidenceCalibrator calibrator;

    @Mock
    private FeedbackApi feedbackApi;

    @TempDir
    Path tempDir;

    private TrustDashboard dashboard;

    @BeforeEach
    void setUp() {
        dashboard = new TrustDashboard(trustManager, metricsCollector, calibrator, feedbackApi);
    }

    @Test
    void testGenerateDashboardReturnsData() {
        setupMocks();

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data).isNotNull();
        assertThat(data.generatedAt).isNotNull();
    }

    @Test
    void testGenerateDashboardIncludesTrustLevel() {
        setupMocks();
        when(trustManager.getCurrentLevel()).thenReturn(TrustLevel.L2_SAFE);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.currentTrustLevel).isEqualTo(TrustLevel.L2_SAFE);
        assertThat(data.trustLevelName).isEqualTo("L2_SAFE");
        assertThat(data.trustLevelDescription).isNotEmpty();
    }

    @Test
    void testGenerateDashboardIncludesConsecutiveCounts() {
        setupMocks();
        when(trustManager.getConsecutiveSuccesses()).thenReturn(7);
        when(trustManager.getConsecutiveFailures()).thenReturn(0);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.consecutiveSuccesses).isEqualTo(7);
        assertThat(data.consecutiveFailures).isEqualTo(0);
    }

    @Test
    void testGenerateDashboardIncludesTotalCounts() {
        setupMocks();
        when(trustManager.getTotalSuccesses()).thenReturn(50);
        when(trustManager.getTotalFailures()).thenReturn(5);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.totalSuccesses).isEqualTo(50);
        assertThat(data.totalFailures).isEqualTo(5);
    }

    @Test
    void testGenerateDashboardCalculatesSuccessRate() {
        setupMocks();
        when(trustManager.getTotalSuccesses()).thenReturn(80);
        when(trustManager.getTotalFailures()).thenReturn(20);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.successRate).isEqualTo(0.8);
    }

    @Test
    void testGenerateDashboardSuccessRateWithNoData() {
        setupMocks();
        when(trustManager.getTotalSuccesses()).thenReturn(0);
        when(trustManager.getTotalFailures()).thenReturn(0);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.successRate).isEqualTo(0.0);
    }

    @Test
    void testGenerateDashboardIncludesMetrics() {
        setupMocks();
        when(metricsCollector.getAverageLatency()).thenReturn(150.5);
        when(metricsCollector.getTotalLlmCostUsd()).thenReturn(0.025);
        when(metricsCollector.getFalseHealRate()).thenReturn(0.05);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.averageLatencyMs).isEqualTo(150.5);
        assertThat(data.totalLlmCost).isEqualTo(0.025);
        assertThat(data.falseHealRate).isEqualTo(0.05);
    }

    @Test
    void testGenerateDashboardIncludesCalibrationStats() {
        setupMocks();
        ConfidenceCalibrator.CalibrationStats calStats =
                new ConfidenceCalibrator.CalibrationStats(100, 0.85, 0.82, 0.05, true, Map.of());
        when(calibrator.getStats()).thenReturn(calStats);
        when(calibrator.getRecommendedThreshold(0.9)).thenReturn(0.82);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.calibrationSamples).isEqualTo(100);
        assertThat(data.calibrationError).isEqualTo(0.05);
        assertThat(data.isWellCalibrated).isTrue();
        assertThat(data.recommendedThreshold).isEqualTo(0.82);
    }

    @Test
    void testGenerateDashboardIncludesFeedbackStats() {
        setupMocks();
        FeedbackApi.FeedbackStats fbStats =
                new FeedbackApi.FeedbackStats(100, 85, 10, 5, 0);
        when(feedbackApi.getStats()).thenReturn(fbStats);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.feedbackTotal).isEqualTo(100);
        assertThat(data.feedbackPositive).isEqualTo(85);
        assertThat(data.feedbackNegative).isEqualTo(10);
        assertThat(data.feedbackCorrections).isEqualTo(5);
    }

    @Test
    void testGenerateDashboardIncludesRecommendations() {
        setupMocks();
        when(trustManager.getCurrentLevel()).thenReturn(TrustLevel.L0_SHADOW);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.recommendations).isNotEmpty();
        assertThat(data.recommendations).anyMatch(r -> r.title().contains("Trust"));
    }

    @Test
    void testGenerateDashboardRecommendsOnHighFalseHealRate() {
        setupMocks();
        when(metricsCollector.getFalseHealRate()).thenReturn(0.25);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.recommendations).anyMatch(r ->
                r.severity().equals("WARNING") && r.title().contains("False Heal"));
    }

    @Test
    void testGenerateDashboardRecommendsOnConsecutiveFailures() {
        setupMocks();
        when(trustManager.getConsecutiveFailures()).thenReturn(5);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.recommendations).anyMatch(r ->
                r.severity().equals("ERROR") && r.title().contains("Consecutive Failures"));
    }

    @Test
    void testGenerateDashboardRecommendsPromotion() {
        setupMocks();
        when(trustManager.getCurrentLevel()).thenReturn(TrustLevel.L2_SAFE);
        when(trustManager.getTotalSuccesses()).thenReturn(100);
        when(trustManager.getTotalFailures()).thenReturn(5);
        when(trustManager.getConsecutiveSuccesses()).thenReturn(15);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.recommendations).anyMatch(r ->
                r.severity().equals("SUCCESS") && r.title().contains("Promotion"));
    }

    @Test
    void testGenerateHtmlDashboardReturnsHtml() {
        setupMocks();

        String html = dashboard.generateHtmlDashboard();

        assertThat(html).isNotNull();
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("Intent Healer Trust Dashboard");
    }

    @Test
    void testGenerateHtmlDashboardContainsTrustInfo() {
        setupMocks();
        when(trustManager.getCurrentLevel()).thenReturn(TrustLevel.L3_AUTO);

        String html = dashboard.generateHtmlDashboard();

        assertThat(html).contains("L3_AUTO");
        assertThat(html).contains("trust-card");
    }

    @Test
    void testGenerateHtmlDashboardContainsMetrics() {
        setupMocks();

        String html = dashboard.generateHtmlDashboard();

        assertThat(html).contains("Success Rate");
        assertThat(html).contains("LLM Cost");
        assertThat(html).contains("Avg Latency");
    }

    @Test
    void testGenerateHtmlDashboardContainsCalibrationSection() {
        setupMocks();

        String html = dashboard.generateHtmlDashboard();

        assertThat(html).contains("Confidence Calibration");
    }

    @Test
    void testGenerateHtmlDashboardContainsFeedbackSection() {
        setupMocks();

        String html = dashboard.generateHtmlDashboard();

        assertThat(html).contains("User Feedback");
    }

    @Test
    void testGenerateHtmlDashboardContainsRecommendations() {
        setupMocks();
        when(trustManager.getCurrentLevel()).thenReturn(TrustLevel.L0_SHADOW);

        String html = dashboard.generateHtmlDashboard();

        assertThat(html).contains("Recommendations");
    }

    @Test
    void testGenerateHtmlDashboardContainsTrustProgression() {
        setupMocks();

        String html = dashboard.generateHtmlDashboard();

        assertThat(html).contains("Trust Level Progression");
        assertThat(html).contains("level-marker");
    }

    @Test
    void testSaveDashboardToFile() throws IOException {
        setupMocks();
        Path outputPath = tempDir.resolve("dashboard.html");

        dashboard.saveDashboard(outputPath);

        assertThat(Files.exists(outputPath)).isTrue();
        String content = Files.readString(outputPath);
        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("Intent Healer Trust Dashboard");
    }

    @Test
    void testDashboardWithNullTrustManager() {
        TrustDashboard nullTrustDashboard = new TrustDashboard(null, metricsCollector, calibrator, feedbackApi);
        setupMetricsMocks();
        setupCalibratorMocks();
        setupFeedbackMocks();

        TrustDashboard.DashboardData data = nullTrustDashboard.generateDashboard();

        assertThat(data).isNotNull();
        assertThat(data.currentTrustLevel).isNull();
    }

    @Test
    void testDashboardWithNullMetricsCollector() {
        TrustDashboard nullMetricsDashboard = new TrustDashboard(trustManager, null, calibrator, feedbackApi);
        setupTrustMocks();
        setupCalibratorMocks();
        setupFeedbackMocks();

        TrustDashboard.DashboardData data = nullMetricsDashboard.generateDashboard();

        assertThat(data).isNotNull();
        assertThat(data.averageLatencyMs).isEqualTo(0.0);
    }

    @Test
    void testDashboardWithNullCalibrator() {
        TrustDashboard nullCalibratorDashboard = new TrustDashboard(trustManager, metricsCollector, null, feedbackApi);
        setupTrustMocks();
        setupMetricsMocks();
        setupFeedbackMocks();

        TrustDashboard.DashboardData data = nullCalibratorDashboard.generateDashboard();

        assertThat(data).isNotNull();
        assertThat(data.calibrationSamples).isEqualTo(0);
    }

    @Test
    void testDashboardWithNullFeedbackApi() {
        TrustDashboard nullFeedbackDashboard = new TrustDashboard(trustManager, metricsCollector, calibrator, null);
        setupTrustMocks();
        setupMetricsMocks();
        setupCalibratorMocks();

        TrustDashboard.DashboardData data = nullFeedbackDashboard.generateDashboard();

        assertThat(data).isNotNull();
        assertThat(data.feedbackTotal).isEqualTo(0);
    }

    @Test
    void testNextLevelRequirementsForL0() {
        setupMocks();
        when(trustManager.getCurrentLevel()).thenReturn(TrustLevel.L0_SHADOW);
        when(trustManager.getConsecutiveSuccesses()).thenReturn(3);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.nextLevelRequirements).contains("consecutive successes");
    }

    @Test
    void testNextLevelRequirementsForL4() {
        setupMocks();
        when(trustManager.getCurrentLevel()).thenReturn(TrustLevel.L4_SILENT);

        TrustDashboard.DashboardData data = dashboard.generateDashboard();

        assertThat(data.nextLevelRequirements).contains("Maximum");
    }

    private void setupMocks() {
        setupTrustMocks();
        setupMetricsMocks();
        setupCalibratorMocks();
        setupFeedbackMocks();
    }

    private void setupTrustMocks() {
        when(trustManager.getCurrentLevel()).thenReturn(TrustLevel.L2_SAFE);
        when(trustManager.getConsecutiveSuccesses()).thenReturn(5);
        when(trustManager.getConsecutiveFailures()).thenReturn(0);
        when(trustManager.getTotalSuccesses()).thenReturn(50);
        when(trustManager.getTotalFailures()).thenReturn(5);
    }

    private void setupMetricsMocks() {
        when(metricsCollector.getAverageLatency()).thenReturn(100.0);
        when(metricsCollector.getTotalLlmCostUsd()).thenReturn(0.01);
        when(metricsCollector.getFalseHealRate()).thenReturn(0.05);
    }

    private void setupCalibratorMocks() {
        ConfidenceCalibrator.CalibrationStats stats =
                new ConfidenceCalibrator.CalibrationStats(50, 0.85, 0.82, 0.08, true, Map.of());
        when(calibrator.getStats()).thenReturn(stats);
        when(calibrator.getRecommendedThreshold(anyDouble())).thenReturn(0.8);
        when(calibrator.getCalibrationCurve()).thenReturn(List.of());
    }

    private void setupFeedbackMocks() {
        FeedbackApi.FeedbackStats stats =
                new FeedbackApi.FeedbackStats(30, 25, 3, 2, 0);
        when(feedbackApi.getStats()).thenReturn(stats);
    }
}
