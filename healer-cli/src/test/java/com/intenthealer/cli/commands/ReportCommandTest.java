package com.intenthealer.cli.commands;

import com.intenthealer.report.ReportGenerator;
import com.intenthealer.report.model.HealEvent;
import com.intenthealer.report.model.HealEvent.FailureInfo;
import com.intenthealer.report.model.HealEvent.ResultInfo;
import com.intenthealer.report.model.HealEvent.DecisionInfo;
import com.intenthealer.report.model.HealEvent.CostInfo;
import com.intenthealer.report.model.HealReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the ReportCommand class.
 */
@ExtendWith(MockitoExtension.class)
class ReportCommandTest {

    @Mock
    private ReportGenerator reportGenerator;

    private ReportCommand reportCommand;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reportCommand = new ReportCommand(reportGenerator);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testGenerateShowsProgressMessages() throws Exception {
        Path inputDir = tempDir.resolve("input");
        Path outputPath = tempDir.resolve("report.html");
        Files.createDirectories(inputDir);

        reportCommand.generate(inputDir.toString(), outputPath.toString(), "html");

        String output = outContent.toString();
        assertThat(output).contains("Generating report");
        assertThat(output).contains("Input:");
        assertThat(output).contains("Output:");
        assertThat(output).contains("Format:");
    }

    @Test
    void testGenerateWithNonexistentInputDirectory() throws Exception {
        reportCommand.generate("/nonexistent/dir", "output.html", "html");

        String errOutput = errContent.toString();
        assertThat(errOutput).contains("Input directory does not exist");
    }

    @Test
    void testGenerateWithNoReportFiles() throws Exception {
        Path inputDir = tempDir.resolve("empty-input");
        Files.createDirectories(inputDir);

        reportCommand.generate(inputDir.toString(), "output.html", "html");

        String output = outContent.toString();
        assertThat(output).contains("No heal report files found");
    }

    @Test
    void testGenerateWithJsonReportFiles() throws Exception {
        Path inputDir = tempDir.resolve("reports");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("heal-report-001.json"), "{}");

        reportCommand.generate(inputDir.toString(), "output.html", "html");

