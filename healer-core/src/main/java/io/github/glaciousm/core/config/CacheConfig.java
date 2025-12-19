package io.github.glaciousm.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for heal caching.
 */
public class CacheConfig {

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("ttl_hours")
    private int ttlHours = 24;

    @JsonProperty("ttl_seconds")
    private long ttlSeconds = 86400; // 24 hours default

    @JsonProperty("max_entries")
    private int maxEntries = 10000;

    @JsonProperty("max_size")
    private int maxSize = 10000;

    @JsonProperty("min_confidence_to_cache")
    private double minConfidenceToCache = 0.7;

    @JsonProperty("storage")
    private StorageType storage = StorageType.MEMORY;

    @JsonProperty("file_path")
    private String filePath = ".healer/cache";

    @JsonProperty("persistence_enabled")
    private boolean persistenceEnabled = false;

    @JsonProperty("persistence_dir")
    private String persistenceDir = ".healer/cache";

    @JsonProperty("redis_url")
    private String redisUrl;

    public CacheConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(int ttlHours) {
        this.ttlHours = ttlHours;
        this.ttlSeconds = ttlHours * 3600L;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
        this.maxSize = maxEntries;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
        this.maxEntries = maxSize;
    }

    public double getMinConfidenceToCache() {
        return minConfidenceToCache;
    }

    public void setMinConfidenceToCache(double minConfidenceToCache) {
        this.minConfidenceToCache = minConfidenceToCache;
    }

    public StorageType getStorage() {
        return storage;
    }

    public void setStorage(StorageType storage) {
        this.storage = storage;
        this.persistenceEnabled = (storage == StorageType.FILE || storage == StorageType.REDIS);
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
        this.persistenceDir = filePath;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public void setPersistenceEnabled(boolean persistenceEnabled) {
        this.persistenceEnabled = persistenceEnabled;
    }

    public String getPersistenceDir() {
        return persistenceDir;
    }

    public void setPersistenceDir(String persistenceDir) {
        this.persistenceDir = persistenceDir;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public void setRedisUrl(String redisUrl) {
        this.redisUrl = redisUrl;
    }

    @Override
    public String toString() {
        return "CacheConfig{enabled=" + enabled + ", ttlHours=" + ttlHours +
               ", maxSize=" + maxSize + ", storage=" + storage + "}";
    }

    /**
     * Cache storage types.
     */
    public enum StorageType {
        MEMORY,
        FILE,
        REDIS
    }
}
