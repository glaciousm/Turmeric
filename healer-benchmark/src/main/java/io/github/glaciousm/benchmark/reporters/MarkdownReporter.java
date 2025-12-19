/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark.reporters;

import io.github.glaciousm.benchmark.BenchmarkResult;
import io.github.glaciousm.benchmark.BenchmarkResult.BenchmarkSummary;
import io.github.glaciousm.benchmark.BenchmarkResult.CategoryStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates Markdown reports from benchmark results.
 * Suitable for including in README or documentation.
 */
public class MarkdownReporter {

    /**
     * Generate a Markdown report from benchmark results.
     *
     * @param results The list of benchmark results
     * @param summary The aggregated summary
     * @param outputPath The path to write the Markdown report
     * @throws IOException If writing fails
     */
    public void generateReport(List<BenchmarkResult> results, BenchmarkSummary summary,
                               Path outputPath) throws IOException {
        StringBuilder md = new StringBuilder();

        // Header
        md.append("# Intent Healer Benchmark Results\n\n");

        // Metadata
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(summary.getRunTimestamp());
        md.append("**Generated:** ").append(timestamp).append("\n");
        md.append("**LLM Provider:** ").append(summary.getLlmProvider()).append("\n");
        md.append("**LLM Model:** ").append(summary.getLlmModel()).append("\n\n");

        // Summary badges (can be copied to README)
        md.append("## Summary Badges\n\n");
        md.append("```markdown\n");
        md.append(generateBadges(summary));
        md.append("```\n\n");

        // Key metrics table
        md.append("## Key Metrics\n\n");
        md.append("| Metric | Value |\n");
        md.append("|--------|-------|\n");
        md.append(String.format("| **Overall Pass Rate** | %.1f%% |\n", summary.getOverallPassRate()));
        md.append(String.format("| **Heal Success Rate** | %.1f%% |\n", summary.getHealSuccessRate()));
        md.append(String.format("| **False Heal Rate** | %.1f%% |\n", summary.getFalseHealRate()));
        md.append(String.format("| **Refusal Accuracy** | %.1f%% |\n", summary.getRefusalAccuracy()));
        md.append(String.format("| Total Scenarios | %d |\n", summary.getTotalScenarios()));
        md.append(String.format("| Passed | %d |\n", summary.getPassed()));
        md.append(String.format("| Failed | %d |\n", summary.getFailed()));
        md.append("\n");

        // Latency metrics
        md.append("## Latency\n\n");
        md.append("| Percentile | Time |\n");
        md.append("|------------|------|\n");
        md.append(String.format("| P50 | %dms |\n", summary.getP50Latency().toMillis()));
        md.append(String.format("| P90 | %dms |\n", summary.getP90Latency().toMillis()));
        md.append(String.format("| P99 | %dms |\n", summary.getP99Latency().toMillis()));
        md.append("\n");

        // Cost metrics (if applicable)
        if (summary.getTotalCostUsd() > 0) {
            md.append("## Cost\n\n");
            md.append(String.format("- **Total Cost:** $%.4f\n", summary.getTotalCostUsd()));
            md.append(String.format("- **Avg Cost per Heal:** $%.4f\n", summary.getAverageCostPerHeal()));
            md.append("\n");
        }

        // Category breakdown
        md.append("## Results by Category\n\n");
        md.append("| Category | Total | Passed | Failed | Pass Rate |\n");
        md.append("|----------|-------|--------|--------|----------|\n");
        for (Map.Entry<String, CategoryStats> entry : summary.getCategoryStats().entrySet()) {
            CategoryStats stats = entry.getValue();
            String status = stats.getPassRate() >= 90 ? "✅" :
                           (stats.getPassRate() >= 70 ? "⚠️" : "❌");
            md.append(String.format("| %s %s | %d | %d | %d | %.1f%% |\n",
                status, entry.getKey(),
                stats.getTotal(), stats.getPassed(), stats.getFailed(),
                stats.getPassRate()));
        }
        md.append("\n");

        // Detailed results table
        md.append("## Detailed Results\n\n");
        md.append("| # | Scenario | Category | Expected | Actual | Conf | Status |\n");
        md.append("|---|----------|----------|----------|--------|------|--------|\n");
        for (BenchmarkResult r : results) {
            String status = r.isPassed() ? "✅" : "❌";
            md.append(String.format("| %s | %s | %s | %s | %s | %.0f%% | %s |\n",
                r.getScenarioId(),
                truncate(r.getScenarioName(), 30),
                r.getCategory(),
                r.getExpectedOutcome(),
                r.getActualOutcome(),
                r.getConfidence() * 100,
                status));
        }
        md.append("\n");

        // Failed scenarios detail
        List<BenchmarkResult> failed = results.stream()
            .filter(r -> !r.isPassed())
            .toList();

        if (!failed.isEmpty()) {
            md.append("## Failed Scenarios\n\n");
            for (BenchmarkResult r : failed) {
                md.append(String.format("### Scenario %s: %s\n\n", r.getScenarioId(), r.getScenarioName()));
                md.append(String.format("- **Category:** %s\n", r.getCategory()));
                md.append(String.format("- **Expected:** %s\n", r.getExpectedOutcome()));
                md.append(String.format("- **Actual:** %s\n", r.getActualOutcome()));
                md.append(String.format("- **Original Locator:** `%s`\n", r.getOriginalLocator()));
                if (r.getHealedLocator() != null) {
                    md.append(String.format("- **Healed Locator:** `%s`\n", r.getHealedLocator()));
                }
                if (r.getReasoning() != null) {
                    md.append(String.format("- **Reasoning:** %s\n", r.getReasoning()));
                }
                if (r.getErrorMessage() != null) {
                    md.append(String.format("- **Error:** %s\n", r.getErrorMessage()));
                }
                md.append("\n");
            }
        }

        // Footer
        md.append("---\n\n");
        md.append("*Generated by Intent Healer Benchmark Suite*\n");

        // Write to file
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, md.toString());
    }

    /**
     * Generate badge markdown for README.
     */
    public String generateBadges(BenchmarkSummary summary) {
        StringBuilder badges = new StringBuilder();

        // Pass rate badge
        String passColor = getBadgeColor(summary.getOverallPassRate());
        badges.append(String.format(
            "[![Pass Rate](https://img.shields.io/badge/Pass_Rate-%.0f%%25-%s)]()%n",
            summary.getOverallPassRate(), passColor));

        // Heal success badge
        String healColor = getBadgeColor(summary.getHealSuccessRate());
        badges.append(String.format(
            "[![Heal Success](https://img.shields.io/badge/Heal_Success-%.0f%%25-%s)]()%n",
            summary.getHealSuccessRate(), healColor));

        // False heal badge (lower is better)
        String falseHealColor = summary.getFalseHealRate() <= 2 ? "green" :
                               (summary.getFalseHealRate() <= 5 ? "yellow" : "red");
        badges.append(String.format(
            "[![False Heal](https://img.shields.io/badge/False_Heal-%.0f%%25-%s)]()%n",
            summary.getFalseHealRate(), falseHealColor));

        // Provider badge
        badges.append(String.format(
            "[![Provider](https://img.shields.io/badge/Provider-%s-blue)]()%n",
            summary.getLlmProvider()));

        return badges.toString();
    }

    /**
     * Generate a single-line summary suitable for console output.
     */
    public String generateConsoleSummary(BenchmarkSummary summary) {
        return String.format(
            "Benchmark: %d/%d passed (%.1f%%) | Heal Success: %.1f%% | False Heal: %.1f%% | P50: %dms",
            summary.getPassed(), summary.getTotalScenarios(),
            summary.getOverallPassRate(),
            summary.getHealSuccessRate(),
            summary.getFalseHealRate(),
            summary.getP50Latency().toMillis());
    }

    private String getBadgeColor(double percentage) {
        if (percentage >= 90) return "brightgreen";
        if (percentage >= 80) return "green";
        if (percentage >= 70) return "yellow";
        if (percentage >= 50) return "orange";
        return "red";
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
}
