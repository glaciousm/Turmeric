package io.github.glaciousm.cucumber.report;

import io.github.glaciousm.core.engine.HealingSummary;
import io.github.glaciousm.core.engine.HealingSummary.HealedLocator;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cucumber plugin that generates an HTML report showing all healed locators.
 *
 * Usage in @CucumberOptions:
 *   plugin = {"io.github.glaciousm.cucumber.report.HealerCucumberReportPlugin:target/healer-report.html"}
 *
 * Or with default output:
 *   plugin = {"io.github.glaciousm.cucumber.report.HealerCucumberReportPlugin"}
 */
public class HealerCucumberReportPlugin implements ConcurrentEventListener {

    private static final Logger logger = LoggerFactory.getLogger(HealerCucumberReportPlugin.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String outputPath;
    private final Map<String, ScenarioHealInfo> scenarioHeals = new ConcurrentHashMap<>();
    private final List<ScenarioResult> scenarioResults = Collections.synchronizedList(new ArrayList<>());
    private Instant startTime;

    public HealerCucumberReportPlugin() {
        this("target/healer-cucumber-report.html");
    }

    public HealerCucumberReportPlugin(String outputPath) {
        this.outputPath = outputPath;
        logger.info("Healer Cucumber Report will be written to: {}", outputPath);
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, this::onTestRunStarted);
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        publisher.registerHandlerFor(TestRunFinished.class, this::onTestRunFinished);
    }

    private void onTestRunStarted(TestRunStarted event) {
        startTime = event.getInstant();
        HealingSummary.getInstance().clear();
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        String scenarioId = event.getTestCase().getId().toString();
        ScenarioHealInfo info = new ScenarioHealInfo();
        info.scenarioName = event.getTestCase().getName();
        info.featureName = extractFeatureName(event.getTestCase().getUri().toString());
        info.startHealsCount = HealingSummary.getInstance().getHealCount();
        scenarioHeals.put(scenarioId, info);
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        String scenarioId = event.getTestCase().getId().toString();
        ScenarioHealInfo info = scenarioHeals.get(scenarioId);

        if (info != null) {
            int endHealsCount = HealingSummary.getInstance().getHealCount();
            info.healsInScenario = endHealsCount - info.startHealsCount;
            info.passed = event.getResult().getStatus() == Status.PASSED;
            info.duration = event.getResult().getDuration().toMillis();

            // Capture heals for this scenario
            List<HealedLocator> allHeals = HealingSummary.getInstance().getHealedLocators();
            if (info.startHealsCount < allHeals.size()) {
                info.heals = new ArrayList<>(allHeals.subList(info.startHealsCount, allHeals.size()));
            }

            scenarioResults.add(new ScenarioResult(info));
        }
    }

    private void onTestRunFinished(TestRunFinished event) {
        try {
            generateReport();
            logger.info("Healer Cucumber Report generated: {}", outputPath);
        } catch (IOException e) {
            logger.error("Failed to generate healer report", e);
        }
    }

