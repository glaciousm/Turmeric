/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark;

import io.github.glaciousm.benchmark.reporters.JsonReporter;
import io.github.glaciousm.benchmark.reporters.MarkdownReporter;
import io.github.glaciousm.benchmark.scenarios.*;
import io.github.glaciousm.core.config.ConfigLoader;
import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.config.LlmConfig;
import io.github.glaciousm.core.config.GuardrailConfig;
import io.github.glaciousm.core.model.HealPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main runner for executing all benchmark scenarios.
 *
 * Usage:
 *   mvn exec:java -pl healer-benchmark
 *   mvn exec:java -pl healer-benchmark -Dexec.args="--provider mock --output ./results"
 */
public class BenchmarkRunner {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final HealerConfig config;
    private final List<BenchmarkScenario> scenarios;
    private final Path outputDir;

    public BenchmarkRunner(HealerConfig config, Path outputDir) {
        this.config = config;
        this.scenarios = loadAllScenarios();
        this.outputDir = outputDir;
    }

    /**
     * Load all benchmark scenarios.
     */
    private List<BenchmarkScenario> loadAllScenarios() {
        List<BenchmarkScenario> all = new ArrayList<>();

        // Locator change scenarios (1-10)
        all.add(new IdChangedScenario());
        all.add(new IdRemovedScenario());
        all.add(new ClassChangedScenario());
        all.add(new ClassAddedScenario());
        all.add(new XPathIndexShiftedScenario());
        all.add(new CssSelectorBrokenScenario());
        all.add(new NameAttributeChangedScenario());
        all.add(new DataTestIdChangedScenario());
        all.add(new AriaLabelChangedScenario());
        all.add(new MultipleAttributesChangedScenario());

        // Element type changes (11-16)
        all.add(new ButtonToLinkScenario());
        all.add(new InputToTextareaScenario());
        all.add(new SelectToCustomDropdownScenario());
        all.add(new SpanToDivScenario());
        all.add(new TableToGridScenario());
        all.add(new FormRestructuredScenario());

        // Text/content changes (17-21)
        all.add(new ButtonTextChangedScenario());
        all.add(new PlaceholderChangedScenario());
        all.add(new LabelTextChangedScenario());
        all.add(new TextTranslatedScenario());
        all.add(new TextTruncatedScenario());

        // Negative tests - should NOT heal (22-27)
        all.add(new ElementRemovedScenario());
        all.add(new WrongPageScenario());
        all.add(new AmbiguousElementsScenario());
        all.add(new DestructiveActionScenario());
        all.add(new ForbiddenUrlScenario());
        all.add(new HiddenElementScenario());

        // False heal detection (28-32)
        all.add(new SimilarTextWrongButtonScenario());
        all.add(new SiblingInsteadOfTargetScenario());
        all.add(new ParentInsteadOfChildScenario());
        all.add(new WrongCriteriaHealScenario());
        all.add(new OutcomeValidationFailsScenario());

        // Complex DOM scenarios (33-35)
        all.add(new ShadowDomScenario());
        all.add(new IframeContentScenario());
        all.add(new DynamicContentScenario());

        return all;
    }

    /**
     * Run all benchmark scenarios.
     */
    public BenchmarkResult.BenchmarkSummary runAll() {
        logger.info("Starting benchmark run with {} scenarios", scenarios.size());
        String provider = config.getLlm() != null ? config.getLlm().getProvider() : "mock";
        String model = config.getLlm() != null ? config.getLlm().getModel() : "heuristic";
        logger.info("LLM Provider: {}, Model: {}", provider, model);

        List<BenchmarkResult> results = new ArrayList<>();

        for (BenchmarkScenario scenario : scenarios) {
            try {
                BenchmarkResult result = scenario.execute(config);
                results.add(result);
                logResult(result);
            } catch (Exception e) {
                logger.error("Error executing scenario {}: {}",
                    scenario.getId(), e.getMessage(), e);
                results.add(BenchmarkResult.builder()
                    .scenarioId(scenario.getId())
                    .scenarioName(scenario.getName())
                    .category(scenario.getCategory())
                    .expectedOutcome(scenario.getExpectedOutcome())
                    .actualOutcome(BenchmarkResult.ActualOutcome.ERROR)
                    .errorMessage(e.getMessage())
                    .passed(false)
                    .build());
            }
        }

        // Generate summary
        BenchmarkResult.BenchmarkSummary summary = new BenchmarkResult.BenchmarkSummary(
            results, provider, model);

        // Generate reports
        generateReports(results, summary);

        // Print console summary
        printConsoleSummary(summary);

        return summary;
    }

