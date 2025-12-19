package io.github.glaciousm.cli.commands;

import io.github.glaciousm.cli.util.CliOutput;
import io.github.glaciousm.core.config.ConfigLoader;
import io.github.glaciousm.core.config.HealerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command for managing healer configuration.
 */
public class ConfigCommand {

    /**
     * Show current configuration.
     */
    public void show() {
        HealerConfig config = new ConfigLoader().load();

        CliOutput.header("HEALER CONFIGURATION");

        CliOutput.println("General:");
        CliOutput.printf("  Mode:             %s%n", config.getMode());
        CliOutput.printf("  Enabled:          %s%n", config.isEnabled());
        CliOutput.println();

        if (config.getLlm() != null) {
            CliOutput.println("LLM:");
            CliOutput.printf("  Provider:         %s%n", config.getLlm().getProvider());
            CliOutput.printf("  Model:            %s%n", config.getLlm().getModel());
            CliOutput.printf("  Timeout:          %ds%n", config.getLlm().getTimeoutSeconds());
            CliOutput.printf("  Max Retries:      %d%n", config.getLlm().getMaxRetries());
            CliOutput.println();
        }

        if (config.getGuardrails() != null) {
            CliOutput.println("Guardrails:");
            CliOutput.printf("  Min Confidence:   %.2f%n", config.getGuardrails().getMinConfidence());
            CliOutput.printf("  Max Heal Attempts: %d%n", config.getGuardrails().getMaxHealAttemptsPerStep());
            if (config.getGuardrails().getForbiddenKeywords() != null) {
                CliOutput.printf("  Forbidden Keywords: %d configured%n",
                        config.getGuardrails().getForbiddenKeywords().size());
            }
            CliOutput.println();
        }

        if (config.getCache() != null) {
            CliOutput.println("Cache:");
            CliOutput.printf("  Enabled:          %s%n", config.getCache().isEnabled());
            CliOutput.printf("  TTL:              %d hours%n", config.getCache().getTtlHours());
            CliOutput.printf("  Max Entries:      %d%n", config.getCache().getMaxEntries());
            CliOutput.printf("  Storage:          %s%n", config.getCache().getStorage());
            CliOutput.println();
        }

        if (config.getReport() != null) {
            CliOutput.println("Reports:");
            CliOutput.printf("  Enabled:          %s%n", config.getReport().isEnabled());
            CliOutput.printf("  Output Dir:       %s%n", config.getReport().getOutputDir());
            CliOutput.printf("  Format:           %s%n", config.getReport().getFormat());
            CliOutput.println();
        }

        CliOutput.divider();
    }

    /**
     * Validate configuration.
     */
    public void validate() {
        CliOutput.println("Validating configuration...");

        try {
            HealerConfig config = new ConfigLoader().load();

            boolean valid = true;
            int warnings = 0;

            // Check LLM configuration
            if (config.getLlm() == null) {
                CliOutput.error("LLM configuration is missing");
                valid = false;
            } else {
                if (config.getLlm().getProvider() == null || config.getLlm().getProvider().isEmpty()) {
                    CliOutput.error("LLM provider is not set");
                    valid = false;
                }
                if (config.getLlm().getApiKey() == null || config.getLlm().getApiKey().isEmpty()) {
                    CliOutput.warn("LLM API key is not set (will need to be set via env var)");
                    warnings++;
                }
            }

            // Check guardrails
            if (config.getGuardrails() == null) {
                CliOutput.warn("Guardrails configuration missing, using defaults");
                warnings++;
            } else {
                if (config.getGuardrails().getMinConfidence() < 0.5) {
                    CliOutput.warn("Min confidence is very low: " +
                            config.getGuardrails().getMinConfidence());
                    warnings++;
                }
            }

            // Summary
            CliOutput.println();
            if (valid) {
                if (warnings > 0) {
                    CliOutput.println("Configuration is valid with " + warnings + " warning(s)");
                } else {
                    CliOutput.println("Configuration is valid");
                }
            } else {
                CliOutput.error("Configuration has errors");
            }

        } catch (Exception e) {
            CliOutput.error("Failed to load configuration: " + e.getMessage());
        }
    }

    /**
     * Initialize default configuration file.
     */
    public void init(String outputPath) throws IOException {
        Path path = Path.of(outputPath);

        if (Files.exists(path)) {
            CliOutput.println("Configuration file already exists: " + outputPath);
            CliOutput.println("Use --force to overwrite");
            return;
        }

        String defaultConfig = """
                # Intent Healer Configuration

                healer:
                  mode: AUTO_SAFE
                  enabled: true

                llm:
                  provider: openai
                  model: gpt-4o-mini
                  # api_key: ${OPENAI_API_KEY}  # Set via environment variable
                  timeout_seconds: 30
                  max_retries: 3

                guardrails:
                  min_confidence: 0.80
                  max_candidates: 5
                  forbidden_keywords:
                    - delete
                    - remove
                    - cancel
                    - unsubscribe
                    - terminate

                cache:
                  enabled: true
                  ttl_hours: 24
                  max_entries: 10000
                  storage: memory

                circuit_breaker:
                  enabled: true
                  failure_threshold: 3
                  cooldown_minutes: 30

                reports:
                  enabled: true
                  output_dir: ./healer-reports
                  format: both
                  include_screenshots: true
                """;

        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
        Files.writeString(path, defaultConfig);

        CliOutput.println("Created configuration file: " + outputPath);
        CliOutput.println();
        CliOutput.println("Next steps:");
        CliOutput.println("  1. Set your LLM API key: export OPENAI_API_KEY=your-key");
        CliOutput.println("  2. Customize the configuration as needed");
        CliOutput.println("  3. Run your tests with the healer enabled");
    }

    /**
     * Show where configuration is being loaded from.
     */
    public void where() {
        String[] locations = {
                "healer-config.yml",
                "healer-config.yaml",
                "src/test/resources/healer-config.yml",
                ".healer/config.yml"
        };

        CliOutput.println("Checking configuration locations...");
        CliOutput.println();

        boolean found = false;
        for (String location : locations) {
            Path path = Path.of(location);
            if (Files.exists(path)) {
                CliOutput.println("Found: " + path.toAbsolutePath());
                found = true;
            } else {
                CliOutput.println("   Not found: " + location);
            }
        }

        CliOutput.println();
        if (!found) {
            CliOutput.println("No configuration file found. Using defaults.");
            CliOutput.println("Run 'healer config init' to create one.");
        }
    }
}
