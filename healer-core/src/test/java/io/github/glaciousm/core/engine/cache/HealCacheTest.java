package io.github.glaciousm.core.engine.cache;

import io.github.glaciousm.core.config.CacheConfig;
import io.github.glaciousm.core.model.ActionType;
import io.github.glaciousm.core.model.LocatorInfo;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HealCache")
class HealCacheTest {

    private HealCache cache;

    @BeforeEach
    void setUp() {
        CacheConfig config = new CacheConfig();
        config.setEnabled(true);
        config.setMaxSize(100);
        config.setTtlSeconds(3600);
        config.setMinConfidenceToCache(0.7);
        config.setPersistenceEnabled(false);
        cache = new HealCache(config);
    }

    @AfterEach
    void tearDown() {
        cache.shutdown();
    }

    @Test
    @DisplayName("should cache and retrieve healed locators")
    void cacheAndRetrieve() {
        CacheKey key = CacheKey.builder()
                .pageUrl("https://example.com/login")
                .originalLocator(new LocatorInfo("id", "submit"))
                .actionType(ActionType.CLICK)
                .build();

        LocatorInfo healed = new LocatorInfo("css", "button[type='submit']");
        cache.put(key, healed, 0.95, "Found matching submit button");

        Optional<LocatorInfo> retrieved = cache.get(key);
        assertTrue(retrieved.isPresent());
        assertEquals(LocatorInfo.LocatorStrategy.CSS, retrieved.get().getStrategy());
        assertEquals("button[type='submit']", retrieved.get().getValue());
    }

    @Test
    @DisplayName("should return empty for cache miss")
    void cacheMiss() {
        CacheKey key = CacheKey.builder()
                .pageUrl("https://example.com/login")
                .originalLocator(new LocatorInfo("id", "nonexistent"))
                .actionType(ActionType.CLICK)
                .build();

        Optional<LocatorInfo> retrieved = cache.get(key);
        assertFalse(retrieved.isPresent());
    }

    @Test
    @DisplayName("should not cache low confidence heals")
    void lowConfidenceNotCached() {
        CacheKey key = CacheKey.builder()
                .pageUrl("https://example.com/login")
                .originalLocator(new LocatorInfo("id", "submit"))
                .actionType(ActionType.CLICK)
                .build();

        LocatorInfo healed = new LocatorInfo("css", "button");
        cache.put(key, healed, 0.5, "Low confidence match"); // Below threshold

        Optional<LocatorInfo> retrieved = cache.get(key);
        assertFalse(retrieved.isPresent());
    }

    @Test
    @DisplayName("should invalidate specific cache entries")
    void invalidateEntry() {
        CacheKey key = CacheKey.builder()
                .pageUrl("https://example.com/login")
                .originalLocator(new LocatorInfo("id", "submit"))
                .actionType(ActionType.CLICK)
                .build();

        cache.put(key, new LocatorInfo("css", "button"), 0.9, "Test");

        assertTrue(cache.get(key).isPresent());

        cache.invalidate(key);

        assertFalse(cache.get(key).isPresent());
    }

    @Test
    @DisplayName("should track cache statistics")
    void trackStatistics() {
        CacheKey key1 = CacheKey.builder()
                .pageUrl("https://example.com/page1")
                .originalLocator(new LocatorInfo("id", "elem1"))
                .actionType(ActionType.CLICK)
                .build();

        CacheKey key2 = CacheKey.builder()
                .pageUrl("https://example.com/page2")
                .originalLocator(new LocatorInfo("id", "elem2"))
                .actionType(ActionType.CLICK)
                .build();

        cache.put(key1, new LocatorInfo("css", "button"), 0.9, "Test");

        // Hit
        cache.get(key1);
        // Miss
        cache.get(key2);

        HealCache.CacheStats stats = cache.getStats();
        assertEquals(1, stats.size());
        assertEquals(1, stats.hits());
        assertEquals(1, stats.misses());
        assertEquals(0.5, stats.getHitRate(), 0.01);
    }

    @Test
    @DisplayName("should evict entries based on failure rate")
    void evictOnFailures() {
        CacheKey key = CacheKey.builder()
                .pageUrl("https://example.com/login")
                .originalLocator(new LocatorInfo("id", "submit"))
                .actionType(ActionType.CLICK)
                .build();

        cache.put(key, new LocatorInfo("css", "button"), 0.9, "Test");

        // Record multiple failures
        cache.recordFailure(key);
        cache.recordFailure(key);
        cache.recordFailure(key);

        // Entry should be evicted on next access due to high failure rate
        Optional<LocatorInfo> retrieved = cache.get(key);
        assertFalse(retrieved.isPresent());
    }

    @Test
    @DisplayName("should clear all entries")
    void clearCache() {
        CacheKey key1 = CacheKey.builder()
                .pageUrl("https://example.com/page1")
                .originalLocator(new LocatorInfo("id", "elem1"))
                .actionType(ActionType.CLICK)
                .build();

        CacheKey key2 = CacheKey.builder()
                .pageUrl("https://example.com/page2")
                .originalLocator(new LocatorInfo("id", "elem2"))
                .actionType(ActionType.CLICK)
                .build();

        cache.put(key1, new LocatorInfo("css", "button1"), 0.9, "Test1");
        cache.put(key2, new LocatorInfo("css", "button2"), 0.9, "Test2");

        assertEquals(2, cache.getStats().size());

        cache.clear();

        assertEquals(0, cache.getStats().size());
    }

    @Test
    @DisplayName("CacheKey should normalize URLs")
    void cacheKeyNormalizesUrls() {
        CacheKey key1 = CacheKey.builder()
                .pageUrl("https://example.com/users/123/profile")
                .originalLocator(new LocatorInfo("id", "submit"))
                .actionType(ActionType.CLICK)
                .build();

        CacheKey key2 = CacheKey.builder()
                .pageUrl("https://example.com/users/456/profile")
                .originalLocator(new LocatorInfo("id", "submit"))
                .actionType(ActionType.CLICK)
                .build();

        // Both should have the same pattern (numeric ID normalized)
        assertEquals(key1.getPageUrlPattern(), key2.getPageUrlPattern());
    }

    @Test
    @DisplayName("CacheKey should remove query parameters")
    void cacheKeyRemovesQueryParams() {
        String url1 = CacheKey.normalizeUrl("https://example.com/page?foo=bar&baz=123");
        String url2 = CacheKey.normalizeUrl("https://example.com/page");

        assertEquals(url1, url2);
    }
}
