/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark.reporters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.glaciousm.benchmark.BenchmarkResult;
import io.github.glaciousm.benchmark.BenchmarkResult.BenchmarkSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates JSON reports from benchmark results.
 */
public class JsonReporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Generate a JSON report from benchmark results.
     *
     * @param results The list of benchmark results
     * @param summary The aggregated summary
     * @param outputPath The path to write the JSON report
     * @throws IOException If writing fails
     */
    public void generateReport(List<BenchmarkResult> results, BenchmarkSummary summary,
                               Path outputPath) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();

        // Metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("generated", Instant.now().toString());
        metadata.put("llmProvider", summary.getLlmProvider());
        metadata.put("llmModel", summary.getLlmModel());
        metadata.put("totalScenarios", summary.getTotalScenarios());
        report.put("metadata", metadata);

        // Summary statistics
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("passed", summary.getPassed());
        stats.put("failed", summary.getFailed());
        stats.put("passRate", round(summary.getOverallPassRate()));
        stats.put("healSuccessRate", round(summary.getHealSuccessRate()));
        stats.put("falseHealRate", round(summary.getFalseHealRate()));
        stats.put("refusalAccuracy", round(summary.getRefusalAccuracy()));
        stats.put("healsCorrect", summary.getHealsCorrect());
        stats.put("healsFalse", summary.getHealsFalse());
        stats.put("refusalsCorrect", summary.getRefusalsCorrect());
        stats.put("refusalsIncorrect", summary.getRefusalsIncorrect());
        report.put("summary", stats);

        // Latency metrics
        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("totalMs", summary.getTotalLatency().toMillis());
        latency.put("p50Ms", summary.getP50Latency().toMillis());
        latency.put("p90Ms", summary.getP90Latency().toMillis());
        latency.put("p99Ms", summary.getP99Latency().toMillis());
        report.put("latency", latency);

        // Cost metrics
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("totalUsd", round(summary.getTotalCostUsd()));
        cost.put("averagePerHealUsd", round(summary.getAverageCostPerHeal()));
        report.put("cost", cost);

        // Category breakdown
        Map<String, Object> categories = new LinkedHashMap<>();
        for (Map.Entry<String, BenchmarkResult.CategoryStats> entry :
                summary.getCategoryStats().entrySet()) {
            Map<String, Object> catStats = new LinkedHashMap<>();
            catStats.put("total", entry.getValue().getTotal());
            catStats.put("passed", entry.getValue().getPassed());
            catStats.put("failed", entry.getValue().getFailed());
            catStats.put("passRate", round(entry.getValue().getPassRate()));
            categories.put(entry.getKey(), catStats);
        }
        report.put("categories", categories);

        // Individual results
        List<Map<String, Object>> resultMaps = results.stream().map(r -> {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("scenarioId", r.getScenarioId());
            rm.put("scenarioName", r.getScenarioName());
            rm.put("category", r.getCategory());
            rm.put("passed", r.isPassed());
            rm.put("expectedOutcome", r.getExpectedOutcome().name());
            rm.put("actualOutcome", r.getActualOutcome().name());
            rm.put("confidence", round(r.getConfidence()));
            rm.put("latencyMs", r.getLatency() != null ? r.getLatency().toMillis() : null);
            rm.put("originalLocator", r.getOriginalLocator());
            rm.put("healedLocator", r.getHealedLocator());
            rm.put("reasoning", r.getReasoning());
            rm.put("errorMessage", r.getErrorMessage());
            return rm;
        }).toList();
        report.put("results", resultMaps);

        // Write to file
        Files.createDirectories(outputPath.getParent());
        MAPPER.writeValue(outputPath.toFile(), report);
    }

    /**
     * Generate a filename with timestamp for the report.
     */
    public static String generateFilename(String provider, String model) {
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
        return String.format("benchmark_%s_%s_%s.json",
            sanitize(provider), sanitize(model), timestamp);
    }

    private static String sanitize(String input) {
        if (input == null) return "unknown";
        return input.replaceAll("[^a-zA-Z0-9.-]", "_").toLowerCase();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
