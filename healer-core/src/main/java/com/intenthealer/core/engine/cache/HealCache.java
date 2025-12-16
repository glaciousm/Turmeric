package com.intenthealer.core.engine.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.intenthealer.core.config.CacheConfig;
import com.intenthealer.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache for storing successful heals to reduce LLM calls and latency.
 * Supports in-memory caching with optional file-based persistence.
 */
public class HealCache {

    private static final Logger logger = LoggerFactory.getLogger(HealCache.class);
    private static final String CACHE_FILE_NAME = "heal-cache.json";

    private final CacheConfig config;
    private final Map<String, CacheEntry> cache;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService cleanupExecutor;
    private final Path persistencePath;

    // Statistics
    private long totalHits = 0;
    private long totalMisses = 0;
    private long totalEvictions = 0;

    public HealCache(CacheConfig config) {
        this.config = config != null ? config : new CacheConfig();
        this.cache = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Set up persistence path
        if (this.config.isPersistenceEnabled()) {
            this.persistencePath = Path.of(this.config.getPersistenceDir(), CACHE_FILE_NAME);
            loadFromDisk();
        } else {
            this.persistencePath = null;
        }

        // Start cleanup task
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heal-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanup,
                1, 1, TimeUnit.HOURS
        );
    }

    public HealCache() {
        this(new CacheConfig());
    }

    /**
     * Get a cached heal for the given key.
     */
    public Optional<LocatorInfo> get(CacheKey key) {
        if (!config.isEnabled()) {
            return Optional.empty();
        }

        CacheEntry entry = cache.get(key.getHash());
        if (entry == null) {
            totalMisses++;
            logger.debug("Cache miss for key: {}", key.getHash());
            return Optional.empty();
        }

        if (entry.isExpired()) {
            cache.remove(key.getHash());
            totalMisses++;
            totalEvictions++;
            logger.debug("Cache entry expired for key: {}", key.getHash());
            return Optional.empty();
        }

        if (entry.shouldEvict()) {
            cache.remove(key.getHash());
            totalMisses++;
            totalEvictions++;
            logger.debug("Cache entry evicted due to failures for key: {}", key.getHash());
            return Optional.empty();
        }

        totalHits++;
        logger.debug("Cache hit for key: {} (hits: {})", key.getHash(), entry.getHitCount() + 1);
        return Optional.of(entry.recordHit());
    }

    /**
     * Store a successful heal in the cache.
     */
    public void put(CacheKey key, LocatorInfo healedLocator, double confidence, String reasoning) {
        if (!config.isEnabled()) {
            return;
        }

        // Don't cache low-confidence heals
        if (confidence < config.getMinConfidenceToCache()) {
            logger.debug("Not caching heal with confidence {} (min: {})",
                    confidence, config.getMinConfidenceToCache());
            return;
        }

        // Check size limit
        if (cache.size() >= config.getMaxSize()) {
            evictOldest();
        }

        CacheEntry entry = CacheEntry.builder()
                .key(key)
                .healedLocator(healedLocator)
                .confidence(confidence)
                .reasoning(reasoning)
                .ttlSeconds(config.getTtlSeconds())
                .build();

        cache.put(key.getHash(), entry);
        logger.debug("Cached heal for key: {} with confidence {}", key.getHash(), confidence);

        // Persist if enabled
        if (config.isPersistenceEnabled()) {
            persistToDisk();
        }
    }

    /**
     * Record that a cached heal was successful.
     */
    public void recordSuccess(CacheKey key) {
        CacheEntry entry = cache.get(key.getHash());
        if (entry != null) {
            entry.recordSuccess();
            logger.debug("Recorded success for cache key: {}", key.getHash());
        }
    }

    /**
     * Record that a cached heal failed.
     */
    public void recordFailure(CacheKey key) {
        CacheEntry entry = cache.get(key.getHash());
        if (entry != null) {
            entry.recordFailure();
            logger.debug("Recorded failure for cache key: {} (failures: {})",
                    key.getHash(), entry.getFailureCount());
        }
    }

    /**
     * Invalidate a specific cache entry.
     */
    public void invalidate(CacheKey key) {
        cache.remove(key.getHash());
        logger.debug("Invalidated cache key: {}", key.getHash());
    }

    /**
     * Invalidate all cache entries for a given page URL pattern.
     */
    public void invalidateByPagePattern(String pageUrlPattern) {
        String pattern = CacheKey.extractPagePattern(pageUrlPattern);
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            CacheEntry entry = it.next().getValue();
            if (pattern.equals(entry.getKey().getPageUrlPattern())) {
                it.remove();
                removed++;
            }
        }
        logger.info("Invalidated {} cache entries for page pattern: {}", removed, pattern);
    }

    /**
     * Clear the entire cache.
     */
    public void clear() {
        cache.clear();
        logger.info("Cache cleared");
        if (config.isPersistenceEnabled()) {
            persistToDisk();
        }
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        return new CacheStats(
                cache.size(),
                totalHits,
                totalMisses,
                totalEvictions,
                config.getMaxSize()
        );
    }

    /**
     * Clean up expired entries.
     */
    private void cleanup() {
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            CacheEntry entry = it.next().getValue();
            if (entry.isExpired() || entry.shouldEvict()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            totalEvictions += removed;
            logger.info("Cache cleanup removed {} entries", removed);
            if (config.isPersistenceEnabled()) {
                persistToDisk();
            }
        }
    }

    /**
     * Evict the oldest entry when cache is full.
     */
    private void evictOldest() {
        cache.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().getLastAccessedAt()))
                .ifPresent(oldest -> {
                    cache.remove(oldest.getKey());
                    totalEvictions++;
                    logger.debug("Evicted oldest cache entry: {}", oldest.getKey());
                });
    }

    /**
     * Persist cache to disk.
     */
    private void persistToDisk() {
        if (persistencePath == null) {
            return;
        }

        try {
            Files.createDirectories(persistencePath.getParent());

            // Convert to serializable format
            List<CacheEntryDto> entries = cache.values().stream()
                    .filter(e -> !e.isExpired())
                    .map(this::toDto)
                    .toList();

            objectMapper.writeValue(persistencePath.toFile(), entries);
            logger.debug("Persisted {} cache entries to disk", entries.size());
        } catch (IOException e) {
            logger.warn("Failed to persist cache to disk: {}", e.getMessage());
        }
    }

    /**
     * Load cache from disk.
     */
    private void loadFromDisk() {
        if (persistencePath == null) {
            return;
        }

        File file = persistencePath.toFile();
        if (!file.exists()) {
            return;
        }

        try {
            CacheEntryDto[] entries = objectMapper.readValue(file, CacheEntryDto[].class);
            int loaded = 0;
            for (CacheEntryDto dto : entries) {
                if (!isExpired(dto)) {
                    CacheEntry entry = fromDto(dto);
                    cache.put(entry.getKey().getHash(), entry);
                    loaded++;
                }
            }
            logger.info("Loaded {} cache entries from disk", loaded);
        } catch (IOException e) {
            logger.warn("Failed to load cache from disk: {}", e.getMessage());
        }
    }

    private CacheEntryDto toDto(CacheEntry entry) {
        CacheEntryDto dto = new CacheEntryDto();
        dto.keyHash = entry.getKey().getHash();
        dto.pageUrlPattern = entry.getKey().getPageUrlPattern();
        dto.originalLocatorStrategy = entry.getKey().getOriginalLocator() != null
                ? entry.getKey().getOriginalLocator().getStrategy().name() : null;
        dto.originalLocatorValue = entry.getKey().getOriginalLocator() != null
                ? entry.getKey().getOriginalLocator().getValue() : null;
        dto.actionType = entry.getKey().getActionType() != null
                ? entry.getKey().getActionType().name() : null;
        dto.intentHint = entry.getKey().getIntentHint();
        dto.healedLocatorStrategy = entry.getHealedLocator().getStrategy().name();
        dto.healedLocatorValue = entry.getHealedLocator().getValue();
        dto.confidence = entry.getConfidence();
        dto.reasoning = entry.getReasoning();
        dto.createdAt = entry.getCreatedAt().toEpochMilli();
        dto.expiresAt = entry.getExpiresAt().toEpochMilli();
        dto.hitCount = entry.getHitCount();
        dto.successCount = entry.getSuccessCount();
        dto.failureCount = entry.getFailureCount();
        return dto;
    }

    private CacheEntry fromDto(CacheEntryDto dto) {
        LocatorInfo originalLocator = null;
        if (dto.originalLocatorStrategy != null && dto.originalLocatorValue != null) {
            originalLocator = new LocatorInfo(
                    LocatorInfo.LocatorStrategy.valueOf(dto.originalLocatorStrategy),
                    dto.originalLocatorValue);
        }

        CacheKey key = CacheKey.builder()
                .pageUrl(dto.pageUrlPattern) // Already a pattern
                .originalLocator(originalLocator)
                .actionType(dto.actionType != null
                        ? com.intenthealer.core.model.ActionType.valueOf(dto.actionType) : null)
                .intentHint(dto.intentHint)
                .build();

        LocatorInfo healedLocator = new LocatorInfo(
                LocatorInfo.LocatorStrategy.valueOf(dto.healedLocatorStrategy),
                dto.healedLocatorValue);

        return CacheEntry.builder()
                .key(key)
                .healedLocator(healedLocator)
                .confidence(dto.confidence)
                .reasoning(dto.reasoning)
                .ttlSeconds((dto.expiresAt - dto.createdAt) / 1000)
                .build();
    }

    private boolean isExpired(CacheEntryDto dto) {
        return Instant.now().toEpochMilli() > dto.expiresAt;
    }

    /**
     * Shutdown the cache cleanup executor.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        if (config.isPersistenceEnabled()) {
            persistToDisk();
        }
    }

    /**
     * DTO for cache serialization.
     */
    private static class CacheEntryDto {
        public String keyHash;
        public String pageUrlPattern;
        public String originalLocatorStrategy;
        public String originalLocatorValue;
        public String actionType;
        public String intentHint;
        public String healedLocatorStrategy;
        public String healedLocatorValue;
        public double confidence;
        public String reasoning;
        public long createdAt;
        public long expiresAt;
        public int hitCount;
        public int successCount;
        public int failureCount;
    }

    /**
     * Cache statistics.
     */
    public record CacheStats(
            int size,
            long hits,
            long misses,
            long evictions,
            int maxSize
    ) {
        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
