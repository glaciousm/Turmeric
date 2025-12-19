package io.github.glaciousm.report;

import io.github.glaciousm.report.HealingAnalytics.AnalyticsSummary;
import io.github.glaciousm.report.HealingAnalytics.FrequentLocator;
import io.github.glaciousm.report.HealingAnalytics.TrendDataPoint;
import io.github.glaciousm.report.model.HealEvent;
import io.github.glaciousm.report.model.HealReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HealingAnalytics.
 */
@DisplayName("HealingAnalytics Tests")
class HealingAnalyticsTest {

    private HealingAnalytics analytics;

    @BeforeEach
    void setUp() {
        analytics = new HealingAnalytics();
    }

    @Nested
    @DisplayName("Single Report Analysis")
    class SingleReportAnalysisTests {

        @Test
        @DisplayName("should handle null report")
        void handlesNullReport() {
            AnalyticsSummary summary = analytics.analyzeReport(null);

            assertEquals(0, summary.totalHeals());
            assertEquals(0, summary.successfulHeals());
            assertEquals(0.0, summary.successRate());
        }

        @Test
        @DisplayName("should handle empty report")
        void handlesEmptyReport() {
            HealReport report = new HealReport();
            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertEquals(0, summary.totalHeals());
            assertEquals(Duration.ZERO, summary.estimatedTimeSaved());
        }

        @Test
        @DisplayName("should calculate success rate correctly")
        void calculatesSuccessRate() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("SUCCESS", 0.85, 0.01),
                    createEvent("FAILED", 0.3, 0.01),
                    createEvent("REFUSED", 0.0, 0.0)
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertEquals(4, summary.totalHeals());
            assertEquals(2, summary.successfulHeals());
            assertEquals(1, summary.failedHeals());
            assertEquals(1, summary.refusedHeals());
            assertEquals(50.0, summary.successRate(), 0.1);
        }