    /**
     * Run a specific category of scenarios.
     */
    public BenchmarkResult.BenchmarkSummary runCategory(String category) {
        List<BenchmarkScenario> filtered = scenarios.stream()
            .filter(s -> s.getCategory().equalsIgnoreCase(category))
            .toList();

        logger.info("Running {} scenarios in category: {}", filtered.size(), category);

        List<BenchmarkResult> results = new ArrayList<>();
        for (BenchmarkScenario scenario : filtered) {
            BenchmarkResult result = scenario.execute(config);
            results.add(result);
            logResult(result);
        }

        String provider = config.getLlm() != null ? config.getLlm().getProvider() : "mock";
        String model = config.getLlm() != null ? config.getLlm().getModel() : "heuristic";
        BenchmarkResult.BenchmarkSummary summary = new BenchmarkResult.BenchmarkSummary(
            results, provider, model);

        generateReports(results, summary);
        printConsoleSummary(summary);

        return summary;
    }

    /**
     * Run a single scenario by ID.
     */
    public BenchmarkResult runScenario(String scenarioId) {
        BenchmarkScenario scenario = scenarios.stream()
            .filter(s -> s.getId().equals(scenarioId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown scenario ID: " + scenarioId));

        return scenario.execute(config);
    }

    private void logResult(BenchmarkResult result) {
        String status = result.isPassed() ? "PASS" : "FAIL";
        logger.info("[{}] Scenario {}: {} - {} ({})",
            status, result.getScenarioId(), result.getScenarioName(),
            result.getActualOutcome(), formatConfidence(result.getConfidence()));
    }

    private String formatConfidence(double confidence) {
        return String.format("%.0f%%", confidence * 100);
    }

    private void generateReports(List<BenchmarkResult> results,
                                  BenchmarkResult.BenchmarkSummary summary) {
        try {
            // JSON report
            JsonReporter jsonReporter = new JsonReporter();
            String jsonFilename = JsonReporter.generateFilename(
                summary.getLlmProvider(), summary.getLlmModel());
            jsonReporter.generateReport(results, summary,
                outputDir.resolve(jsonFilename));
            logger.info("JSON report written to: {}", outputDir.resolve(jsonFilename));

            // Markdown report
            MarkdownReporter mdReporter = new MarkdownReporter();
            String mdFilename = jsonFilename.replace(".json", ".md");
            mdReporter.generateReport(results, summary,
                outputDir.resolve(mdFilename));
            logger.info("Markdown report written to: {}", outputDir.resolve(mdFilename));

        } catch (Exception e) {
            logger.error("Error generating reports: {}", e.getMessage(), e);
        }
    }

    private void printConsoleSummary(BenchmarkResult.BenchmarkSummary summary) {
        MarkdownReporter mdReporter = new MarkdownReporter();

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    INTENT HEALER - BENCHMARK RESULTS                       ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Provider: %-20s  Model: %-30s ║%n",
            summary.getLlmProvider(), summary.getLlmModel());
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Total: %3d  │  Passed: %3d  │  Failed: %3d  │  Pass Rate: %5.1f%%          ║%n",
            summary.getTotalScenarios(), summary.getPassed(), summary.getFailed(),
            summary.getOverallPassRate());
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Heal Success Rate: %5.1f%%   │  False Heal Rate: %5.1f%%                    ║%n",
            summary.getHealSuccessRate(), summary.getFalseHealRate());
        System.out.printf("║  Refusal Accuracy:  %5.1f%%   │  Avg Cost/Heal:   $%.4f                  ║%n",
            summary.getRefusalAccuracy(), summary.getAverageCostPerHeal());
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Latency - P50: %5dms  │  P90: %5dms  │  P99: %5dms                   ║%n",
            summary.getP50Latency().toMillis(),
            summary.getP90Latency().toMillis(),
            summary.getP99Latency().toMillis());
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Badges for README:");
        System.out.println(mdReporter.generateBadges(summary));
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        // Parse arguments - defaults
        String provider = System.getProperty("healer.provider", "mock");
        String model = System.getProperty("healer.model", "heuristic");
        String outputPath = System.getProperty("healer.output", "./target/benchmark-results");

        // Parse command line args
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--provider", "-p" -> provider = args[++i];
                case "--model", "-m" -> model = args[++i];
                case "--output", "-o" -> outputPath = args[++i];
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
            }
        }

        // Load base config if available, then override with CLI args
        HealerConfig config;
        try {
            config = new ConfigLoader().load();
            // Override with CLI arguments
            if (config.getLlm() == null) {
                config.setLlm(new LlmConfig());
            }
            config.getLlm().setProvider(provider);
            config.getLlm().setModel(model);
            logger.info("Loaded config, overriding with CLI: provider={}, model={}", provider, model);
        } catch (Exception e) {
            // Create minimal config with CLI args
            config = createMinimalConfig(provider, model);
            logger.info("Created minimal config: provider={}, model={}", provider, model);
        }

        // Ensure guardrails are configured for benchmarking
        configureBenchmarkGuardrails(config);

        // Run benchmarks
        BenchmarkRunner runner = new BenchmarkRunner(config, Paths.get(outputPath));
        BenchmarkResult.BenchmarkSummary summary = runner.runAll();

        // Exit with appropriate code
        System.exit(summary.getFailed() > 0 ? 1 : 0);
    }

    /**
     * Configure benchmark-specific guardrails.
     * This ensures guardrails are set even when loading from config file.
     */
    private static void configureBenchmarkGuardrails(HealerConfig config) {
        GuardrailConfig guardrails = config.getGuardrails();
        if (guardrails == null) {
            guardrails = new GuardrailConfig();
            config.setGuardrails(guardrails);
        }

        // Ensure minimum confidence threshold is set
        if (guardrails.getMinConfidence() == 0) {
            guardrails.setMinConfidence(0.75);
        }

        // Configure forbidden URL patterns - admin pages should not be healed
        if (guardrails.getForbiddenUrlPatterns() == null ||
            guardrails.getForbiddenUrlPatterns().isEmpty()) {
            guardrails.setForbiddenUrlPatterns(Arrays.asList(".*/admin/.*", ".*/admin$"));
        }
    }

    private static HealerConfig createMinimalConfig(String provider, String model) {
        HealerConfig config = new HealerConfig();
        config.setEnabled(true);
        config.setMode(HealPolicy.AUTO_SAFE);

        LlmConfig llm = new LlmConfig();
        llm.setProvider(provider);
        llm.setModel(model);
        llm.setTimeoutSeconds(60);
        llm.setMaxRetries(2);
        config.setLlm(llm);

        GuardrailConfig guardrails = new GuardrailConfig();
        guardrails.setMinConfidence(0.75);
        guardrails.setMaxHealsPerScenario(10);
        // Configure forbidden URL patterns - admin pages should not be healed
        guardrails.setForbiddenUrlPatterns(Arrays.asList(".*/admin/.*", ".*/admin$"));
        config.setGuardrails(guardrails);

        return config;
    }

    private static void printHelp() {
        System.out.println("Intent Healer Benchmark Runner");
        System.out.println();
        System.out.println("Usage: mvn exec:java -pl healer-benchmark [-Dexec.args=\"OPTIONS\"]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --provider, -p <name>   LLM provider (mock, ollama, openai, anthropic)");
        System.out.println("  --model, -m <name>      LLM model name");
        System.out.println("  --output, -o <path>     Output directory for reports");
        System.out.println("  --help, -h              Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  mvn exec:java -pl healer-benchmark");
        System.out.println("  mvn exec:java -pl healer-benchmark -Dexec.args=\"--provider ollama --model llama3.1\"");
        System.out.println("  mvn exec:java -pl healer-benchmark -Dexec.args=\"--provider openai --model gpt-4o-mini\"");
    }
}
