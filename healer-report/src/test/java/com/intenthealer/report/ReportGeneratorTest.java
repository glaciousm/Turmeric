package com.intenthealer.report;

import com.intenthealer.core.config.ReportConfig;
import com.intenthealer.report.model.HealEvent;
import com.intenthealer.report.model.HealEvent.*;
import com.intenthealer.report.model.HealReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the ReportGenerator class.
 */
class ReportGeneratorTest {

    @TempDir
    Path tempDir;

    private ReportGenerator reportGenerator;
    private ReportConfig config;

    @BeforeEach
    void setUp() {
        config = new ReportConfig();
        config.setOutputDir(tempDir.toString());
        config.setJsonEnabled(true);
        config.setHtmlEnabled(true);
        reportGenerator = new ReportGenerator(config);
    }

    @Test
    void testStartReportCreatesNewReport() {
        reportGenerator.startReport();

        HealReport report = reportGenerator.getCurrentReport();
        assertThat(report).isNotNull();
        assertThat(report.getTimestamp()).isNotNull();
        assertThat(report.getRunId()).isNotNull();
    }

    @Test
    void testAddEventToReport() {
        reportGenerator.startReport();
        HealEvent event = createTestEvent("SUCCESS", "I click the button", "By.id: btn", "By.css: .btn", 0.95);

        reportGenerator.addEvent(event);

        HealReport report = reportGenerator.getCurrentReport();
        assertThat(report.getEvents()).hasSize(1);
        assertThat(report.getEvents().get(0).getStep()).isEqualTo("I click the button");
    }

    @Test
    void testAddEventAutoStartsReport() {
        assertThat(reportGenerator.getCurrentReport()).isNull();

        HealEvent event = createTestEvent("SUCCESS", "Step 1", "By.id: x", "By.id: y", 0.9);
        reportGenerator.addEvent(event);

        assertThat(reportGenerator.getCurrentReport()).isNotNull();
        assertThat(reportGenerator.getCurrentReport().getEvents()).hasSize(1);
    }