        @Test
        @DisplayName("should calculate average confidence")
        void calculatesAverageConfidence() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("SUCCESS", 0.8, 0.01),
                    createEvent("SUCCESS", 0.7, 0.01)
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertEquals(0.8, summary.averageConfidence(), 0.01);
        }

        @Test
        @DisplayName("should calculate total cost")
        void calculatesTotalCost() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("SUCCESS", 0.8, 0.02),
                    createEvent("SUCCESS", 0.7, 0.03)
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertEquals(0.06, summary.totalCostUsd(), 0.001);
            assertEquals(0.02, summary.averageCostPerHeal(), 0.001);
        }

        @Test
        @DisplayName("should estimate time saved")
        void estimatesTimeSaved() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("SUCCESS", 0.85, 0.01),
                    createEvent("FAILED", 0.3, 0.01) // Failed heals don't count
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);

            // 2 successful heals * 15 minutes each = 30 minutes
            assertEquals(30, summary.estimatedTimeSaved().toMinutes());
        }

        @Test
        @DisplayName("should track confidence distribution")
        void tracksConfidenceDistribution() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.95, 0.01), // 80-100%
                    createEvent("SUCCESS", 0.85, 0.01), // 80-100%
                    createEvent("SUCCESS", 0.65, 0.01), // 60-80%
                    createEvent("SUCCESS", 0.35, 0.01)  // 20-40%
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertEquals(50.0, summary.confidenceDistribution().get("80-100%"), 0.1);
            assertEquals(25.0, summary.confidenceDistribution().get("60-80%"), 0.1);
            assertEquals(25.0, summary.confidenceDistribution().get("20-40%"), 0.1);
        }
    }

    @Nested
    @DisplayName("ROI Calculation Tests")
    class ROICalculationTests {

        @Test
        @DisplayName("should calculate cost savings")
        void calculatesCostSavings() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.10),
                    createEvent("SUCCESS", 0.85, 0.10)
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);

            // 2 heals * 15 min = 30 min = 0.5 hours
            // At $100/hr: savings = 0.5 * 100 - 0.20 (cost) = $49.80
            double savings = summary.getEstimatedCostSavings(100.0);
            assertEquals(49.80, savings, 0.01);
        }

        @Test
        @DisplayName("should calculate ROI percentage")
        void calculatesROI() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.10),
                    createEvent("SUCCESS", 0.85, 0.10)
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);

            // Savings = $49.80, Cost = $0.20
            // ROI = (49.80 / 0.20) * 100 = 24900%
            double roi = summary.getROI(100.0);
            assertEquals(24900.0, roi, 1.0);
        }

        @Test
        @DisplayName("should handle zero cost gracefully")
        void handlesZeroCost() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.0)
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertEquals(0.0, summary.getROI(100.0));
        }
    }

    @Nested
    @DisplayName("Frequent Locators Tests")
    class FrequentLocatorsTests {

        @Test
        @DisplayName("should identify frequently healed locators")
        void identifiesFrequentLocators() {
            HealEvent event1 = createEvent("SUCCESS", 0.9, 0.01);
            event1.setFailure(createFailure("#login-btn"));

            HealEvent event2 = createEvent("SUCCESS", 0.8, 0.01);
            event2.setFailure(createFailure("#login-btn"));

            HealEvent event3 = createEvent("SUCCESS", 0.85, 0.01);
            event3.setFailure(createFailure("#login-btn"));

            HealEvent event4 = createEvent("SUCCESS", 0.9, 0.01);
            event4.setFailure(createFailure("#submit-btn"));

            HealReport report = createReportWithEvents(event1, event2, event3, event4);

            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertFalse(summary.mostFrequentlyHealedLocators().isEmpty());
            FrequentLocator topLocator = summary.mostFrequentlyHealedLocators().get(0);
            assertEquals("#login-btn", topLocator.locator());
            assertEquals(3, topLocator.healCount());
        }

        @Test
        @DisplayName("should exclude single-occurrence locators")
        void excludesSingleOccurrenceLocators() {
            HealEvent event1 = createEvent("SUCCESS", 0.9, 0.01);
            event1.setFailure(createFailure("#unique-btn"));

            HealReport report = createReportWithEvents(event1);

            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertTrue(summary.mostFrequentlyHealedLocators().isEmpty());
        }
    }

    @Nested
    @DisplayName("Action Type Categorization Tests")
    class ActionTypeCategoryTests {

        @Test
        @DisplayName("should categorize click actions")
        void categorizesClickActions() {
            HealEvent event = createEvent("SUCCESS", 0.9, 0.01);
            event.setStep("I click the submit button");

            HealReport report = createReportWithEvents(event);
            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertTrue(summary.healsByActionType().containsKey("Click"));
            assertEquals(1, summary.healsByActionType().get("Click"));
        }

        @Test
        @DisplayName("should categorize input actions")
        void categorizesInputActions() {
            HealEvent event = createEvent("SUCCESS", 0.9, 0.01);
            event.setStep("I type 'hello' into the username field");

            HealReport report = createReportWithEvents(event);
            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertTrue(summary.healsByActionType().containsKey("Input"));
        }

        @Test
        @DisplayName("should categorize assertion actions")
        void categorizesAssertionActions() {
            HealEvent event = createEvent("SUCCESS", 0.9, 0.01);
            event.setStep("I should see the welcome message");

            HealReport report = createReportWithEvents(event);
            AnalyticsSummary summary = analytics.analyzeReport(report);

            assertTrue(summary.healsByActionType().containsKey("Assertion"));
        }
    }

    @Nested
    @DisplayName("Multiple Reports Analysis")
    class MultipleReportsAnalysisTests {

        @Test
        @DisplayName("should combine events from multiple reports")
        void combinesEventsFromMultipleReports() {
            HealReport report1 = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("SUCCESS", 0.85, 0.01)
            );
            report1.setTimestamp(Instant.now().minusSeconds(3600));

            HealReport report2 = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("FAILED", 0.3, 0.01)
            );
            report2.setTimestamp(Instant.now());

            AnalyticsSummary summary = analytics.analyzeReports(List.of(report1, report2));

            assertEquals(4, summary.totalHeals());
            assertEquals(3, summary.successfulHeals());
            assertEquals(1, summary.failedHeals());
        }

        @Test
        @DisplayName("should generate trend from multiple reports")
        void generatesTrendFromMultipleReports() {
            HealReport report1 = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("FAILED", 0.3, 0.01)
            );
            report1.setTimestamp(Instant.now().minusSeconds(7200));

            HealReport report2 = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("SUCCESS", 0.85, 0.01)
            );
            report2.setTimestamp(Instant.now().minusSeconds(3600));

            HealReport report3 = createReportWithEvents(
                    createEvent("SUCCESS", 0.95, 0.01),
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("SUCCESS", 0.85, 0.01)
            );
            report3.setTimestamp(Instant.now());

            AnalyticsSummary summary = analytics.analyzeReports(List.of(report1, report2, report3));

            assertEquals(3, summary.successRateTrend().size());

            // Verify trend is ordered by timestamp
            List<TrendDataPoint> trend = summary.successRateTrend();
            assertTrue(trend.get(0).timestamp().isBefore(trend.get(1).timestamp()));
            assertTrue(trend.get(1).timestamp().isBefore(trend.get(2).timestamp()));

            // Verify success rates
            assertEquals(50.0, trend.get(0).successRate(), 0.1);  // report1: 1/2
            assertEquals(100.0, trend.get(1).successRate(), 0.1); // report2: 2/2
            assertEquals(100.0, trend.get(2).successRate(), 0.1); // report3: 3/3
        }

        @Test
        @DisplayName("should handle empty reports list")
        void handlesEmptyReportsList() {
            AnalyticsSummary summary = analytics.analyzeReports(Collections.emptyList());

            assertEquals(0, summary.totalHeals());
            assertTrue(summary.successRateTrend().isEmpty());
        }
    }

    @Nested
    @DisplayName("Text Formatting Tests")
    class TextFormattingTests {

        @Test
        @DisplayName("should format analytics as text")
        void formatsAnalyticsAsText() {
            HealReport report = createReportWithEvents(
                    createEvent("SUCCESS", 0.9, 0.01),
                    createEvent("SUCCESS", 0.85, 0.02),
                    createEvent("FAILED", 0.3, 0.01)
            );

            AnalyticsSummary summary = analytics.analyzeReport(report);
            String text = analytics.formatAsText(summary, 100.0);

            assertNotNull(text);
            assertTrue(text.contains("Healing Analytics Summary"));
            assertTrue(text.contains("Total Heals: 3"));
            assertTrue(text.contains("Successful: 2"));
            assertTrue(text.contains("ROI"));
        }
    }

    // Helper methods

    private HealReport createReportWithEvents(HealEvent... events) {
        HealReport report = new HealReport();
        report.setTimestamp(Instant.now());
        for (HealEvent event : events) {
            report.addEvent(event);
        }
        return report;
    }

    private HealEvent createEvent(String status, double confidence, double cost) {
        HealEvent event = new HealEvent();

        HealEvent.DecisionInfo decision = new HealEvent.DecisionInfo();
        decision.setConfidence(confidence);
        decision.setCanHeal("SUCCESS".equals(status) || "FAILED".equals(status));
        event.setDecision(decision);

        HealEvent.ResultInfo result = new HealEvent.ResultInfo();
        result.setStatus(status);
        event.setResult(result);

        HealEvent.CostInfo costInfo = new HealEvent.CostInfo();
        costInfo.setCostUsd(cost);
        event.setCost(costInfo);

        event.setStep("I perform an action");
        event.setFeature("Test Feature");
        event.setScenario("Test Scenario");

        return event;
    }

    private HealEvent.FailureInfo createFailure(String locator) {
        HealEvent.FailureInfo failure = new HealEvent.FailureInfo();
        failure.setOriginalLocator(locator);
        failure.setExceptionType("NoSuchElementException");
        failure.setMessage("Element not found");
        return failure;
    }
}
