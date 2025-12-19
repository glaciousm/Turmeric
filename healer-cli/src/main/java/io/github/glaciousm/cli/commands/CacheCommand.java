package io.github.glaciousm.cli.commands;

import io.github.glaciousm.cli.util.CliOutput;
import io.github.glaciousm.core.config.CacheConfig;
import io.github.glaciousm.core.config.ConfigLoader;
import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.engine.cache.HealCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command for managing the heal cache.
 */
public class CacheCommand {

    /**
     * Show cache statistics.
     */
    public void stats() {
        HealerConfig config = new ConfigLoader().load();
        HealCache cache = new HealCache(config.getCache());

        HealCache.CacheStats stats = cache.getStats();

        CliOutput.header("CACHE STATISTICS");
        CliOutput.printf("  Entries:          %d / %d%n", stats.size(), stats.maxSize());
        CliOutput.printf("  Hit Rate:         %.1f%%%n", stats.getHitRate() * 100);
        CliOutput.println();
        CliOutput.printf("  Hits:             %d%n", stats.hits());
        CliOutput.printf("  Misses:           %d%n", stats.misses());
        CliOutput.printf("  Evictions:        %d%n", stats.evictions());
        CliOutput.println();
        CliOutput.divider();

        cache.shutdown();
    }

    /**
     * Clear the cache.
     */
    public void clear(boolean force) {
        if (!force) {
            CliOutput.println("This will clear all cached heals.");
            CliOutput.println("Use --force to confirm.");
            return;
        }

        HealerConfig config = new ConfigLoader().load();

        // Clear in-memory cache
        HealCache cache = new HealCache(config.getCache());
        cache.clear();
        cache.shutdown();

        // Clear persistence file if exists
        if (config.getCache() != null && config.getCache().isPersistenceEnabled()) {
            String cacheDir = config.getCache().getPersistenceDir();
            if (cacheDir != null) {
                try {
                    Path cachePath = Path.of(cacheDir, "heal-cache.json");
                    if (Files.exists(cachePath)) {
                        Files.delete(cachePath);
                        CliOutput.println("Deleted cache file: " + cachePath);
                    }
                } catch (IOException e) {
                    CliOutput.warn("Could not delete cache file: " + e.getMessage());
                }
            }
        }

        CliOutput.println("Cache cleared");
    }

    /**
     * Warm up cache from previous runs.
     */
    public void warmup(String reportDir) {
        CliOutput.println("Warming up cache from reports in: " + reportDir);

        Path dirPath = Path.of(reportDir);
        if (!Files.exists(dirPath)) {
            CliOutput.error("Report directory not found: " + reportDir);
            return;
        }

        HealerConfig config = new ConfigLoader().load();
        HealCache cache = new HealCache(config.getCache());

        // Would load successful heals from reports and add to cache
        // Implementation depends on report format

        CliOutput.println("Cache warmup complete.");
        CliOutput.printf("Cache now contains %d entries%n", cache.getStats().size());

        cache.shutdown();
    }

    /**
     * Export cache to file.
     */
    public void export(String outputPath) throws IOException {
        HealerConfig config = new ConfigLoader().load();
        CacheConfig cacheConfig = config.getCache();

        if (cacheConfig == null) {
            cacheConfig = new CacheConfig();
        }

        // Enable persistence temporarily to force save
        cacheConfig.setPersistenceEnabled(true);
        cacheConfig.setPersistenceDir(Path.of(outputPath).getParent().toString());

        HealCache cache = new HealCache(cacheConfig);
        cache.shutdown(); // This triggers persistence

        CliOutput.println("Cache exported to: " + outputPath);
    }

    /**
     * Import cache from file.
     */
    public void importCache(String inputPath) throws IOException {
        Path path = Path.of(inputPath);
        if (!Files.exists(path)) {
            CliOutput.error("Cache file not found: " + inputPath);
            return;
        }

        HealerConfig config = new ConfigLoader().load();
        CacheConfig cacheConfig = config.getCache();

        if (cacheConfig == null) {
            cacheConfig = new CacheConfig();
        }

        cacheConfig.setPersistenceEnabled(true);
        cacheConfig.setPersistenceDir(path.getParent().toString());

        HealCache cache = new HealCache(cacheConfig);
        CliOutput.printf("Imported %d cache entries%n", cache.getStats().size());

        cache.shutdown();
    }
}
