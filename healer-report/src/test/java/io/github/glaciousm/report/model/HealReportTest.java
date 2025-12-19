package io.github.glaciousm.report.model;

import io.github.glaciousm.report.model.HealEvent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the HealReport class.
 */
class HealReportTest {

    private HealReport report;

    @BeforeEach
    void setUp() {
        report = new HealReport();
    }

    @Test
    void testDefaultRunIdGenerated() {
        assertThat(report.getRunId()).isNotNull();
        assertThat(report.getRunId()).isNotEmpty();
    }

    @Test
    void testDefaultTimestampSet() {
        assertThat(report.getTimestamp()).isNotNull();
    }

    @Test
    void testDefaultEventsListEmpty() {
        assertThat(report.getEvents()).isEmpty();
    }

    @Test
    void testDefaultSummaryInitialized() {
        assertThat(report.getSummary()).isNotNull();
        assertThat(report.getSummary().getHealAttempts()).isEqualTo(0);
    }

    @Test
    void testAddEventIncrementsAttempts() {
        HealEvent event = createEvent("SUCCESS", 0.9, 0.0);
        report.addEvent(event);

        assertThat(report.getSummary().getHealAttempts()).isEqualTo(1);
        assertThat(report.getEvents()).hasSize(1);
    }

    @Test
    void testAddSuccessEventIncrementsSuccesses() {
        report.addEvent(createEvent("SUCCESS", 0.9, 0.0));
        report.addEvent(createEvent("SUCCESS", 0.85, 0.0));

        assertThat(report.getSummary().getHealSuccesses()).isEqualTo(2);
    }

    @Test
    void testAddRefusedEventIncrementsRefusals() {
        report.addEvent(createEvent("REFUSED", 0.0, 0.0));

        assertThat(report.getSummary().getHealRefusals()).isEqualTo(1);
    }

    @Test
    void testAddFailedEventIncrementsFailures() {
        report.addEvent(createEvent("FAILED", 0.0, 0.0));

        assertThat(report.getSummary().getHealFailures()).isEqualTo(1);
    }

    @Test
    void testAddEventAccumulatesCost() {
        report.addEvent(createEvent("SUCCESS", 0.9, 0.001));
        report.addEvent(createEvent("SUCCESS", 0.85, 0.002));
        report.addEvent(createEvent("SUCCESS", 0.88, 0.0015));

        assertThat(report.getSummary().getTotalLlmCostUsd()).isEqualTo(0.0045, org.assertj.core.api.Assertions.within(0.0001));
    }

    @Test
    void testMixedOutcomes() {
        report.addEvent(createEvent("SUCCESS", 0.9, 0.001));
        report.addEvent(createEvent("SUCCESS", 0.85, 0.002));
        report.addEvent(createEvent("REFUSED", 0.0, 0.0));
        report.addEvent(createEvent("FAILED", 0.0, 0.001));

        HealReport.ReportSummary summary = report.getSummary();
        assertThat(summary.getHealAttempts()).isEqualTo(4);
        assertThat(summary.getHealSuccesses()).isEqualTo(2);
        assertThat(summary.getHealRefusals()).isEqualTo(1);
        assertThat(summary.getHealFailures()).isEqualTo(1);
    }

    @Test
    void testSetAndGetRunId() {
        report.setRunId("custom-run-id");
        assertThat(report.getRunId()).isEqualTo("custom-run-id");
    }

    @Test
    void testSetAndGetTimestamp() {
        Instant customTime = Instant.parse("2024-01-15T10:30:00Z");
        report.setTimestamp(customTime);
        assertThat(report.getTimestamp()).isEqualTo(customTime);
    }

    @Test
    void testSetAndGetDurationMs() {
        report.setDurationMs(5000L);
        assertThat(report.getDurationMs()).isEqualTo(5000L);
    }

    @Test
    void testSetAndGetTestName() {
        report.setTestName("LoginTest");
        assertThat(report.getTestName()).isEqualTo("LoginTest");
    }

    @Test
    void testSetAndGetTestStatus() {
        report.setTestStatus("PASSED");
        assertThat(report.getTestStatus()).isEqualTo("PASSED");
    }

    @Test
    void testSetAndGetStartTime() {
        Instant startTime = Instant.now();
        report.setStartTime(startTime);
        assertThat(report.getStartTime()).isEqualTo(startTime);
    }

    @Test
    void testSetAndGetEndTime() {
        Instant endTime = Instant.now();
        report.setEndTime(endTime);
        assertThat(report.getEndTime()).isEqualTo(endTime);
    }

    @Test
    void testConfigSummary() {
        HealReport.ConfigSummary configSummary = new HealReport.ConfigSummary();
        configSummary.setMode("AUTO_SAFE");
        configSummary.setLlmProvider("openai");
        configSummary.setLlmModel("gpt-4");
        configSummary.setConfidenceThreshold(0.8);

        report.setConfig(configSummary);

        assertThat(report.getConfig()).isNotNull();
        assertThat(report.getConfig().getMode()).isEqualTo("AUTO_SAFE");
        assertThat(report.getConfig().getLlmProvider()).isEqualTo("openai");
        assertThat(report.getConfig().getLlmModel()).isEqualTo("gpt-4");
        assertThat(report.getConfig().getConfidenceThreshold()).isEqualTo(0.8);
    }

    @Test
    void testReportSummarySetters() {
        HealReport.ReportSummary summary = new HealReport.ReportSummary();
        summary.setTotalSteps(100);
        summary.setTotalFailures(10);
        summary.setHealAttempts(8);
        summary.setHealSuccesses(6);
        summary.setHealRefusals(1);
        summary.setHealFailures(1);
        summary.setTotalLlmCostUsd(0.05);

        report.setSummary(summary);

        assertThat(report.getSummary().getTotalSteps()).isEqualTo(100);
        assertThat(report.getSummary().getTotalFailures()).isEqualTo(10);
        assertThat(report.getSummary().getHealAttempts()).isEqualTo(8);
        assertThat(report.getSummary().getHealSuccesses()).isEqualTo(6);
        assertThat(report.getSummary().getHealRefusals()).isEqualTo(1);
        assertThat(report.getSummary().getHealFailures()).isEqualTo(1);
        assertThat(report.getSummary().getTotalLlmCostUsd()).isEqualTo(0.05);
    }

    @Test
    void testSetEventsList() {
        java.util.List<HealEvent> events = new java.util.ArrayList<>();
        events.add(createEvent("SUCCESS", 0.9, 0.001));
        events.add(createEvent("SUCCESS", 0.85, 0.002));

        report.setEvents(events);

        assertThat(report.getEvents()).hasSize(2);
    }

    private HealEvent createEvent(String status, double confidence, double cost) {
        HealEvent event = new HealEvent();
        event.setStep("Test step");

        ResultInfo result = new ResultInfo();
        result.setStatus(status);
        event.setResult(result);

        DecisionInfo decision = new DecisionInfo();
        decision.setConfidence(confidence);
        event.setDecision(decision);

        if (cost > 0) {
            CostInfo costInfo = new CostInfo();
            costInfo.setCostUsd(cost);
            event.setCost(costInfo);
        }

        return event;
    }
}
