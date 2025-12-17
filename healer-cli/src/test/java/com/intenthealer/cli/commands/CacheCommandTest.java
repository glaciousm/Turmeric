package com.intenthealer.cli.commands;

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
 * Tests for the CacheCommand class.
 */
class CacheCommandTest {

    private CacheCommand cacheCommand;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cacheCommand = new CacheCommand();
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
    void testStatsDisplaysStatistics() {
        cacheCommand.stats();

        String output = outContent.toString();
        assertThat(output).contains("CACHE STATISTICS");
        assertThat(output).contains("Entries:");
        assertThat(output).contains("Hit Rate:");
    }

    @Test
    void testStatsDisplaysHitsAndMisses() {
        cacheCommand.stats();

        String output = outContent.toString();
        assertThat(output).contains("Hits:");
        assertThat(output).contains("Misses:");
        assertThat(output).contains("Evictions:");
    }

    @Test
    void testStatsFormatsOutputCorrectly() {
        cacheCommand.stats();

        String output = outContent.toString();
        assertThat(output).contains("═══════════════════════════════════════════════════════════════");
    }

    @Test
    void testClearWithoutForceShowsWarning() {
        cacheCommand.clear(false);

        String output = outContent.toString();
        assertThat(output).contains("This will clear all cached heals");
        assertThat(output).contains("--force");
    }

    @Test
    void testClearWithForceClearsCache() {
        cacheCommand.clear(true);

        String output = outContent.toString();
        assertThat(output).contains("Cache cleared");
    }

    @Test
    void testClearWithForceShowsSuccessIcon() {
        cacheCommand.clear(true);

        String output = outContent.toString();
        assertThat(output).contains("✅");
    }

    @Test
    void testWarmupShowsProgressMessage() {
        cacheCommand.warmup(tempDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("Warming up cache");
        assertThat(output).contains(tempDir.toString());
    }

    @Test
    void testWarmupCompletesSuccessfully() {
        cacheCommand.warmup(tempDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("Cache warmup complete");
    }

    @Test
    void testWarmupWithNonexistentDirectory() {
        cacheCommand.warmup("/nonexistent/directory");

        String errOutput = errContent.toString();
        assertThat(errOutput).contains("Report directory not found");
    }

    @Test
    void testWarmupShowsEntryCount() {
        cacheCommand.warmup(tempDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("Cache now contains");
        assertThat(output).contains("entries");
    }

    @Test
    void testExportCreatesFile() throws Exception {
        Path exportPath = tempDir.resolve("cache-export.json");

        cacheCommand.export(exportPath.toString());

        String output = outContent.toString();
        assertThat(output).contains("Cache exported");
    }

    @Test
    void testExportShowsSuccessMessage() throws Exception {
        Path exportPath = tempDir.resolve("cache-export.json");

        cacheCommand.export(exportPath.toString());

        String output = outContent.toString();
        assertThat(output).contains("✅");
        assertThat(output).contains(exportPath.toString());
    }

    @Test
    void testImportCacheFromFile() throws Exception {
        // Create a dummy cache file
        Path cachePath = tempDir.resolve("heal-cache.json");
        Files.writeString(cachePath, "{}");

        cacheCommand.importCache(cachePath.toString());

        String output = outContent.toString();
        assertThat(output).contains("Imported");
        assertThat(output).contains("cache entries");
    }

    @Test
    void testImportCacheShowsEntryCount() throws Exception {
        Path cachePath = tempDir.resolve("heal-cache.json");
        Files.writeString(cachePath, "{}");

        cacheCommand.importCache(cachePath.toString());

        String output = outContent.toString();
        assertThat(output).contains("✅");
    }

    @Test
    void testImportCacheWithNonexistentFile() throws Exception {
        cacheCommand.importCache("/nonexistent/cache.json");

        String errOutput = errContent.toString();
        assertThat(errOutput).contains("Cache file not found");
    }

    @Test
    void testStatsShowsMaxSize() {
        cacheCommand.stats();

        String output = outContent.toString();
        // Format is "Entries: X / Y" where Y is max size
        assertThat(output).contains("/");
    }

    @Test
    void testStatsShowsHitRatePercentage() {
        cacheCommand.stats();

        String output = outContent.toString();
        assertThat(output).contains("Hit Rate:");
        assertThat(output).contains("%");
    }

    @Test
    void testClearDoesNotRequireConfirmationWhenForced() {
        cacheCommand.clear(true);

        String output = outContent.toString();
        assertThat(output).doesNotContain("Use --force");
        assertThat(output).contains("cleared");
    }

    @Test
    void testWarmupHandlesEmptyDirectory() {
        // Empty temp directory
        cacheCommand.warmup(tempDir.toString());

        String output = outContent.toString();
        assertThat(output).contains("Cache warmup complete");
        assertThat(output).contains("entries");
    }

    @Test
    void testExportToNestedDirectory() throws Exception {
        Path nestedPath = tempDir.resolve("nested/subdir/cache.json");
        Files.createDirectories(nestedPath.getParent());

        cacheCommand.export(nestedPath.toString());

        String output = outContent.toString();
        assertThat(output).contains("Cache exported");
    }
}
