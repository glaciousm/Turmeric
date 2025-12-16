package com.intenthealer.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.intenthealer.core.config.ReportConfig;
import com.intenthealer.report.model.HealEvent;
import com.intenthealer.report.model.HealReport;
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

/**
 * Generates JSON and HTML reports from healing events.
 */
public class ReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final ReportConfig config;
    private final ObjectMapper objectMapper;
    private HealReport currentReport;

    public ReportGenerator(ReportConfig config) {
        this.config = config != null ? config : new ReportConfig();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
                    escapeHtml(event.getDecision() != null ? event.getDecision().getReasoning() : "N/A")
            ));
        }

        html.append("""
                </div>
            </body>
            </html>
            """);

        return html.toString();
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
