package com.intenthealer.core.engine.cache;

import com.intenthealer.core.config.CacheConfig;
import com.intenthealer.core.model.ActionType;
import com.intenthealer.core.model.LocatorInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load and performance tests for HealCache.
 */
@DisplayName("HealCache Load Tests")
class HealCacheLoadTest {

    private HealCache cache;
    private CacheConfig config;

    @BeforeEach
    void setUp() {
        config = new CacheConfig();
        config.setEnabled(true);
        config.setMaxSize(100000);
        config.setTtlSeconds(3600);
        config.setMinConfidenceToCache(0.5);
        cache = new HealCache(config);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
    }

    @Nested
    @DisplayName("Large Scale Insertion Tests")
    class LargeScaleInsertionTests {

        @Test
        @DisplayName("should handle 10K entries efficiently")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void handles10KEntries() {
            int entryCount = 10_000;

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < entryCount; i++) {
                CacheKey key = createKey(i);
                LocatorInfo healedLocator = createHealed(i);
                cache.put(key, healedLocator, 0.9, "Test reasoning " + i);
            }

            long insertTime = System.currentTimeMillis() - startTime;
            System.out.println("Insert " + entryCount + " entries: " + insertTime + "ms");
            System.out.println("Average insert time: " + (insertTime / (double) entryCount) + "ms");

            assertEquals(entryCount, cache.getStats().size());
            assertTrue(insertTime < 30000, "Insert should complete within 30 seconds");
        }

        @Test
        @DisplayName("should handle 50K entries")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void handles50KEntries() {
            int entryCount = 50_000;

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < entryCount; i++) {
                CacheKey key = createKey(i);
                LocatorInfo healedLocator = createHealed(i);
                cache.put(key, healedLocator, 0.9, "Test reasoning " + i);
            }

            long insertTime = System.currentTimeMillis() - startTime;
            System.out.println("Insert " + entryCount + " entries: " + insertTime + "ms");

