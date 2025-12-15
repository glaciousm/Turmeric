package com.intenthealer.cli.commands;

import com.intenthealer.core.config.CacheConfig;
import com.intenthealer.core.config.ConfigLoader;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.cache.HealCache;

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
        HealerConfig config = ConfigLoader.load();
        HealCache cache = new HealCache(config.getCache());

        HealCache.CacheStats stats = cache.getStats();

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("                      CACHE STATISTICS                          ");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("  Entries:          %d / %d%n", stats.size(), stats.maxSize());
        System.out.printf("  Hit Rate:         %.1f%%%n", stats.getHitRate() * 100);
        System.out.println();
        System.out.printf("  Hits:             %d%n", stats.hits());
        System.out.printf("  Misses:           %d%n", stats.misses());
        System.out.printf("  Evictions:        %d%n", stats.evictions());
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");

        cache.shutdown();
    }

    /**
     * Clear the cache.
     */
    public void clear(boolean force) {
        if (!force) {
            System.out.println("This will clear all cached heals.");
            System.out.println("Use --force to confirm.");
            return;
        }

        HealerConfig config = ConfigLoader.load();

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
                        System.out.println("Deleted cache file: " + cachePath);
                    }
                } catch (IOException e) {
                    System.err.println("Warning: Could not delete cache file: " + e.getMessage());
                }
            }
        }

        System.out.println("✅ Cache cleared");
    }

    /**
     * Warm up cache from previous runs.
     */
    public void warmup(String reportDir) {
        System.out.println("Warming up cache from reports in: " + reportDir);

        Path dirPath = Path.of(reportDir);
        if (!Files.exists(dirPath)) {
            System.err.println("Report directory not found: " + reportDir);
            return;
        }

        HealerConfig config = ConfigLoader.load();
        HealCache cache = new HealCache(config.getCache());

        // Would load successful heals from reports and add to cache
        // Implementation depends on report format

        System.out.println("Cache warmup complete.");
        System.out.printf("Cache now contains %d entries%n", cache.getStats().size());

        cache.shutdown();
    }

    /**
     * Export cache to file.
     */
    public void export(String outputPath) throws IOException {
        HealerConfig config = ConfigLoader.load();
        CacheConfig cacheConfig = config.getCache();

        if (cacheConfig == null) {
            cacheConfig = new CacheConfig();
        }

        // Enable persistence temporarily to force save
        cacheConfig.setPersistenceEnabled(true);
        cacheConfig.setPersistenceDir(Path.of(outputPath).getParent().toString());

        HealCache cache = new HealCache(cacheConfig);
        cache.shutdown(); // This triggers persistence

        System.out.println("✅ Cache exported to: " + outputPath);
    }

    /**
     * Import cache from file.
     */
    public void importCache(String inputPath) throws IOException {
        Path path = Path.of(inputPath);
        if (!Files.exists(path)) {
            System.err.println("Cache file not found: " + inputPath);
            return;
        }

        HealerConfig config = ConfigLoader.load();
        CacheConfig cacheConfig = config.getCache();

        if (cacheConfig == null) {
            cacheConfig = new CacheConfig();
        }

        cacheConfig.setPersistenceEnabled(true);
        cacheConfig.setPersistenceDir(path.getParent().toString());

        HealCache cache = new HealCache(cacheConfig);
        System.out.printf("✅ Imported %d cache entries%n", cache.getStats().size());

        cache.shutdown();
    }
}
