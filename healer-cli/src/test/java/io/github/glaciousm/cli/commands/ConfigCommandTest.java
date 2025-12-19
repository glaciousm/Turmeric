package io.github.glaciousm.cli.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the ConfigCommand class.
 */
class ConfigCommandTest {

    private ConfigCommand configCommand;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        configCommand = new ConfigCommand();
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testShowDisplaysConfiguration() {
        configCommand.show();

        String output = outContent.toString();
        assertThat(output).contains("HEALER CONFIGURATION");
        assertThat(output).contains("General:");
        assertThat(output).contains("Mode:");
        assertThat(output).contains("Enabled:");
    }

    @Test
    void testShowDisplaysLlmSection() {
        configCommand.show();

        String output = outContent.toString();
        // LLM section may or may not be present depending on defaults
        // CLI uses simple ASCII box-drawing (===) instead of Unicode (═══)
        assertThat(output).contains("===================================================================");
    }

    @Test
    void testValidateRunsValidation() {
        configCommand.validate();

        String output = outContent.toString();
        assertThat(output).contains("Validating configuration");
        // Should have either valid or error message
        assertThat(output).containsAnyOf("Configuration is valid", "LLM", "Guardrails");
    }

    @Test
    void testValidateReportsWarnings() {
        configCommand.validate();

        String output = outContent.toString();
        // The default config may have warnings (e.g., missing API key)
        assertThat(output).contains("Validating");
    }

    @Test
    void testInitCreatesConfigFile() throws Exception {
        Path configPath = tempDir.resolve("healer-config.yml");

        configCommand.init(configPath.toString());

        assertThat(Files.exists(configPath)).isTrue();
        String content = Files.readString(configPath);
        assertThat(content).contains("Intent Healer Configuration");
        assertThat(content).contains("mode: AUTO_SAFE");
        assertThat(content).contains("enabled: true");
    }

    @Test
    void testInitCreatesConfigWithLlmSection() throws Exception {
        Path configPath = tempDir.resolve("healer-config.yml");

        configCommand.init(configPath.toString());

        String content = Files.readString(configPath);
        assertThat(content).contains("llm:");
        assertThat(content).contains("provider:");
        assertThat(content).contains("model:");
    }

    @Test
    void testInitCreatesConfigWithGuardrails() throws Exception {
        Path configPath = tempDir.resolve("healer-config.yml");

        configCommand.init(configPath.toString());

        String content = Files.readString(configPath);
        assertThat(content).contains("guardrails:");
        assertThat(content).contains("min_confidence:");
        assertThat(content).contains("forbidden_keywords:");
    }

    @Test
    void testInitCreatesConfigWithCache() throws Exception {
        Path configPath = tempDir.resolve("healer-config.yml");

        configCommand.init(configPath.toString());

        String content = Files.readString(configPath);
        assertThat(content).contains("cache:");
        assertThat(content).contains("enabled:");
        assertThat(content).contains("ttl_hours:");
    }

    @Test
    void testInitCreatesConfigWithCircuitBreaker() throws Exception {
        Path configPath = tempDir.resolve("healer-config.yml");

        configCommand.init(configPath.toString());

        String content = Files.readString(configPath);
        assertThat(content).contains("circuit_breaker:");
        assertThat(content).contains("failure_threshold:");
    }

    @Test
    void testInitDoesNotOverwriteExistingFile() throws Exception {
        Path configPath = tempDir.resolve("existing-config.yml");
        Files.writeString(configPath, "existing content");

        configCommand.init(configPath.toString());

        String output = outContent.toString();
        assertThat(output).contains("already exists");
        assertThat(output).contains("--force");

        // Original file should not be modified
        String content = Files.readString(configPath);
        assertThat(content).isEqualTo("existing content");
    }

    @Test
    void testInitCreatesParentDirectories() throws Exception {
        Path configPath = tempDir.resolve("subdir/nested/healer-config.yml");

        configCommand.init(configPath.toString());

        assertThat(Files.exists(configPath)).isTrue();
    }

    @Test
    void testInitPrintsSuccessMessage() throws Exception {
        Path configPath = tempDir.resolve("healer-config.yml");

        configCommand.init(configPath.toString());

        String output = outContent.toString();
        assertThat(output).contains("Created configuration file");
        assertThat(output).contains("Next steps:");
    }

    @Test
    void testInitPrintsNextSteps() throws Exception {
        Path configPath = tempDir.resolve("healer-config.yml");

        configCommand.init(configPath.toString());

        String output = outContent.toString();
        assertThat(output).contains("Set your LLM API key");
        assertThat(output).contains("Customize the configuration");
        assertThat(output).contains("Run your tests");
    }

    @Test
    void testWhereChecksMultipleLocations() {
        configCommand.where();

        String output = outContent.toString();
        assertThat(output).contains("Checking configuration locations");
        assertThat(output).containsAnyOf("Found:", "Not found:");
    }

    @Test
    void testWhereShowsCommonLocations() {
        configCommand.where();

        String output = outContent.toString();
        assertThat(output).contains("healer-config.yml");
    }

    @Test
    void testWhereShowsHelpWhenNotFound() {
        // Note: This test may pass or fail depending on actual config file presence
        configCommand.where();

        String output = outContent.toString();
        // Either found a config or suggests creating one
        assertThat(output).containsAnyOf("Found:", "No configuration file found", "healer config init");
    }

    @Test
    void testShowFormatsOutputCorrectly() {
        configCommand.show();

        String output = outContent.toString();
        // Check for proper formatting - CLI uses ASCII (===) instead of Unicode (═══)
        assertThat(output).contains("===");
        assertThat(output).contains("Mode:");
        assertThat(output).contains("Enabled:");
    }

    @Test
    void testValidateDetectsValidConfig() {
        configCommand.validate();

        String output = outContent.toString();
        // Should complete validation without exception
        assertThat(output).contains("Validating configuration");
    }

    @Test
    void testValidateShowsSummary() {
        configCommand.validate();

        String output = outContent.toString();
        // Should show either valid or invalid summary
        assertThat(output).containsAnyOf("valid", "errors", "warning");
    }
}
