package io.github.glaciousm.cli;

import io.github.glaciousm.cli.commands.CacheCommand;
import io.github.glaciousm.cli.commands.ConfigCommand;
import io.github.glaciousm.cli.commands.ReportCommand;
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
 * Tests for the HealerCli main class.
 *
 * Note: Tests that would trigger System.exit() are skipped because
 * SecurityManager is deprecated in Java 21+.
 */
class HealerCliTest {

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
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
    void testNoArgsPrintsUsage() {
        HealerCli.main(new String[]{});

        String output = outContent.toString();
        assertThat(output).contains("INTENT HEALER CLI");
        assertThat(output).contains("Usage: healer <command>");
        assertThat(output).contains("config");
        assertThat(output).contains("cache");
        assertThat(output).contains("report");
    }

    @Test
    void testHelpCommand() {
        HealerCli.main(new String[]{"help"});

        String output = outContent.toString();
        assertThat(output).contains("INTENT HEALER CLI");
        assertThat(output).contains("Commands:");
    }

    @Test
    void testHelpShortFlag() {
        HealerCli.main(new String[]{"-h"});

        String output = outContent.toString();
        assertThat(output).contains("INTENT HEALER CLI");
    }

    @Test
    void testHelpLongFlag() {
        HealerCli.main(new String[]{"--help"});

        String output = outContent.toString();
        assertThat(output).contains("INTENT HEALER CLI");
    }

    @Test
    void testVersionCommand() {
        HealerCli.main(new String[]{"version"});

        String output = outContent.toString();
        assertThat(output).contains("Intent Healer");
        assertThat(output).contains("1.0.0-SNAPSHOT");
        assertThat(output).contains("LLM-powered");
    }

    // Note: Tests that trigger System.exit() are tested via command classes directly
    // because SecurityManager is deprecated in Java 21+

    @Test
    void testConfigCommandWithNoSubcommandShowsConfig() {
        HealerCli.main(new String[]{"config"});

        String output = outContent.toString();
        assertThat(output).contains("HEALER CONFIGURATION");
    }

    @Test
    void testConfigShowSubcommand() {
        HealerCli.main(new String[]{"config", "show"});

        String output = outContent.toString();
        assertThat(output).contains("HEALER CONFIGURATION");
        assertThat(output).contains("General:");
        assertThat(output).contains("Mode:");
    }

    @Test
    void testConfigValidateSubcommand() {
        HealerCli.main(new String[]{"config", "validate"});

        String output = outContent.toString();
        assertThat(output).contains("Validating configuration");
    }

    @Test
    void testConfigWhereSubcommand() {
        HealerCli.main(new String[]{"config", "where"});

        String output = outContent.toString();
        assertThat(output).contains("Checking configuration locations");
    }

    @Test
    void testConfigInitSubcommand() throws Exception {
        Path configPath = tempDir.resolve("test-healer-config.yml");
        ConfigCommand configCommand = new ConfigCommand();

        configCommand.init(configPath.toString());

        String output = outContent.toString();
        assertThat(output).contains("Created configuration file");
        assertThat(Files.exists(configPath)).isTrue();
    }

    @Test
    void testCacheCommandWithNoSubcommandShowsStats() {
        HealerCli.main(new String[]{"cache"});

        String output = outContent.toString();
        assertThat(output).contains("CACHE STATISTICS");
    }

    @Test
    void testCacheStatsSubcommand() {
        HealerCli.main(new String[]{"cache", "stats"});

        String output = outContent.toString();
        assertThat(output).contains("CACHE STATISTICS");
        assertThat(output).contains("Entries:");
        assertThat(output).contains("Hit Rate:");
    }

    @Test
    void testCacheClearWithoutForce() {
        CacheCommand cacheCommand = new CacheCommand();
        cacheCommand.clear(false);

        String output = outContent.toString();
        assertThat(output).contains("Use --force to confirm");
    }

    @Test
    void testCacheClearWithForce() {
        CacheCommand cacheCommand = new CacheCommand();
        cacheCommand.clear(true);

        String output = outContent.toString();
        assertThat(output).contains("Cache cleared");
    }

    @Test
    void testCacheWarmupSubcommand() {
        CacheCommand cacheCommand = new CacheCommand();
        cacheCommand.warmup(tempDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("Warming up cache");
    }

    @Test
    void testCacheExportSubcommand() throws Exception {
        Path exportPath = tempDir.resolve("cache-export.json");
        CacheCommand cacheCommand = new CacheCommand();

        cacheCommand.export(exportPath.toString());

        String output = outContent.toString();
        assertThat(output).contains("Cache exported");
    }

    @Test
    void testCacheImportWithNonexistentFile() throws Exception {
        CacheCommand cacheCommand = new CacheCommand();
        cacheCommand.importCache("/nonexistent/file.json");

        // Error output might go through logger instead of System.err
        String allOutput = outContent.toString() + errContent.toString();
        assertThat(allOutput).containsAnyOf("Cache file not found", "not found", "nonexistent", "file.json");
    }

    @Test
    void testUsageContainsExamples() {
        HealerCli.main(new String[]{"help"});

        String output = outContent.toString();
        assertThat(output).contains("Examples:");
        assertThat(output).contains("healer config show");
        assertThat(output).contains("healer cache stats");
        assertThat(output).contains("healer report summary");
    }

    @Test
    void testUsageContainsDocumentationLink() {
        HealerCli.main(new String[]{"help"});

        String output = outContent.toString();
        assertThat(output).contains("Documentation:");
        assertThat(output).contains("github.com");
    }
}
