package io.github.glaciousm.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.glaciousm.core.config.ReportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates HTML and JSON reports from healing summary data.
 */
public class HealingReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(HealingReportGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReportConfig config;
    private final ObjectMapper objectMapper;

    public HealingReportGenerator(ReportConfig config) {
        this.config = config != null ? config : new ReportConfig();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Generate reports from the current HealingSummary.
     *
     * @return Path to the generated HTML report, or null if no heals occurred
     */
    public Path generateReports() {
        HealingSummary summary = HealingSummary.getInstance();
        if (!summary.hasHeals()) {
            logger.debug("No heals to report");
            return null;
        }

        List<HealingSummary.HealedLocator> heals = summary.getHealedLocators();
        LocalDateTime timestamp = LocalDateTime.now();
        String timestampStr = timestamp.format(TIMESTAMP_FORMAT);

        Path outputDir = Paths.get(config.getOutputDir());
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            logger.error("Failed to create report directory: {}", outputDir, e);
            return null;
        }

        Path htmlPath = null;

        // Generate JSON report
        if (config.isJsonEnabled()) {
            Path jsonPath = outputDir.resolve("healer-report-" + timestampStr + ".json");
            try {
                generateJsonReport(heals, timestamp, jsonPath);
                logger.info("JSON report generated: {}", jsonPath.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to generate JSON report", e);
            }
        }

        // Generate HTML report
        if (config.isHtmlEnabled()) {
            htmlPath = outputDir.resolve("healer-report-" + timestampStr + ".html");
            try {
                generateHtmlReport(heals, timestamp, htmlPath);
                logger.info("HTML report generated: {}", htmlPath.toAbsolutePath());
                System.out.println("\n  Report generated: " + htmlPath.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to generate HTML report", e);
            }
        }

        return htmlPath;
    }

    private void generateJsonReport(List<HealingSummary.HealedLocator> heals, LocalDateTime timestamp, Path path) throws IOException {
        Map<String, Object> report = new HashMap<>();
        report.put("generated", timestamp.format(DISPLAY_FORMAT));
        report.put("totalHeals", heals.size());
        report.put("heals", heals.stream().map(h -> {
            Map<String, Object> heal = new HashMap<>();
            heal.put("stepText", h.stepText());
            heal.put("originalLocator", h.originalLocator());
            heal.put("healedLocator", h.healedLocator());
            heal.put("confidence", h.confidence());
            if (h.sourceFile() != null) {
                heal.put("sourceFile", h.sourceFile());
                heal.put("lineNumber", h.lineNumber());
            }
            return heal;
        }).toList());

        objectMapper.writeValue(path.toFile(), report);
    }

    private void generateHtmlReport(List<HealingSummary.HealedLocator> heals, LocalDateTime timestamp, Path path) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Intent Healer Report</title>
                    <style>
                        :root {
                            --primary: #4f46e5;
                            --success: #10b981;
                            --warning: #f59e0b;
                            --bg: #f8fafc;
                            --card: #ffffff;
                            --text: #1e293b;
                            --muted: #64748b;
                            --border: #e2e8f0;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            background: var(--bg);
                            color: var(--text);
                            line-height: 1.6;
                            padding: 2rem;
                        }
                        .container { max-width: 1200px; margin: 0 auto; }
                        header {
                            background: linear-gradient(135deg, var(--primary), #7c3aed);
                            color: white;
                            padding: 2rem;
                            border-radius: 12px;
                            margin-bottom: 2rem;
                            box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);
                        }
                        header h1 { font-size: 1.75rem; margin-bottom: 0.5rem; }
                        header p { opacity: 0.9; }
                        .stats {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                            gap: 1rem;
                            margin-bottom: 2rem;
                        }
                        .stat-card {
                            background: var(--card);
                            padding: 1.5rem;
                            border-radius: 8px;
                            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                            border-left: 4px solid var(--primary);
                        }
                        .stat-card h3 { color: var(--muted); font-size: 0.875rem; text-transform: uppercase; }
                        .stat-card .value { font-size: 2rem; font-weight: bold; color: var(--primary); }
                        .heal-card {
                            background: var(--card);
                            border-radius: 8px;
                            padding: 1.5rem;
                            margin-bottom: 1rem;
                            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                            border-left: 4px solid var(--success);
                        }
                        .heal-card h3 {
                            font-size: 1rem;
                            margin-bottom: 1rem;
                            color: var(--text);
                        }
                        .heal-card .number {
                            display: inline-block;
                            background: var(--primary);
                            color: white;
                            width: 24px;
                            height: 24px;
                            border-radius: 50%%;
                            text-align: center;
                            line-height: 24px;
                            font-size: 0.75rem;
                            margin-right: 0.5rem;
                        }
                        .locator-box {
                            background: #f1f5f9;
                            padding: 1rem;
                            border-radius: 6px;
                            margin: 0.5rem 0;
                            font-family: 'Monaco', 'Menlo', monospace;
                            font-size: 0.875rem;
                            overflow-x: auto;
                        }
                        .locator-box.original { border-left: 3px solid #ef4444; }
                        .locator-box.healed { border-left: 3px solid var(--success); }
                        .label {
                            font-size: 0.75rem;
                            text-transform: uppercase;
                            color: var(--muted);
                            margin-bottom: 0.25rem;
                        }
                        .confidence {
                            display: inline-block;
                            padding: 0.25rem 0.75rem;
                            border-radius: 9999px;
                            font-size: 0.75rem;
                            font-weight: 600;
                            margin-top: 0.5rem;
                        }
                        .confidence.high { background: #dcfce7; color: #166534; }
                        .confidence.medium { background: #fef3c7; color: #92400e; }
                        .confidence.low { background: #fee2e2; color: #991b1b; }
                        .copy-btn {
                            background: var(--primary);
                            color: white;
                            border: none;
                            padding: 0.5rem 1rem;
                            border-radius: 6px;
                            cursor: pointer;
                            font-size: 0.75rem;
                            margin-top: 0.5rem;
                        }
                        .copy-btn:hover { opacity: 0.9; }
                        .source-location {
                            font-size: 0.75rem;
                            color: var(--muted);
                            margin-top: 0.5rem;
                        }
                        .screenshots {
                            margin-top: 1rem;
                            border-top: 1px solid var(--border);
                            padding-top: 1rem;
                        }
                        .screenshots-toggle {
                            background: #e2e8f0;
                            color: var(--text);
                            border: none;
                            padding: 0.5rem 1rem;
                            border-radius: 6px;
                            cursor: pointer;
                            font-size: 0.75rem;
                            margin-bottom: 0.5rem;
                        }
                        .screenshots-toggle:hover { background: #cbd5e1; }
                        .screenshots-content {
                            display: none;
                        }
                        .screenshots-content.visible {
                            display: block;
                        }
                        .screenshot-comparison {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 1rem;
                            margin-top: 0.5rem;
                        }
                        .screenshot-box {
                            text-align: center;
                        }
                        .screenshot-box .label {
                            margin-bottom: 0.5rem;
                        }
                        .screenshot-box img {
                            max-width: 100%%;
                            border: 1px solid var(--border);
                            border-radius: 6px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        }
                        .screenshot-box.before img {
                            border-color: #ef4444;
                        }
                        .screenshot-box.after img {
                            border-color: var(--success);
                        }
                        footer {
                            text-align: center;
                            padding: 2rem;
                            color: var(--muted);
                            font-size: 0.875rem;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <header>
                            <h1>Intent Healer Report</h1>
                            <p>Generated: %s</p>
                        </header>

                        <div class="stats">
                            <div class="stat-card">
                                <h3>Total Heals</h3>
                                <div class="value">%d</div>
                            </div>
                            <div class="stat-card">
                                <h3>Average Confidence</h3>
                                <div class="value">%.0f%%</div>
                            </div>
                        </div>

                        <h2 style="margin-bottom: 1rem;">Healed Locators</h2>
                """.formatted(
                timestamp.format(DISPLAY_FORMAT),
                heals.size(),
                heals.stream().mapToDouble(HealingSummary.HealedLocator::confidence).average().orElse(0) * 100
        ));

        int index = 1;
        for (HealingSummary.HealedLocator heal : heals) {
            String confidenceClass = heal.confidence() >= 0.9 ? "high" : heal.confidence() >= 0.75 ? "medium" : "low";
            // Escape for HTML first, then escape % for String.format
            String stepText = escapeForFormat(escapeHtml(heal.stepText() != null ? heal.stepText() : "Unknown step"));
            String originalLocator = escapeForFormat(escapeHtml(heal.originalLocator()));
            String healedLocator = escapeForFormat(escapeHtml(heal.healedLocator()));

            html.append("""
                    <div class="heal-card">
                        <h3><span class="number">%d</span>%s</h3>
                        <div class="label">Original Locator</div>
                        <div class="locator-box original">%s</div>
                        <div class="label">Healed To</div>
                        <div class="locator-box healed" id="heal-%d">%s</div>
                        <button class="copy-btn" onclick="copyToClipboard('heal-%d')">Copy Healed Locator</button>
                        <span class="confidence %s">%.0f%% confidence</span>
                    """.formatted(
                    index, stepText,
                    originalLocator,
                    index, healedLocator,
                    index,
                    confidenceClass, heal.confidence() * 100
            ));

            if (heal.sourceFile() != null && !heal.sourceFile().isEmpty()) {
                html.append("""
                        <div class="source-location">Location: %s:%d</div>
                        """.formatted(escapeForFormat(escapeHtml(heal.sourceFile())), heal.lineNumber()));
            }

            // Add screenshots if available
            if (heal.hasVisualEvidence()) {
                html.append("""
                        <div class="screenshots">
                            <button class="screenshots-toggle" onclick="toggleScreenshots('screenshots-%d')">Show/Hide Screenshots</button>
                            <div class="screenshots-content" id="screenshots-%d">
                                <div class="screenshot-comparison">
                                    <div class="screenshot-box before">
                                        <div class="label">Before (Failed)</div>
                                        <img src="data:image/png;base64,%s" alt="Before healing" />
                                    </div>
                                    <div class="screenshot-box after">
                                        <div class="label">After (Healed)</div>
                                        <img src="data:image/png;base64,%s" alt="After healing" />
                                    </div>
                                </div>
                            </div>
                        </div>
                        """.formatted(index, index, heal.beforeScreenshotBase64(), heal.afterScreenshotBase64()));
            }

            html.append("</div>\n");
            index++;
        }

        html.append("""
                        <footer>
                            <p>Generated by Intent Healer - Self-Healing Selenium Tests</p>
                            <p>Update your Page Objects with the healed locators above to prevent repeated healing.</p>
                        </footer>
                    </div>
                    <script>
                        function copyToClipboard(id) {
                            const text = document.getElementById(id).textContent;
                            navigator.clipboard.writeText(text).then(() => {
                                alert('Copied to clipboard!');
                            });
                        }
                        function toggleScreenshots(id) {
                            const el = document.getElementById(id);
                            if (el) {
                                el.classList.toggle('visible');
                            }
                        }
                    </script>
                </body>
                </html>
                """);

        Files.writeString(path, html.toString());
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
     * Escape % characters for use with String.format()
     */
    private String escapeForFormat(String text) {
        if (text == null) return "";
        return text.replace("%", "%%");
    }
}