        String output = outContent.toString();
        assertThat(output).contains("Found 1 report file(s)");
    }

    @Test
    void testGenerateCallsReportGenerator() throws Exception {
        Path inputDir = tempDir.resolve("reports");
        Path outputPath = tempDir.resolve("output.html");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("heal-report-001.json"), "{}");

        reportCommand.generate(inputDir.toString(), outputPath.toString(), "html");

        verify(reportGenerator).generateHtmlFromDirectory(inputDir.toString(), outputPath.toString());
    }

    @Test
    void testGenerateShowsCompletionMessage() throws Exception {
        Path inputDir = tempDir.resolve("reports");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("heal-report-001.json"), "{}");

        reportCommand.generate(inputDir.toString(), "output.html", "html");

        String output = outContent.toString();
        assertThat(output).contains("Report generation complete");
    }

    @Test
    void testSummaryWithNonexistentDirectory() throws Exception {
        reportCommand.summary("/nonexistent/dir");

        String errOutput = errContent.toString();
        assertThat(errOutput).contains("Report directory not found");
    }

    @Test
    void testSummaryWithEmptyDirectory() throws Exception {
        reportCommand.summary(tempDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("No heal reports found");
    }

    @Test
    void testSummaryWithReports() throws Exception {
        // Create a report file and mock the report loading
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealReport mockReport = createMockReport(List.of(
                createHealEvent("SUCCESS", "Step 1", "By.id: old", "By.id: new", 0.95),
                createHealEvent("REFUSED", "Step 2", "By.css: .old", null, 0.0)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.summary(reportDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("HEAL REPORT SUMMARY");
        assertThat(output).contains("Total Reports:");
        assertThat(output).contains("Total Heal Attempts:");
    }

    @Test
    void testSummaryShowsOutcomes() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealReport mockReport = createMockReport(List.of(
                createHealEvent("SUCCESS", "Step 1", "By.id: old", "By.id: new", 0.95),
                createHealEvent("REFUSED", "Step 2", "By.css: .old", null, 0.0),
                createHealEvent("FAILED", "Step 3", "By.xpath: //div", null, 0.0)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.summary(reportDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("Outcomes:");
        assertThat(output).contains("Success:");
        assertThat(output).contains("Refused:");
        assertThat(output).contains("Failed:");
    }

    @Test
    void testSummaryShowsPercentages() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealReport mockReport = createMockReport(List.of(
                createHealEvent("SUCCESS", "Step 1", "By.id: old", "By.id: new", 0.95)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.summary(reportDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("%");
    }

    @Test
    void testSummaryShowsLlmCost() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealEvent event = createHealEvent("SUCCESS", "Step 1", "By.id: old", "By.id: new", 0.95, 0.0025);

        HealReport mockReport = createMockReport(List.of(event));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.summary(reportDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("Total LLM Cost:");
        assertThat(output).contains("$");
    }

    @Test
    void testListWithNonexistentDirectory() throws Exception {
        reportCommand.list("/nonexistent/dir", 10);

        String errOutput = errContent.toString();
        assertThat(errOutput).contains("Report directory not found");
    }

    @Test
    void testListWithEmptyDirectory() throws Exception {
        reportCommand.list(tempDir.toString(), 10);

        String output = outContent.toString();
        assertThat(output).contains("No heal reports found");
    }

    @Test
    void testListShowsRecentEvents() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealReport mockReport = createMockReport(List.of(
                createHealEvent("SUCCESS", "I click the login button", "By.id: login", "By.css: .login-btn", 0.92)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.list(reportDir.toString(), 20);

        String output = outContent.toString();
        assertThat(output).contains("Recent Heal Events:");
        assertThat(output).contains("login");
    }

    @Test
    void testListRespectsLimit() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealReport mockReport = createMockReport(List.of(
                createHealEvent("SUCCESS", "Step 1", "By.id: 1", "By.id: new1", 0.9),
                createHealEvent("SUCCESS", "Step 2", "By.id: 2", "By.id: new2", 0.9),
                createHealEvent("SUCCESS", "Step 3", "By.id: 3", "By.id: new3", 0.9)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.list(reportDir.toString(), 2);

        String output = outContent.toString();
        assertThat(output).contains("Showing 2 of 3 total events");
    }

    @Test
    void testListShowsOutcomeIcons() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealReport mockReport = createMockReport(List.of(
                createHealEvent("SUCCESS", "Step 1", "By.id: 1", "By.id: new1", 0.9),
                createHealEvent("REFUSED", "Step 2", "By.id: 2", null, 0.0)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.list(reportDir.toString(), 10);

        String output = outContent.toString();
        assertThat(output).contains("✅");
        assertThat(output).contains("🚫");
    }

    @Test
    void testListShowsLocatorTransformation() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealReport mockReport = createMockReport(List.of(
                createHealEvent("SUCCESS", "Click button", "By.id: old-btn", "By.css: .new-btn", 0.95)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.list(reportDir.toString(), 10);

        String output = outContent.toString();
        assertThat(output).contains("By.id: old-btn");
        assertThat(output).contains("By.css: .new-btn");
        assertThat(output).contains("->");
    }

    @Test
    void testListHandlesNullHealedLocator() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        HealReport mockReport = createMockReport(List.of(
                createHealEvent("REFUSED", "Click button", "By.id: old-btn", null, 0.0)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.list(reportDir.toString(), 10);

        String output = outContent.toString();
        assertThat(output).contains("N/A");
    }

    @Test
    void testListTruncatesLongStepText() throws Exception {
        Path reportDir = tempDir.resolve("reports");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("heal-report.json");
        Files.writeString(reportFile, "{}");

        String longStepText = "This is a very long step text that should be truncated when displayed in the list view because it exceeds the maximum length";
        HealReport mockReport = createMockReport(List.of(
                createHealEvent("SUCCESS", longStepText, "By.id: btn", "By.css: .btn", 0.9)
        ));

        when(reportGenerator.loadReport(reportFile.toString())).thenReturn(mockReport);

        reportCommand.list(reportDir.toString(), 10);

        String output = outContent.toString();
        assertThat(output).contains("...");
    }

    @Test
    void testGenerateWithBothFormat() throws Exception {
        Path inputDir = tempDir.resolve("reports");
        Path outputPath = tempDir.resolve("report.html");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("heal-report.json"), "{}");

        reportCommand.generate(inputDir.toString(), outputPath.toString(), "both");

        verify(reportGenerator).generateHtmlFromDirectory(inputDir.toString(), outputPath.toString());
    }

    private HealReport createMockReport(List<HealEvent> events) {
        HealReport report = new HealReport();
        report.setEvents(events);
        return report;
    }

    private HealEvent createHealEvent(String outcome, String stepText, String originalLocator,
                                       String healedLocator, double confidence) {
        return createHealEvent(outcome, stepText, originalLocator, healedLocator, confidence, 0.0);
    }

    private HealEvent createHealEvent(String outcome, String stepText, String originalLocator,
                                       String healedLocator, double confidence, double costUsd) {
        HealEvent event = new HealEvent();
        event.setStep(stepText);
        event.setTimestamp(Instant.now());

        // Set failure info
        FailureInfo failure = new FailureInfo();
        failure.setOriginalLocator(originalLocator);
        event.setFailure(failure);

        // Set decision info
        DecisionInfo decision = new DecisionInfo();
        decision.setConfidence(confidence);
        decision.setCanHeal(healedLocator != null);
        event.setDecision(decision);

        // Set result info
        ResultInfo result = new ResultInfo();
        result.setStatus(outcome);
        result.setHealedLocator(healedLocator);
        event.setResult(result);

        // Set cost info
        if (costUsd > 0) {
            CostInfo cost = new CostInfo();
            cost.setCostUsd(costUsd);
            event.setCost(cost);
        }

        return event;
    }
}
