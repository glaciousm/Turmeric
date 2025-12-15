package com.intenthealer.cli.commands;

import com.intenthealer.core.config.ConfigLoader;
import com.intenthealer.core.config.HealerConfig;

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
        HealerConfig config = ConfigLoader.load();

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("                   HEALER CONFIGURATION                         ");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();

        System.out.println("General:");
        System.out.printf("  Mode:             %s%n", config.getMode());
        System.out.printf("  Enabled:          %s%n", config.isEnabled());
        System.out.println();

        if (config.getLlm() != null) {
            System.out.println("LLM:");
            System.out.printf("  Provider:         %s%n", config.getLlm().getProvider());
            System.out.printf("  Model:            %s%n", config.getLlm().getModel());
            System.out.printf("  Timeout:          %ds%n", config.getLlm().getTimeoutSeconds());
            System.out.printf("  Max Retries:      %d%n", config.getLlm().getMaxRetries());
            System.out.println();
        }

        if (config.getGuardrails() != null) {
            System.out.println("Guardrails:");
            System.out.printf("  Min Confidence:   %.2f%n", config.getGuardrails().getMinConfidence());
            System.out.printf("  Max Candidates:   %d%n", config.getGuardrails().getMaxCandidates());
            if (config.getGuardrails().getForbiddenKeywords() != null) {
                System.out.printf("  Forbidden Keywords: %d configured%n",
                        config.getGuardrails().getForbiddenKeywords().size());
            }
            System.out.println();
        }

        if (config.getCache() != null) {
            System.out.println("Cache:");
            System.out.printf("  Enabled:          %s%n", config.getCache().isEnabled());
            System.out.printf("  TTL:              %d hours%n", config.getCache().getTtlHours());
            System.out.printf("  Max Entries:      %d%n", config.getCache().getMaxEntries());
            System.out.printf("  Storage:          %s%n", config.getCache().getStorage());
            System.out.println();
        }

        if (config.getReports() != null) {
            System.out.println("Reports:");
            System.out.printf("  Enabled:          %s%n", config.getReports().isEnabled());
            System.out.printf("  Output Dir:       %s%n", config.getReports().getOutputDir());
            System.out.printf("  Format:           %s%n", config.getReports().getFormat());
            System.out.println();
        }

        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    /**
     * Validate configuration.
     */
    public void validate() {
        System.out.println("Validating configuration...");

        try {
            HealerConfig config = ConfigLoader.load();

            boolean valid = true;
            int warnings = 0;

            // Check LLM configuration
            if (config.getLlm() == null) {
                System.out.println("❌ LLM configuration is missing");
                valid = false;
            } else {
                if (config.getLlm().getProvider() == null || config.getLlm().getProvider().isEmpty()) {
                    System.out.println("❌ LLM provider is not set");
                    valid = false;
                }
                if (config.getLlm().getApiKey() == null || config.getLlm().getApiKey().isEmpty()) {
                    System.out.println("⚠️  LLM API key is not set (will need to be set via env var)");
                    warnings++;
                }
            }

            // Check guardrails
            if (config.getGuardrails() == null) {
                System.out.println("⚠️  Guardrails configuration missing, using defaults");
                warnings++;
            } else {
                if (config.getGuardrails().getMinConfidence() < 0.5) {
                    System.out.println("⚠️  Min confidence is very low: " +
                            config.getGuardrails().getMinConfidence());
                    warnings++;
                }
            }

            // Summary
            System.out.println();
            if (valid) {
                if (warnings > 0) {
                    System.out.println("✅ Configuration is valid with " + warnings + " warning(s)");
                } else {
                    System.out.println("✅ Configuration is valid");
                }
            } else {
                System.out.println("❌ Configuration has errors");
            }

        } catch (Exception e) {
            System.out.println("❌ Failed to load configuration: " + e.getMessage());
        }
    }

    /**
     * Initialize default configuration file.
     */
    public void init(String outputPath) throws IOException {
        Path path = Path.of(outputPath);

        if (Files.exists(path)) {
            System.out.println("Configuration file already exists: " + outputPath);
            System.out.println("Use --force to overwrite");
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

        System.out.println("✅ Created configuration file: " + outputPath);
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Set your LLM API key: export OPENAI_API_KEY=your-key");
        System.out.println("  2. Customize the configuration as needed");
        System.out.println("  3. Run your tests with the healer enabled");
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

        System.out.println("Checking configuration locations...");
        System.out.println();

        boolean found = false;
        for (String location : locations) {
            Path path = Path.of(location);
            if (Files.exists(path)) {
                System.out.println("✅ Found: " + path.toAbsolutePath());
                found = true;
            } else {
                System.out.println("   Not found: " + location);
            }
        }

        System.out.println();
        if (!found) {
            System.out.println("No configuration file found. Using defaults.");
            System.out.println("Run 'healer config init' to create one.");
        }
    }
}