    private void generateReport() throws IOException {
        Path outputFile = Path.of(outputPath);
        Files.createDirectories(outputFile.getParent());

        List<HealedLocator> allHeals = HealingSummary.getInstance().getHealedLocators();

        String html = generateHtml(allHeals);

        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            writer.write(html);
        }
    }

    private String generateHtml(List<HealedLocator> heals) {
        StringBuilder html = new StringBuilder();

        int totalScenarios = scenarioResults.size();
        int passedScenarios = (int) scenarioResults.stream().filter(s -> s.passed).count();
        int scenariosWithHeals = (int) scenarioResults.stream().filter(s -> s.healsCount > 0).count();
        int totalHeals = heals.size();

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Intent Healer - Cucumber Report</title>
                <style>
                    :root {
                        --primary: #6366f1;
                        --success: #22c55e;
                        --warning: #f59e0b;
                        --danger: #ef4444;
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
                    }
                    .container { max-width: 1400px; margin: 0 auto; padding: 20px; }

                    header {
                        background: linear-gradient(135deg, var(--primary), #8b5cf6);
                        color: white;
                        padding: 30px;
                        margin-bottom: 30px;
                        border-radius: 12px;
                    }
                    header h1 { font-size: 2em; margin-bottom: 5px; }
                    header p { opacity: 0.9; }

                    .stats {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 20px;
                        margin-bottom: 30px;
                    }
                    .stat-card {
                        background: var(--card);
                        padding: 20px;
                        border-radius: 12px;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                        text-align: center;
                    }
                    .stat-value {
                        font-size: 2.5em;
                        font-weight: bold;
                        color: var(--primary);
                    }
                    .stat-value.success { color: var(--success); }
                    .stat-value.warning { color: var(--warning); }
                    .stat-label { color: var(--muted); font-size: 0.9em; margin-top: 5px; }

                    .section {
                        background: var(--card);
                        border-radius: 12px;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                        margin-bottom: 30px;
                        overflow: hidden;
                    }
                    .section-header {
                        background: #f1f5f9;
                        padding: 15px 20px;
                        font-weight: 600;
                        border-bottom: 1px solid var(--border);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .section-body { padding: 20px; }

                    .heal-item {
                        border: 1px solid var(--border);
                        border-radius: 8px;
                        margin-bottom: 15px;
                        overflow: hidden;
                    }
                    .heal-item:last-child { margin-bottom: 0; }
                    .heal-header {
                        padding: 15px;
                        background: #f8fafc;
                        cursor: pointer;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .heal-header:hover { background: #f1f5f9; }
                    .heal-body {
                        padding: 15px;
                        border-top: 1px solid var(--border);
                        display: none;
                    }
                    .heal-item.expanded .heal-body { display: block; }

                    .locator-box {
                        background: #1e293b;
                        color: #e2e8f0;
                        padding: 12px 15px;
                        border-radius: 6px;
                        font-family: 'Monaco', 'Menlo', monospace;
                        font-size: 0.9em;
                        margin: 10px 0;
                        overflow-x: auto;
                    }
                    .locator-label {
                        font-size: 0.8em;
                        color: var(--muted);
                        text-transform: uppercase;
                        margin-bottom: 5px;
                    }
                    .locator-original { border-left: 3px solid var(--danger); }
                    .locator-healed { border-left: 3px solid var(--success); }

                    .confidence-badge {
                        display: inline-block;
                        padding: 4px 10px;
                        border-radius: 20px;
                        font-size: 0.85em;
                        font-weight: 500;
                    }
                    .confidence-high { background: #dcfce7; color: #166534; }
                    .confidence-medium { background: #fef3c7; color: #92400e; }
                    .confidence-low { background: #fee2e2; color: #991b1b; }

                    .scenario-badge {
                        display: inline-block;
                        padding: 3px 8px;
                        border-radius: 4px;
                        font-size: 0.75em;
                        font-weight: 500;
                    }
                    .scenario-passed { background: #dcfce7; color: #166534; }
                    .scenario-failed { background: #fee2e2; color: #991b1b; }

                    .copy-btn {
                        background: var(--primary);
                        color: white;
                        border: none;
                        padding: 6px 12px;
                        border-radius: 4px;
                        cursor: pointer;
                        font-size: 0.85em;
                    }
                    .copy-btn:hover { opacity: 0.9; }

                    .empty-state {
                        text-align: center;
                        padding: 40px;
                        color: var(--muted);
                    }
                    .empty-state svg { width: 64px; height: 64px; margin-bottom: 15px; }

                    table { width: 100%%; border-collapse: collapse; }
                    th, td { padding: 12px; text-align: left; border-bottom: 1px solid var(--border); }
                    th { background: #f8fafc; font-weight: 600; }
                    tr:hover { background: #f8fafc; }

                    .arrow { transition: transform 0.2s; }
                    .heal-item.expanded .arrow { transform: rotate(90deg); }

                    @media (max-width: 768px) {
                        .stats { grid-template-columns: repeat(2, 1fr); }
                        .stat-value { font-size: 1.8em; }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>Intent Healer Report</h1>
                        <p>Self-Healing Test Execution Summary - %s</p>
                    </header>

                    <div class="stats">
                        <div class="stat-card">
                            <div class="stat-value">%d</div>
                            <div class="stat-label">Total Scenarios</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value success">%d</div>
                            <div class="stat-label">Passed</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value warning">%d</div>
                            <div class="stat-label">Scenarios Healed</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value">%d</div>
                            <div class="stat-label">Total Heals Applied</div>
                        </div>
                    </div>
            """.formatted(timestamp, totalScenarios, passedScenarios, scenariosWithHeals, totalHeals));

        // Healed Locators Section
        html.append("""
                    <div class="section">
                        <div class="section-header">
                            <span>Healed Locators</span>
                            <span style="color: var(--muted); font-weight: normal;">Click to expand</span>
                        </div>
                        <div class="section-body">
            """);

        if (heals.isEmpty()) {
            html.append("""
                            <div class="empty-state">
                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                </svg>
                                <p>No healing was required - all locators worked correctly!</p>
                            </div>
                """);
        } else {
            int index = 1;
            for (HealedLocator heal : heals) {
                String confidenceClass = heal.confidence() >= 0.9 ? "confidence-high" :
                                         heal.confidence() >= 0.75 ? "confidence-medium" : "confidence-low";

                html.append("""
                            <div class="heal-item" onclick="this.classList.toggle('expanded')">
                                <div class="heal-header">
                                    <div>
                                        <span class="arrow">▶</span>
                                        <strong>#%d</strong> %s
                                    </div>
                                    <span class="confidence-badge %s">%.0f%% confidence</span>
                                </div>
                                <div class="heal-body">
                                    <div class="locator-label">Original Locator (broken)</div>
                                    <div class="locator-box locator-original">%s</div>

                                    <div class="locator-label">Healed Locator (working)</div>
                                    <div class="locator-box locator-healed">%s</div>

                                    <div style="margin-top: 15px;">
                                        <button class="copy-btn" onclick="event.stopPropagation(); navigator.clipboard.writeText('%s')">
                                            Copy Healed Locator
                                        </button>
                                    </div>
                                </div>
                            </div>
                    """.formatted(
                        index++,
                        escapeHtml(truncate(heal.stepText(), 80)),
                        confidenceClass,
                        heal.confidence() * 100,
                        escapeHtml(heal.originalLocator()),
                        escapeHtml(heal.healedLocator()),
                        escapeJsString(heal.healedLocator())
                    ));
            }
        }

        html.append("""
                        </div>
                    </div>
            """);

        // Scenario Summary Table
        html.append("""
                    <div class="section">
                        <div class="section-header">
                            <span>Scenario Summary</span>
                        </div>
                        <div class="section-body" style="padding: 0;">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Scenario</th>
                                        <th>Feature</th>
                                        <th>Status</th>
                                        <th>Heals</th>
                                        <th>Duration</th>
                                    </tr>
                                </thead>
                                <tbody>
            """);

        for (ScenarioResult result : scenarioResults) {
            String statusClass = result.passed ? "scenario-passed" : "scenario-failed";
            String statusText = result.passed ? "PASSED" : "FAILED";
            String healsText = result.healsCount > 0 ? String.valueOf(result.healsCount) : "-";

            html.append("""
                                    <tr>
                                        <td>%s</td>
                                        <td style="color: var(--muted);">%s</td>
                                        <td><span class="scenario-badge %s">%s</span></td>
                                        <td>%s</td>
                                        <td style="color: var(--muted);">%.2fs</td>
                                    </tr>
                """.formatted(
                    escapeHtml(result.scenarioName),
                    escapeHtml(result.featureName),
                    statusClass,
                    statusText,
                    healsText,
                    result.duration / 1000.0
                ));
        }

        html.append("""
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <footer style="text-align: center; color: var(--muted); padding: 20px;">
                        Generated by Intent Healer
                    </footer>
                </div>
            </body>
            </html>
            """);

        return html.toString();
    }

    private String extractFeatureName(String uri) {
        int lastSlash = uri.lastIndexOf('/');
        if (lastSlash >= 0) {
            return uri.substring(lastSlash + 1).replace(".feature", "");
        }
        return uri;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private String escapeJsString(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("'", "\\'")
                   .replace("\"", "\\\"");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private static class ScenarioHealInfo {
        String scenarioName;
        String featureName;
        int startHealsCount;
        int healsInScenario;
        boolean passed;
        long duration;
        List<HealedLocator> heals = new ArrayList<>();
    }

    private static class ScenarioResult {
        final String scenarioName;
        final String featureName;
        final boolean passed;
        final int healsCount;
        final long duration;

        ScenarioResult(ScenarioHealInfo info) {
            this.scenarioName = info.scenarioName;
            this.featureName = info.featureName;
            this.passed = info.passed;
            this.healsCount = info.healsInScenario;
            this.duration = info.duration;
        }
    }
}
