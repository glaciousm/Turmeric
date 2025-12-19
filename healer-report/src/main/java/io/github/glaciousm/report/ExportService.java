package io.github.glaciousm.report;

import io.github.glaciousm.report.model.HealEvent;
import io.github.glaciousm.report.model.HealReport;
import com.lowagie.text.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting heal reports to various formats.
 * Supports CSV, JUnit XML, PDF, and other CI-friendly formats.
 */
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PdfReportGenerator pdfGenerator;

    public ExportService() {
        this.pdfGenerator = new PdfReportGenerator();
    }

    /**
     * Export a heal report to CSV format.
     *
     * @param report the heal report to export
     * @param outputPath the output file path
     */
    public void exportToCsv(HealReport report, String outputPath) throws IOException {
        Path path = Path.of(outputPath);
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));

        try (PrintWriter writer = new PrintWriter(new FileWriter(path.toFile()))) {
            // Write header
            writer.println("event_id,timestamp,feature,scenario,step,status,confidence,original_locator,healed_locator,reasoning,exception_type,exception_message,input_tokens,output_tokens,cost_usd");

            // Write each event
            for (HealEvent event : report.getEvents()) {
                writer.println(formatCsvLine(event));
            }
        }

        logger.info("Exported {} events to CSV: {}", report.getEvents().size(), outputPath);
    }

    /**
     * Export a heal report to JUnit XML format for CI integration.
     * Each heal is represented as a test case.
     *
     * @param report the heal report to export
     * @param outputPath the output file path
     */
    public void exportToJunitXml(HealReport report, String outputPath) throws IOException {
        Path path = Path.of(outputPath);
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));

        HealReport.ReportSummary summary = report.getSummary();
        int totalTests = summary.getHealAttempts();
        int failures = summary.getHealFailures();
        int skipped = summary.getHealRefusals();
        long durationSeconds = report.getDurationMs() / 1000;

        try (PrintWriter writer = new PrintWriter(new FileWriter(path.toFile()))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.printf("<testsuite name=\"Intent Healer\" tests=\"%d\" failures=\"%d\" skipped=\"%d\" time=\"%d\" timestamp=\"%s\">%n",
                    totalTests, failures, skipped, durationSeconds, formatTimestamp(report.getTimestamp()));

            // Properties
            writer.println("  <properties>");
            writer.printf("    <property name=\"healer.version\" value=\"1.0.0-SNAPSHOT\"/>%n");
            writer.printf("    <property name=\"healer.successRate\" value=\"%.1f%%\"/>%n",
                    totalTests > 0 ? (summary.getHealSuccesses() * 100.0) / totalTests : 0);
            writer.printf("    <property name=\"healer.totalCost\" value=\"$%.4f\"/>%n", summary.getTotalLlmCostUsd());
            writer.println("  </properties>");

            // Test cases
            for (HealEvent event : report.getEvents()) {
                writeTestCase(writer, event);
            }

            writer.println("</testsuite>");
        }

        logger.info("Exported {} events to JUnit XML: {}", report.getEvents().size(), outputPath);
    }

    /**
     * Export heal events to a summary text file suitable for CI logs.
     */
    public void exportToSummaryText(HealReport report, String outputPath) throws IOException {
        Path path = Path.of(outputPath);
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));

        HealReport.ReportSummary summary = report.getSummary();

        try (PrintWriter writer = new PrintWriter(new FileWriter(path.toFile()))) {
            writer.println("╔════════════════════════════════════════════════════════════════════════════╗");
            writer.println("║                    INTENT HEALER - TEST RUN SUMMARY                       ║");
            writer.println("╠════════════════════════════════════════════════════════════════════════════╣");
            writer.printf("║  Heal Attempts:    %-57d ║%n", summary.getHealAttempts());
            writer.printf("║  Successes:        %-57d ║%n", summary.getHealSuccesses());
            writer.printf("║  Refused:          %-57d ║%n", summary.getHealRefusals());
            writer.printf("║  Failed:           %-57d ║%n", summary.getHealFailures());
            writer.printf("║  Success Rate:     %-57.1f%% ║%n",
                    summary.getHealAttempts() > 0
                        ? (summary.getHealSuccesses() * 100.0) / summary.getHealAttempts()
                        : 0);
            writer.printf("║  LLM Cost:         $%-56.4f ║%n", summary.getTotalLlmCostUsd());
            writer.println("╚════════════════════════════════════════════════════════════════════════════╝");
            writer.println();

            if (!report.getEvents().isEmpty()) {
                writer.println("Heal Events:");
                writer.println("─────────────────────────────────────────────────────────────────────────────");

                int index = 1;
                for (HealEvent event : report.getEvents()) {
                    String status = event.getResult() != null ? event.getResult().getStatus() : "UNKNOWN";
                    String statusIcon = switch (status) {
                        case "SUCCESS" -> "[OK]";
                        case "REFUSED" -> "[--]";
                        default -> "[!!]";
                    };

                    writer.printf("%3d. %s %s%n", index++, statusIcon, truncate(event.getStep(), 65));
                    writer.printf("     Feature:  %s%n", truncate(event.getFeature(), 60));
                    writer.printf("     Scenario: %s%n", truncate(event.getScenario(), 60));

                    if (event.getFailure() != null && event.getFailure().getOriginalLocator() != null) {
                        writer.printf("     Original: %s%n", truncate(event.getFailure().getOriginalLocator(), 60));
                    }
                    if (event.getResult() != null && event.getResult().getHealedLocator() != null) {
                        writer.printf("     Healed:   %s%n", truncate(event.getResult().getHealedLocator(), 60));
                    }

                    double confidence = event.getDecision() != null ? event.getDecision().getConfidence() * 100 : 0;
                    writer.printf("     Confidence: %.0f%%%n", confidence);
                    writer.println();
                }
            }
        }

        logger.info("Exported summary to: {}", outputPath);
    }

    /**
     * Export a heal report to PDF format.
     * Produces a professional report suitable for sharing and archiving.
     *
     * @param report the heal report to export
     * @param outputPath the output file path
     */
    public void exportToPdf(HealReport report, String outputPath) throws IOException {
        try {
            pdfGenerator.generateReport(report, outputPath);
        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF report", e);
        }
    }

    /**
     * Export multiple reports to a combined PDF for comprehensive analysis.
     *
     * @param reports the list of reports to combine
     * @param outputPath the output file path
     */
    public void exportToCombinedPdf(List<HealReport> reports, String outputPath) throws IOException {
        if (reports == null || reports.isEmpty()) {
            throw new IllegalArgumentException("No reports to export");
        }

        // Combine all events into a single report
        HealReport combinedReport = new HealReport();
        combinedReport.setTimestamp(reports.get(0).getTimestamp());

        long totalDuration = 0;
        for (HealReport report : reports) {
            if (report.getEvents() != null) {
                for (HealEvent event : report.getEvents()) {
                    combinedReport.addEvent(event);
                }
            }
            totalDuration += report.getDurationMs();
        }
        combinedReport.setDurationMs(totalDuration);

        exportToPdf(combinedReport, outputPath);
        logger.info("Exported {} reports to combined PDF: {}", reports.size(), outputPath);
    }

    /**
     * Export multiple reports to a combined CSV for trend analysis.
     */
    public void exportTrendsCsv(List<HealReport> reports, String outputPath) throws IOException {
        Path path = Path.of(outputPath);
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));

        try (PrintWriter writer = new PrintWriter(new FileWriter(path.toFile()))) {
            // Write header
            writer.println("report_date,total_heals,successes,failures,refused,success_rate,avg_confidence,total_cost_usd");

            // Write each report summary
            for (HealReport report : reports) {
                HealReport.ReportSummary summary = report.getSummary();
                String date = formatDate(report.getTimestamp());
                int total = summary.getHealAttempts();
                double successRate = total > 0 ? (summary.getHealSuccesses() * 100.0) / total : 0;
                double avgConfidence = calculateAvgConfidence(report);

                writer.printf("%s,%d,%d,%d,%d,%.2f,%.2f,%.6f%n",
                        date,
                        total,
                        summary.getHealSuccesses(),
                        summary.getHealFailures(),
                        summary.getHealRefusals(),
                        successRate,
                        avgConfidence,
                        summary.getTotalLlmCostUsd()
                );
            }
        }

        logger.info("Exported trend data for {} reports to: {}", reports.size(), outputPath);
    }

    private String formatCsvLine(HealEvent event) {
        StringBuilder sb = new StringBuilder();

        // event_id
        sb.append(escapeCsv(event.getEventId())).append(",");

        // timestamp
        sb.append(formatTimestamp(event.getTimestamp())).append(",");

        // feature
        sb.append(escapeCsv(event.getFeature())).append(",");

        // scenario
        sb.append(escapeCsv(event.getScenario())).append(",");

        // step
        sb.append(escapeCsv(event.getStep())).append(",");

        // status
        String status = event.getResult() != null ? event.getResult().getStatus() : "";
        sb.append(escapeCsv(status)).append(",");

        // confidence
        double confidence = event.getDecision() != null ? event.getDecision().getConfidence() : 0;
        sb.append(String.format("%.4f", confidence)).append(",");

        // original_locator
        String origLocator = event.getFailure() != null ? event.getFailure().getOriginalLocator() : "";
        sb.append(escapeCsv(origLocator)).append(",");

        // healed_locator
        String healedLocator = event.getResult() != null ? event.getResult().getHealedLocator() : "";
        sb.append(escapeCsv(healedLocator)).append(",");

        // reasoning
        String reasoning = event.getDecision() != null ? event.getDecision().getReasoning() : "";
        sb.append(escapeCsv(reasoning)).append(",");

        // exception_type
        String excType = event.getFailure() != null ? event.getFailure().getExceptionType() : "";
        sb.append(escapeCsv(excType)).append(",");

        // exception_message
        String excMsg = event.getFailure() != null ? event.getFailure().getMessage() : "";
        sb.append(escapeCsv(excMsg)).append(",");

        // input_tokens
        int inputTokens = event.getCost() != null ? event.getCost().getInputTokens() : 0;
        sb.append(inputTokens).append(",");

        // output_tokens
        int outputTokens = event.getCost() != null ? event.getCost().getOutputTokens() : 0;
        sb.append(outputTokens).append(",");

        // cost_usd
        double cost = event.getCost() != null ? event.getCost().getCostUsd() : 0;
        sb.append(String.format("%.6f", cost));

        return sb.toString();
    }

    private void writeTestCase(PrintWriter writer, HealEvent event) {
        String testName = String.format("%s - %s",
                event.getScenario() != null ? event.getScenario() : "Unknown",
                event.getStep() != null ? event.getStep() : "Unknown");
        String className = event.getFeature() != null ? event.getFeature() : "Unknown";
        String status = event.getResult() != null ? event.getResult().getStatus() : "UNKNOWN";

        writer.printf("  <testcase name=\"%s\" classname=\"%s\" time=\"0\">%n",
                escapeXml(testName), escapeXml(className));

        switch (status) {
            case "SUCCESS" -> {
                // Successful test - add properties about the heal
                writer.println("    <system-out><![CDATA[");
                writer.printf("Heal applied successfully.%n");
                writer.printf("Original: %s%n", event.getFailure() != null ? event.getFailure().getOriginalLocator() : "N/A");
                writer.printf("Healed:   %s%n", event.getResult() != null ? event.getResult().getHealedLocator() : "N/A");
                writer.printf("Confidence: %.0f%%%n", event.getDecision() != null ? event.getDecision().getConfidence() * 100 : 0);
                if (event.getDecision() != null && event.getDecision().getReasoning() != null) {
                    writer.printf("Reasoning: %s%n", event.getDecision().getReasoning());
                }
                writer.println("]]></system-out>");
            }
            case "REFUSED" -> {
                writer.printf("    <skipped message=\"%s\"/>%n",
                        escapeXml(event.getDecision() != null && event.getDecision().getRefusalReason() != null
                                ? event.getDecision().getRefusalReason()
                                : "Heal refused"));
            }
            case "FAILED" -> {
                String message = event.getFailure() != null ? event.getFailure().getMessage() : "Heal failed";
                String type = event.getFailure() != null ? event.getFailure().getExceptionType() : "HealingException";
                writer.printf("    <failure message=\"%s\" type=\"%s\"><![CDATA[%n",
                        escapeXml(message), escapeXml(type));
                writer.printf("Original locator: %s%n",
                        event.getFailure() != null ? event.getFailure().getOriginalLocator() : "N/A");
                if (event.getDecision() != null && event.getDecision().getReasoning() != null) {
                    writer.printf("Reasoning: %s%n", event.getDecision().getReasoning());
                }
                writer.println("]]></failure>");
            }
            default -> {
                writer.printf("    <error message=\"Unknown heal status: %s\"/>%n", status);
            }
        }

        writer.println("  </testcase>");
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        // Escape quotes and wrap in quotes if contains comma, newline, or quote
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String formatTimestamp(Instant timestamp) {
        if (timestamp == null) return "";
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(TIMESTAMP_FORMAT);
    }

    private String formatDate(Instant timestamp) {
        if (timestamp == null) return "";
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private double calculateAvgConfidence(HealReport report) {
        if (report.getEvents().isEmpty()) return 0;

        double sum = 0;
        int count = 0;
        for (HealEvent event : report.getEvents()) {
            if (event.getDecision() != null) {
                sum += event.getDecision().getConfidence();
                count++;
            }
        }
        return count > 0 ? (sum / count) * 100 : 0;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
