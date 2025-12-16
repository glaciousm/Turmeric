package com.intenthealer.report;

import com.intenthealer.core.engine.patch.SourceCodeUpdater;
import com.intenthealer.core.model.ValidatedHeal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates reports for auto-update operations.
 * Shows what files were updated, before/after locators, and rollback instructions.
 */
public class AutoUpdateReport {

    private static final Logger logger = LoggerFactory.getLogger(AutoUpdateReport.class);

    private final String outputDir;
    private final DateTimeFormatter timestampFormatter;

    public AutoUpdateReport(String outputDir) {
        this.outputDir = outputDir != null ? outputDir : "target/healer-reports";
        this.timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    public AutoUpdateReport() {
        this("target/healer-reports");
    }

    /**
     * Generates an HTML report for auto-update results.
     *
     * @param results list of update results
     * @return path to the generated report
     */
    public String generateReport(List<SourceCodeUpdater.UpdateResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Auto-Update Report - Intent Healer</title>\n");
        html.append("  <style>\n");
        html.append(getStyles());
        html.append("  </style>\n");
        html.append("</head>\n<body>\n");

        // Header
        html.append("  <div class=\"header\">\n");
        html.append("    <h1>Auto-Update Report</h1>\n");
        html.append("    <p>Generated: ").append(timestampFormatter.format(Instant.now().atZone(java.time.ZoneId.systemDefault()))).append("</p>\n");
        html.append("  </div>\n");

        // Summary
        long successCount = results.stream().filter(SourceCodeUpdater.UpdateResult::isSuccess).count();
        long failedCount = results.size() - successCount;

        html.append("  <div class=\"summary\">\n");
        html.append("    <h2>Summary</h2>\n");
        html.append("    <div class=\"stats\">\n");
        html.append("      <div class=\"stat success\"><span class=\"value\">").append(successCount).append("</span><span class=\"label\">Successful</span></div>\n");
        html.append("      <div class=\"stat failed\"><span class=\"value\">").append(failedCount).append("</span><span class=\"label\">Failed</span></div>\n");
        html.append("      <div class=\"stat total\"><span class=\"value\">").append(results.size()).append("</span><span class=\"label\">Total</span></div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");

        // Updates table
        html.append("  <div class=\"updates\">\n");
        html.append("    <h2>Update Details</h2>\n");
        html.append("    <table>\n");
        html.append("      <thead>\n");
        html.append("        <tr><th>Status</th><th>File</th><th>Line</th><th>Old Locator</th><th>New Locator</th><th>Backup</th></tr>\n");
        html.append("      </thead>\n");
        html.append("      <tbody>\n");

        for (SourceCodeUpdater.UpdateResult result : results) {
            String statusClass = result.isSuccess() ? "success" : "failed";
            String statusText = result.isSuccess() ? "SUCCESS" : "FAILED";
            String filePath = result.getFilePath() != null ? getFileName(result.getFilePath()) : "N/A";
            int lineNumber = result.getLineNumber();
            String oldValue = result.getOldValue() != null ? escapeHtml(result.getOldValue()) : "N/A";
            String newValue = result.getNewValue() != null ? escapeHtml(result.getNewValue()) : "N/A";
            String backup = result.getBackupPath() != null ? getFileName(result.getBackupPath()) : "-";

            html.append("        <tr class=\"").append(statusClass).append("\">\n");
            html.append("          <td><span class=\"status ").append(statusClass).append("\">").append(statusText).append("</span></td>\n");
            html.append("          <td title=\"").append(escapeHtml(result.getFilePath())).append("\">").append(filePath).append("</td>\n");
            html.append("          <td>").append(lineNumber).append("</td>\n");
            html.append("          <td class=\"locator\"><code>").append(oldValue).append("</code></td>\n");
            html.append("          <td class=\"locator\"><code>").append(newValue).append("</code></td>\n");
            html.append("          <td title=\"").append(escapeHtml(result.getBackupPath() != null ? result.getBackupPath() : "")).append("\">").append(backup).append("</td>\n");
            html.append("        </tr>\n");

            if (!result.isSuccess() && result.getErrorMessage() != null) {
                html.append("        <tr class=\"error-detail\">\n");
                html.append("          <td colspan=\"6\"><span class=\"error-msg\">Error: ").append(escapeHtml(result.getErrorMessage())).append("</span></td>\n");
                html.append("        </tr>\n");
            }
        }

        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </div>\n");

        // Rollback instructions
        boolean hasBackups = results.stream()
                .filter(SourceCodeUpdater.UpdateResult::isSuccess)
                .anyMatch(r -> r.getBackupPath() != null);

        if (hasBackups) {
            html.append("  <div class=\"rollback\">\n");
            html.append("    <h2>Rollback Instructions</h2>\n");
            html.append("    <p>To rollback changes, you can:</p>\n");
            html.append("    <ol>\n");
            html.append("      <li>Use the CLI command: <code>healer patch rollback</code></li>\n");
            html.append("      <li>Manually restore from backup files listed above</li>\n");
            html.append("      <li>Use git to discard changes: <code>git checkout -- &lt;file&gt;</code></li>\n");
            html.append("    </ol>\n");
            html.append("  </div>\n");
        }

        html.append("</body>\n</html>");

        // Write to file
        String filename = "auto-update-" + System.currentTimeMillis() + ".html";
        Path reportPath = Paths.get(outputDir, filename);

        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, html.toString());
            logger.info("Auto-update report generated: {}", reportPath);
            return reportPath.toString();
        } catch (IOException e) {
            logger.error("Failed to write auto-update report: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generates a summary report for validated heals.
     *
     * @param heals list of validated heals
     * @return path to the generated report
     */
    public String generateValidatedHealsReport(List<ValidatedHeal> heals) {
        if (heals == null || heals.isEmpty()) {
            return null;
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <title>Validated Heals Report - Intent Healer</title>\n");
        html.append("  <style>\n").append(getStyles()).append("  </style>\n");
        html.append("</head>\n<body>\n");

        html.append("  <div class=\"header\">\n");
        html.append("    <h1>Validated Heals Report</h1>\n");
        html.append("    <p>Generated: ").append(timestampFormatter.format(Instant.now().atZone(java.time.ZoneId.systemDefault()))).append("</p>\n");
        html.append("  </div>\n");

        html.append("  <div class=\"updates\">\n");
        html.append("    <h2>Heals Ready for Auto-Update</h2>\n");
        html.append("    <table>\n");
        html.append("      <thead>\n");
        html.append("        <tr><th>Test</th><th>File:Line</th><th>Original</th><th>Healed</th><th>Confidence</th><th>Can Update</th></tr>\n");
        html.append("      </thead>\n");
        html.append("      <tbody>\n");

        for (ValidatedHeal heal : heals) {
            String location = heal.getSourceLocation() != null
                    ? heal.getSourceLocation().toShortString()
                    : "N/A";
            String canUpdate = heal.canAutoUpdate() ? "Yes" : "No";
            String rowClass = heal.canAutoUpdate() ? "success" : "pending";

            html.append("        <tr class=\"").append(rowClass).append("\">\n");
            html.append("          <td>").append(escapeHtml(heal.getTestName())).append("</td>\n");
            html.append("          <td>").append(location).append("</td>\n");
            html.append("          <td class=\"locator\"><code>").append(escapeHtml(heal.getOriginalLocator())).append("</code></td>\n");
            html.append("          <td class=\"locator\"><code>").append(escapeHtml(heal.getHealedLocator())).append("</code></td>\n");
            html.append("          <td>").append(String.format("%.0f%%", heal.getConfidence() * 100)).append("</td>\n");
            html.append("          <td>").append(canUpdate).append("</td>\n");
            html.append("        </tr>\n");
        }

        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </div>\n");
        html.append("</body>\n</html>");

        String filename = "validated-heals-" + System.currentTimeMillis() + ".html";
        Path reportPath = Paths.get(outputDir, filename);

        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, html.toString());
            logger.info("Validated heals report generated: {}", reportPath);
            return reportPath.toString();
        } catch (IOException e) {
            logger.error("Failed to write validated heals report: {}", e.getMessage());
            return null;
        }
    }

    private String getStyles() {
        return """
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
            .header { background: #2563eb; color: white; padding: 20px; margin: -20px -20px 20px -20px; }
            .header h1 { margin: 0; }
            .header p { margin: 10px 0 0 0; opacity: 0.9; }
            .summary { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
            .summary h2 { margin-top: 0; }
            .stats { display: flex; gap: 20px; }
            .stat { text-align: center; padding: 15px 25px; border-radius: 8px; }
            .stat.success { background: #dcfce7; }
            .stat.failed { background: #fee2e2; }
            .stat.total { background: #e0e7ff; }
            .stat .value { display: block; font-size: 2em; font-weight: bold; }
            .stat .label { font-size: 0.9em; color: #666; }
            .updates { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
            .updates h2 { margin-top: 0; }
            table { width: 100%; border-collapse: collapse; }
            th, td { padding: 12px; text-align: left; border-bottom: 1px solid #e5e7eb; }
            th { background: #f9fafb; font-weight: 600; }
            tr.success { background: #f0fdf4; }
            tr.failed { background: #fef2f2; }
            tr.pending { background: #fffbeb; }
            tr.error-detail td { background: #fef2f2; color: #991b1b; font-size: 0.9em; }
            .status { padding: 4px 8px; border-radius: 4px; font-size: 0.8em; font-weight: 600; }
            .status.success { background: #22c55e; color: white; }
            .status.failed { background: #ef4444; color: white; }
            .locator code { background: #f3f4f6; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; word-break: break-all; }
            .rollback { background: #fffbeb; padding: 20px; border-radius: 8px; border: 1px solid #fbbf24; }
            .rollback h2 { margin-top: 0; color: #92400e; }
            .rollback code { background: #fef3c7; padding: 2px 6px; border-radius: 4px; }
            """;
    }

    private String getFileName(String path) {
        if (path == null) return "N/A";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