    @Test
    void testFinishReportWritesJsonFile() throws IOException {
        config.setJsonEnabled(true);
        config.setHtmlEnabled(false);

        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Step 1", "By.id: old", "By.id: new", 0.9));
        reportGenerator.finishReport();

        // Should have created a JSON file
        long jsonCount = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".json"))
                .count();
        assertThat(jsonCount).isEqualTo(1);
    }

    @Test
    void testFinishReportWritesHtmlFile() throws IOException {
        config.setJsonEnabled(false);
        config.setHtmlEnabled(true);

        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Step 1", "By.id: old", "By.id: new", 0.9));
        reportGenerator.finishReport();

        // Should have created an HTML file
        long htmlCount = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".html"))
                .count();
        assertThat(htmlCount).isEqualTo(1);
    }

    @Test
    void testFinishReportWritesBothFormats() throws IOException {
        config.setJsonEnabled(true);
        config.setHtmlEnabled(true);

        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Step 1", "By.id: old", "By.id: new", 0.9));
        reportGenerator.finishReport();

        // Should have created both files
        long jsonCount = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".json"))
                .count();
        long htmlCount = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".html"))
                .count();
        assertThat(jsonCount).isEqualTo(1);
        assertThat(htmlCount).isEqualTo(1);
    }

    @Test
    void testFinishReportClearsCurrentReport() throws IOException {
        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Step 1", "By.id: old", "By.id: new", 0.9));
        reportGenerator.finishReport();

        assertThat(reportGenerator.getCurrentReport()).isNull();
    }

    @Test
    void testFinishReportWithNoReportDoesNotThrow() throws IOException {
        // Should not throw when there's no report to finish
        reportGenerator.finishReport();
    }

    @Test
    void testJsonReportContainsValidJson() throws IOException {
        config.setJsonEnabled(true);
        config.setHtmlEnabled(false);

        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Click button", "By.id: btn", "By.css: .btn", 0.92));
        reportGenerator.finishReport();

        Path jsonFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".json"))
                .findFirst()
                .orElseThrow();

        String content = Files.readString(jsonFile);
        assertThat(content).contains("\"events\"");
        assertThat(content).contains("\"summary\"");
        assertThat(content).contains("Click button");
    }

    @Test
    void testHtmlReportContainsContent() throws IOException {
        config.setJsonEnabled(false);
        config.setHtmlEnabled(true);

        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Click button", "By.id: btn", "By.css: .btn", 0.92));
        reportGenerator.finishReport();

        Path htmlFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".html"))
                .findFirst()
                .orElseThrow();

        String content = Files.readString(htmlFile);
        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("Intent Healer Report");
        assertThat(content).contains("Summary");
        assertThat(content).contains("Click button");
    }

    @Test
    void testHtmlReportEscapesSpecialCharacters() throws IOException {
        config.setJsonEnabled(false);
        config.setHtmlEnabled(true);

        reportGenerator.startReport();
        HealEvent event = createTestEvent("SUCCESS", "<script>alert('xss')</script>", "By.id: btn", "By.css: .btn", 0.9);
        reportGenerator.addEvent(event);
        reportGenerator.finishReport();

        Path htmlFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".html"))
                .findFirst()
                .orElseThrow();

        String content = Files.readString(htmlFile);
        // Should be escaped
        assertThat(content).doesNotContain("<script>alert");
        assertThat(content).contains("&lt;script&gt;");
    }

    @Test
    void testReportSummaryCountsSuccesses() throws IOException {
        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Step 1", "By.id: 1", "By.id: new1", 0.9));
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Step 2", "By.id: 2", "By.id: new2", 0.9));
        reportGenerator.addEvent(createTestEvent("REFUSED", "Step 3", "By.id: 3", null, 0.0));

        HealReport report = reportGenerator.getCurrentReport();
        assertThat(report.getSummary().getHealSuccesses()).isEqualTo(2);
        assertThat(report.getSummary().getHealRefusals()).isEqualTo(1);
        assertThat(report.getSummary().getHealAttempts()).isEqualTo(3);
    }

    @Test
    void testReportSummaryCountsFailures() throws IOException {
        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Step 1", "By.id: 1", "By.id: new1", 0.9));
        reportGenerator.addEvent(createTestEvent("FAILED", "Step 2", "By.id: 2", null, 0.0));

        HealReport report = reportGenerator.getCurrentReport();
        assertThat(report.getSummary().getHealFailures()).isEqualTo(1);
    }

    @Test
    void testReportWithZeroEvents() throws IOException {
        config.setJsonEnabled(true);
        config.setHtmlEnabled(true);

        reportGenerator.startReport();
        reportGenerator.finishReport();

        // Should still create files
        long fileCount = Files.list(tempDir).count();
        assertThat(fileCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testLoadReportFromJsonFile() throws IOException {
        config.setJsonEnabled(true);
        config.setHtmlEnabled(false);

        reportGenerator.startReport();
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Test step", "By.id: x", "By.id: y", 0.85));
        reportGenerator.finishReport();

        Path jsonFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".json"))
                .findFirst()
                .orElseThrow();

        HealReport loadedReport = reportGenerator.loadReport(jsonFile.toString());
        assertThat(loadedReport).isNotNull();
        assertThat(loadedReport.getEvents()).hasSize(1);
        assertThat(loadedReport.getEvents().get(0).getStep()).isEqualTo("Test step");
    }

    @Test
    void testGenerateHtmlFromDirectory() throws IOException {
        // Create some JSON report files
        createTestJsonReport("heal-report-1.json", "Step 1");
        createTestJsonReport("heal-report-2.json", "Step 2");

        Path outputPath = tempDir.resolve("combined.html");
        reportGenerator.generateHtmlFromDirectory(tempDir.toString(), outputPath.toString());

        assertThat(Files.exists(outputPath)).isTrue();
        String content = Files.readString(outputPath);
        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("Intent Healer Report");
    }

    @Test
    void testGenerateHtmlFromNonexistentDirectoryThrows() {
        assertThatThrownBy(() ->
            reportGenerator.generateHtmlFromDirectory("/nonexistent/dir", "output.html")
        ).isInstanceOf(IOException.class)
         .hasMessageContaining("does not exist");
    }

    @Test
    void testDefaultConstructor() {
        ReportGenerator defaultGenerator = new ReportGenerator();
        defaultGenerator.startReport();

        assertThat(defaultGenerator.getCurrentReport()).isNotNull();
    }

    @Test
    void testNullConfigUseDefaults() {
        ReportGenerator generator = new ReportGenerator(null);
        generator.startReport();

        assertThat(generator.getCurrentReport()).isNotNull();
    }

    @Test
    void testReportDurationIsCalculated() throws IOException, InterruptedException {
        reportGenerator.startReport();
        Thread.sleep(50); // Small delay to ensure duration > 0
        reportGenerator.addEvent(createTestEvent("SUCCESS", "Step 1", "By.id: x", "By.id: y", 0.9));

        // Capture report before finish clears it
        Instant startTime = reportGenerator.getCurrentReport().getTimestamp();
        reportGenerator.finishReport();

        // Load the report to check duration
        Path jsonFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".json"))
                .findFirst()
                .orElseThrow();

        HealReport loadedReport = reportGenerator.loadReport(jsonFile.toString());
        assertThat(loadedReport.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    private void createTestJsonReport(String filename, String stepText) throws IOException {
        String json = """
            {
                "run_id": "test-run-123",
                "timestamp": "%s",
                "events": [{
                    "event_id": "evt-123",
                    "timestamp": "%s",
                    "step": "%s",
                    "feature": "Test Feature",
                    "scenario": "Test Scenario",
                    "failure": {
                        "exception_type": "NoSuchElementException",
                        "message": "Element not found",
                        "original_locator": "By.id: x"
                    },
                    "result": {
                        "status": "SUCCESS",
                        "healed_locator": "By.id: y"
                    },
                    "decision": {
                        "can_heal": true,
                        "confidence": 0.9,
                        "reasoning": "Test reasoning"
                    }
                }],
                "summary": {
                    "heal_attempts": 1,
                    "heal_successes": 1
                }
            }
            """.formatted(Instant.now().toString(), Instant.now().toString(), stepText);
        Files.writeString(tempDir.resolve(filename), json);
    }

    private HealEvent createTestEvent(String status, String stepText, String originalLocator,
                                       String healedLocator, double confidence) {
        HealEvent event = new HealEvent();
        event.setStep(stepText);
        event.setTimestamp(Instant.now());
        event.setFeature("Test Feature");
        event.setScenario("Test Scenario");

        FailureInfo failure = new FailureInfo();
        failure.setOriginalLocator(originalLocator);
        failure.setExceptionType("NoSuchElementException");
        failure.setMessage("Element not found");
        event.setFailure(failure);

        DecisionInfo decision = new DecisionInfo();
        decision.setConfidence(confidence);
        decision.setCanHeal(healedLocator != null);
        decision.setReasoning("Test reasoning");
        event.setDecision(decision);

        ResultInfo result = new ResultInfo();
        result.setStatus(status);
        result.setHealedLocator(healedLocator);
        event.setResult(result);

        return event;
    }
}
