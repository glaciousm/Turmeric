package com.intenthealer.cli.commands;

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
        System.out.println("Generating report...");
        System.out.println("  Input: " + inputDir);
        System.out.println("  Output: " + outputPath);
        System.out.println("  Format: " + format);

        // Find all JSON report files
        Path inputPath = Path.of(inputDir);
        if (!Files.exists(inputPath)) {
            System.err.println("Error: Input directory does not exist: " + inputDir);
            return;
        }

        List<Path> reportFiles = Files.walk(inputPath)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> p.getFileName().toString().startsWith("heal-"))
                .toList();

        if (reportFiles.isEmpty()) {
            System.out.println("No heal report files found in " + inputDir);
            return;
        }

        System.out.println("Found " + reportFiles.size() + " report file(s)");

        // Generate combined report
        if (format.equalsIgnoreCase("html") || format.equalsIgnoreCase("both")) {
            String htmlPath = outputPath.endsWith(".html") ? outputPath : outputPath + ".html";
            reportGenerator.generateHtmlFromDirectory(inputDir, htmlPath);
            System.out.println("Generated HTML report: " + htmlPath);
        }

        System.out.println("Report generation complete.");
    }

    /**
     * Show summary of recent heal events.
     */
    public void summary(String reportDir) throws IOException {
        Path dirPath = Path.of(reportDir);
        if (!Files.exists(dirPath)) {
            System.err.println("Report directory not found: " + reportDir);
            return;
        }

        List<HealReport> reports = loadReports(dirPath);

        if (reports.isEmpty()) {
            System.out.println("No heal reports found in " + reportDir);
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
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("                     HEAL REPORT SUMMARY                        ");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("  Total Reports:     %d%n", reports.size());
        System.out.printf("  Total Heal Attempts: %d%n", totalAttempts);
        System.out.println();
        System.out.println("  Outcomes:");
        System.out.printf("    ✅ Success:      %d (%.1f%%)%n",
                successCount, totalAttempts > 0 ? 100.0 * successCount / totalAttempts : 0);
        System.out.printf("    🚫 Refused:      %d (%.1f%%)%n",
                refusedCount, totalAttempts > 0 ? 100.0 * refusedCount / totalAttempts : 0);
        System.out.printf("    ❌ Failed:       %d (%.1f%%)%n",
                failedCount, totalAttempts > 0 ? 100.0 * failedCount / totalAttempts : 0);
        System.out.println();
        System.out.printf("  Total LLM Cost:    $%.4f%n", totalCost);
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    /**
     * List recent heal events.
     */
    public void list(String reportDir, int limit) throws IOException {
        Path dirPath = Path.of(reportDir);
        if (!Files.exists(dirPath)) {
            System.err.println("Report directory not found: " + reportDir);
            return;
        }

        List<HealReport> reports = loadReports(dirPath);

        if (reports.isEmpty()) {
            System.out.println("No heal reports found.");
            return;
        }

        System.out.println();
        System.out.println("Recent Heal Events:");
        System.out.println("─".repeat(80));

        int count = 0;
        for (HealReport report : reports) {
            for (HealEvent event : report.getEvents()) {
                if (count >= limit) break;

                String icon = switch (event.getOutcome()) {
                    case "SUCCESS" -> "✅";
                    case "REFUSED" -> "🚫";
                    default -> "❌";
                };

                System.out.printf("%s [%s] %s%n",
                        icon,
                        formatTime(event.getTimestamp()),
                        truncate(event.getStepText(), 50));
                System.out.printf("   %s -> %s%n",
                        event.getOriginalLocator(),
                        event.getHealedLocator() != null ? event.getHealedLocator() : "N/A");
                System.out.println();

                count++;
            }
            if (count >= limit) break;
        }

        System.out.printf("Showing %d of %d total events%n", count, getTotalEvents(reports));
    }

    private List<HealReport> loadReports(Path dirPath) throws IOException {
        return Files.walk(dirPath)
                .filter(p -> p.toString().endsWith(".json"))
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