            assertEquals(entryCount, cache.getStats().size());
        }

        @Test
        @DisplayName("should evict oldest when max size reached")
        void evictsWhenFull() {
            config.setMaxSize(1000);
            HealCache smallCache = new HealCache(config);

            try {
                // Insert more than max size
                for (int i = 0; i < 1100; i++) {
                    CacheKey key = createKey(i);
                    LocatorInfo healedLocator = createHealed(i);
                    smallCache.put(key, healedLocator, 0.9, "Test reasoning");
                }

                // Should have evicted to stay at max size
                assertTrue(smallCache.getStats().size() <= 1000);
                assertTrue(smallCache.getStats().evictions() >= 100);
            } finally {
                smallCache.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("Retrieval Performance Tests")
    class RetrievalPerformanceTests {

        @Test
        @DisplayName("should have fast retrieval for large cache")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void fastRetrievalFromLargeCache() {
            int entryCount = 10_000;

            // Populate cache
            for (int i = 0; i < entryCount; i++) {
                CacheKey key = createKey(i);
                LocatorInfo healedLocator = createHealed(i);
                cache.put(key, healedLocator, 0.9, "Test reasoning");
            }

            // Measure retrieval time
            long startTime = System.currentTimeMillis();
            int hits = 0;

            for (int i = 0; i < entryCount; i++) {
                CacheKey key = createKey(i);
                Optional<LocatorInfo> result = cache.get(key);
                if (result.isPresent()) {
                    hits++;
                }
            }

            long retrievalTime = System.currentTimeMillis() - startTime;
            System.out.println("Retrieve " + entryCount + " entries: " + retrievalTime + "ms");
            System.out.println("Average retrieval time: " + (retrievalTime / (double) entryCount) + "ms");

            assertEquals(entryCount, hits);
            // Average retrieval should be under 1ms
            assertTrue(retrievalTime / (double) entryCount < 1.0,
                    "Average retrieval should be under 1ms");
        }

        @Test
        @DisplayName("should handle mixed hit/miss pattern")
        void handlesMixedHitMissPattern() {
            int entryCount = 5_000;

            // Populate with even numbers
            for (int i = 0; i < entryCount; i += 2) {
                CacheKey key = createKey(i);
                LocatorInfo healedLocator = createHealed(i);
                cache.put(key, healedLocator, 0.9, "Test reasoning");
            }

            // Access all numbers (half should hit, half should miss)
            int hits = 0;
            int misses = 0;

            for (int i = 0; i < entryCount; i++) {
                CacheKey key = createKey(i);
                Optional<LocatorInfo> result = cache.get(key);
                if (result.isPresent()) {
                    hits++;
                } else {
                    misses++;
                }
            }

            // Should have roughly 50% hit rate
            double hitRate = hits / (double) (hits + misses);
            assertEquals(0.5, hitRate, 0.01);
        }
    }

    @Nested
    @DisplayName("Concurrent Access Tests")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("should handle concurrent reads and writes")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void handlesConcurrentReadWrites() throws InterruptedException {
            int threads = 10;
            int operationsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threads);

            AtomicInteger writeErrors = new AtomicInteger(0);
            AtomicInteger readErrors = new AtomicInteger(0);

            // Start concurrent workers
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operationsPerThread; i++) {
                            int keyId = threadId * operationsPerThread + i;
                            CacheKey key = createKey(keyId);

                            // Write
                            try {
                                cache.put(key, createHealed(keyId), 0.9, "Concurrent test");
                            } catch (Exception e) {
                                writeErrors.incrementAndGet();
                            }

                            // Read
                            try {
                                cache.get(key);
                            } catch (Exception e) {
                                readErrors.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            // Start all threads at once
            startLatch.countDown();
            endLatch.await();
            executor.shutdown();

            assertEquals(0, writeErrors.get(), "No write errors expected");
            assertEquals(0, readErrors.get(), "No read errors expected");

            // Should have entries from all threads
            assertTrue(cache.getStats().size() > 0);
        }

        @Test
        @DisplayName("should handle concurrent invalidations")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void handlesConcurrentInvalidations() throws InterruptedException {
            int entries = 5000;

            // Populate cache
            for (int i = 0; i < entries; i++) {
                cache.put(createKey(i), createHealed(i), 0.9, "Test");
            }

            int threads = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threads);

            AtomicInteger errors = new AtomicInteger(0);

            // Start concurrent invalidators
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = threadId; i < entries; i += threads) {
                            try {
                                cache.invalidate(createKey(i));
                            } catch (Exception e) {
                                errors.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await();
            executor.shutdown();

            assertEquals(0, errors.get(), "No errors expected");
            assertEquals(0, cache.getStats().size(), "Cache should be empty");
        }
    }

    @Nested
    @DisplayName("TTL Expiration Tests")
    class TtlExpirationTests {

        @Test
        @DisplayName("should expire entries after TTL")
        void expiresEntriesAfterTtl() throws InterruptedException {
            config.setTtlSeconds(1); // 1 second TTL
            HealCache shortTtlCache = new HealCache(config);

            try {
                CacheKey key = createKey(1);
                shortTtlCache.put(key, createHealed(1), 0.9, "Test");

                // Should be present immediately
                assertTrue(shortTtlCache.get(key).isPresent());

                // Wait for expiration
                Thread.sleep(1500);

                // Should be expired
                assertFalse(shortTtlCache.get(key).isPresent());
                assertEquals(1, shortTtlCache.getStats().evictions());
            } finally {
                shortTtlCache.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("Memory Efficiency Tests")
    class MemoryEfficiencyTests {

        @Test
        @DisplayName("should not cause memory issues with 10K entries")
        void memoryEfficiencyWith10KEntries() {
            int entryCount = 10_000;

            // Get baseline memory
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long memBefore = runtime.totalMemory() - runtime.freeMemory();

            // Populate cache
            for (int i = 0; i < entryCount; i++) {
                CacheKey key = createKey(i);
                LocatorInfo healedLocator = createHealed(i);
                cache.put(key, healedLocator, 0.9, "Test reasoning for entry " + i);
            }

            runtime.gc();
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memUsed = memAfter - memBefore;

            System.out.println("Memory used for " + entryCount + " entries: " + (memUsed / 1024 / 1024) + " MB");
            System.out.println("Memory per entry: " + (memUsed / entryCount) + " bytes");

            // Should use less than 100MB for 10K entries
            assertTrue(memUsed < 100 * 1024 * 1024,
                    "Memory usage should be under 100MB for 10K entries");
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("should track hit rate accurately")
        void tracksHitRateAccurately() {
            // Insert 100 entries
            for (int i = 0; i < 100; i++) {
                cache.put(createKey(i), createHealed(i), 0.9, "Test");
            }

            // 50 hits (existing keys)
            for (int i = 0; i < 50; i++) {
                cache.get(createKey(i));
            }

            // 50 misses (non-existing keys)
            for (int i = 100; i < 150; i++) {
                cache.get(createKey(i));
            }

            HealCache.CacheStats stats = cache.getStats();
            assertEquals(100, stats.size());
            assertEquals(50, stats.hits());
            assertEquals(50, stats.misses());
            assertEquals(0.5, stats.getHitRate(), 0.01);
        }
    }

    // Helper methods

    private CacheKey createKey(int id) {
        LocatorInfo originalLocator = new LocatorInfo(
                LocatorInfo.LocatorStrategy.CSS,
                "#element-" + id
        );

        return CacheKey.builder()
                .pageUrl("https://example.com/page/" + (id % 100))
                .originalLocator(originalLocator)
                .actionType(ActionType.CLICK)
                .intentHint("click button " + id)
                .build();
    }

    private LocatorInfo createHealed(int id) {
        return new LocatorInfo(
                LocatorInfo.LocatorStrategy.CSS,
                "[data-testid='element-" + id + "']"
        );
    }
}
