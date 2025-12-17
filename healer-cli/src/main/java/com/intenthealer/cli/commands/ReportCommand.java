package com.intenthealer.cli.commands;

import com.intenthealer.cli.util.CliOutput;
import com.intenthealer.report.ReportGenerator;
import com.intenthealer.report.model.HealEvent;
import com.intenthealer.report.model.HealReport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CLI command for generating and viewing heal reports.
 */
public class ReportCommand {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ReportGenerator reportGenerator;

    public ReportCommand(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    /**
     * Generate a report from JSON heal events.
     */
    public void generate(String inputDir, String outputPath, String format) throws IOException {
        CliOutput.println("Generating report...");
        CliOutput.println("  Input: " + inputDir);
        CliOutput.println("  Output: " + outputPath);
        CliOutput.println("  Format: " + format);

        // Find all JSON report files
        Path inputPath = Path.of(inputDir);
        if (!Files.exists(inputPath)) {
            CliOutput.error("Input directory does not exist: " + inputDir);
            return;
        }

        List<Path> reportFiles = Files.walk(inputPath)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> p.getFileName().toString().startsWith("heal-"))
                .toList();

        if (reportFiles.isEmpty()) {
            CliOutput.println("No heal report files found in " + inputDir);
            return;
        }

        CliOutput.println("Found " + reportFiles.size() + " report file(s)");

        // Generate combined report
        if (format.equalsIgnoreCase("html") || format.equalsIgnoreCase("both")) {
            String htmlPath = outputPath.endsWith(".html") ? outputPath : outputPath + ".html";
            reportGenerator.generateHtmlFromDirectory(inputDir, htmlPath);
            CliOutput.println("Generated HTML report: " + htmlPath);
        }

        CliOutput.println("Report generation complete.");
    }

    /**
     * Show summary of recent heal events.
     */
    public void summary(String reportDir) throws IOException {
        Path dirPath = Path.of(reportDir);
        if (!Files.exists(dirPath)) {
            CliOutput.error("Report directory not found: " + reportDir);
            return;
        }

        List<HealReport> reports = loadReports(dirPath);

        if (reports.isEmpty()) {
            CliOutput.println("No heal reports found in " + reportDir);
            return;
        }

        // Calculate statistics
        int totalAttempts = 0;
        int successCount = 0;
        int refusedCount = 0;
        int failedCount = 0;
        double totalCost = 0;

        for (HealReport report : reports) {
            for (HealEvent event : report.getEvents()) {
                totalAttempts++;
                switch (event.getOutcome()) {
                    case "SUCCESS" -> successCount++;
                    case "REFUSED" -> refusedCount++;
                    case "FAILED" -> failedCount++;
                }
                if (event.getLlmCostUsd() != null) {
                    totalCost += event.getLlmCostUsd();
                }
            }
        }

        // Print summary
        CliOutput.header("HEAL REPORT SUMMARY");
        CliOutput.printf("  Total Reports:     %d%n", reports.size());
        CliOutput.printf("  Total Heal Attempts: %d%n", totalAttempts);
        CliOutput.println();
        CliOutput.println("  Outcomes:");
        CliOutput.printf("    Success:      %d (%.1f%%)%n",
                successCount, totalAttempts > 0 ? 100.0 * successCount / totalAttempts : 0);
        CliOutput.printf("    Refused:      %d (%.1f%%)%n",
                refusedCount, totalAttempts > 0 ? 100.0 * refusedCount / totalAttempts : 0);
        CliOutput.printf("    Failed:       %d (%.1f%%)%n",
                failedCount, totalAttempts > 0 ? 100.0 * failedCount / totalAttempts : 0);
        CliOutput.println();
        CliOutput.printf("  Total LLM Cost:    $%.4f%n", totalCost);
        CliOutput.println();
        CliOutput.divider();
    }

    /**
     * List recent heal events.
     */
    public void list(String reportDir, int limit) throws IOException {
        Path dirPath = Path.of(reportDir);
        if (!Files.exists(dirPath)) {
            CliOutput.error("Report directory not found: " + reportDir);
            return;
        }

        List<HealReport> reports = loadReports(dirPath);

        if (reports.isEmpty()) {
            CliOutput.println("No heal reports found.");
            return;
        }

        CliOutput.println();
        CliOutput.println("Recent Heal Events:");
        CliOutput.println("-".repeat(80));

        int count = 0;
        for (HealReport report : reports) {
            for (HealEvent event : report.getEvents()) {
                if (count >= limit) break;

                String icon = switch (event.getOutcome()) {
                    case "SUCCESS" -> "[OK]";
                    case "REFUSED" -> "[--]";
                    default -> "[!!]";
                };

                CliOutput.printf("%s [%s] %s%n",
                        icon,
                        formatTime(event.getTimestamp()),
                        truncate(event.getStepText(), 50));
                CliOutput.printf("   %s -> %s%n",
                        event.getOriginalLocator(),
                        event.getHealedLocator() != null && !event.getHealedLocator().isEmpty()
                            ? event.getHealedLocator() : "N/A");
                CliOutput.println();

                count++;
            }
            if (count >= limit) break;
        }

        CliOutput.printf("Showing %d of %d total events%n", count, getTotalEvents(reports));
    }

    private List<HealReport> loadReports(Path dirPath) throws IOException {
        return Files.walk(dirPath)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> p.getFileName().toString().startsWith("heal"))
                .map(p -> {
                    try {
                        return reportGenerator.loadReport(p.toString());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(r -> r != null)
                .toList();
    }

    private int getTotalEvents(List<HealReport> reports) {
        return reports.stream()
                .mapToInt(r -> r.getEvents().size())
                .sum();
    }

    private String formatTime(Instant instant) {
        return instant != null ? DATE_FORMAT.format(instant) : "unknown";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }
}
