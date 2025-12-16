package com.intenthealer.core.engine.blacklist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.intenthealer.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Blacklist for preventing certain heals from being applied.
 *
 * Use cases:
 * - Blocking heals that are known to be incorrect
 * - Preventing heal attempts for specific locators
 * - Temporarily blocking problematic heals during debugging
 */
public class HealBlacklist {

    private static final Logger logger = LoggerFactory.getLogger(HealBlacklist.class);
    private static final String BLACKLIST_FILE_NAME = "heal-blacklist.json";
    private static final long DEFAULT_CLEANUP_INTERVAL_MINUTES = 15;

    private final Map<String, BlacklistEntry> entries;
    private final ObjectMapper objectMapper;
    private final Path persistencePath;
    private final boolean persistenceEnabled;
    private final ScheduledExecutorService cleanupScheduler;
    private final AtomicInteger totalExpired = new AtomicInteger(0);
    private final AtomicInteger totalBlocked = new AtomicInteger(0);
    private final long defaultTtlSeconds;
    private volatile Instant lastCleanupTime;

    public HealBlacklist(String persistenceDir) {
        this(persistenceDir, 0, false);
    }

    public HealBlacklist(String persistenceDir, long defaultTtlSeconds, boolean enableScheduledCleanup) {
        this.entries = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.lastCleanupTime = Instant.now();

        if (persistenceDir != null && !persistenceDir.isEmpty()) {
            this.persistencePath = Path.of(persistenceDir, BLACKLIST_FILE_NAME);
            this.persistenceEnabled = true;
            loadFromDisk();
        } else {
            this.persistencePath = null;
            this.persistenceEnabled = false;
        }

        if (enableScheduledCleanup) {
            this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "blacklist-cleanup");
                t.setDaemon(true);
                return t;
            });
            this.cleanupScheduler.scheduleAtFixedRate(
                    this::scheduledCleanup,
                    DEFAULT_CLEANUP_INTERVAL_MINUTES,
                    DEFAULT_CLEANUP_INTERVAL_MINUTES,
                    TimeUnit.MINUTES
            );
            logger.info("Scheduled blacklist cleanup enabled (every {} minutes)", DEFAULT_CLEANUP_INTERVAL_MINUTES);
        } else {
            this.cleanupScheduler = null;
        }
    }

    public HealBlacklist() {
        this(null, 0, false);
    }

    /**
     * Create blacklist with scheduled cleanup.
     */
    public static HealBlacklist withScheduledCleanup(String persistenceDir, long defaultTtlSeconds) {
        return new HealBlacklist(persistenceDir, defaultTtlSeconds, true);
    }

    /**
     * Check if a heal is blacklisted.
     */
    public boolean isBlacklisted(String pageUrl, LocatorInfo original, LocatorInfo healed) {
        cleanup(); // Remove expired entries

        String origStrategy = original != null ? original.getStrategy().name() : null;
        String origValue = original != null ? original.getValue() : null;
        String healedStrategy = healed != null ? healed.getStrategy().name() : null;
        String healedValue = healed != null ? healed.getValue() : null;

        for (BlacklistEntry entry : entries.values()) {
            if (entry.matches(pageUrl, origStrategy, origValue, healedStrategy, healedValue)) {
                totalBlocked.incrementAndGet();
                logger.info("Heal blocked by blacklist: {} -> {} (reason: {})",
                        original, healed, entry.getReason());
                return true;
            }
        }

        return false;
    }

    /**
     * Add an entry to the blacklist.
     */
    public BlacklistEntry add(BlacklistEntry entry) {
        entries.put(entry.getId(), entry);
        logger.info("Added blacklist entry: {}", entry);

        if (persistenceEnabled) {
            persistToDisk();
        }

        return entry;
    }

    /**
     * Add a simple blacklist entry for a locator.
     */
    public BlacklistEntry addLocator(LocatorInfo original, String reason) {
        return addLocator(original, null, reason);
    }

    /**
     * Add a blacklist entry for a specific heal (original -> healed).
     */
    public BlacklistEntry addLocator(LocatorInfo original, LocatorInfo healed, String reason) {
        BlacklistEntry.Builder builder = BlacklistEntry.builder()
                .originalLocator(original.getStrategy().name(), original.getValue())
                .reason(reason);

        if (healed != null) {
            builder.healedLocator(healed.getStrategy().name(), healed.getValue());
        }

        return add(builder.build());
    }

    /**
     * Add a blacklist entry with TTL.
     */
    public BlacklistEntry addTemporary(LocatorInfo original, String reason, long ttlSeconds) {
        BlacklistEntry entry = BlacklistEntry.builder()
                .originalLocator(original.getStrategy().name(), original.getValue())
                .reason(reason)
                .ttlSeconds(ttlSeconds)
                .build();

        return add(entry);
    }

    /**
     * Remove an entry from the blacklist.
     */
    public boolean remove(String id) {
        BlacklistEntry removed = entries.remove(id);
        if (removed != null) {
            logger.info("Removed blacklist entry: {}", removed);
            if (persistenceEnabled) {
                persistToDisk();
            }
            return true;
        }
        return false;
    }

    /**
     * Remove all entries for a specific original locator.
     */
    public int removeByOriginalLocator(LocatorInfo original) {
        int removed = 0;
        Iterator<Map.Entry<String, BlacklistEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            BlacklistEntry entry = it.next().getValue();
            if (Objects.equals(entry.getOriginalLocatorStrategy(), original.getStrategy().name()) &&
                Objects.equals(entry.getOriginalLocatorValue(), original.getValue())) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            logger.info("Removed {} blacklist entries for locator: {}", removed, original);
            if (persistenceEnabled) {
                persistToDisk();
            }
        }

        return removed;
    }

    /**
     * Get an entry by ID.
     */
    public Optional<BlacklistEntry> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    /**
     * Get all entries.
     */
    public List<BlacklistEntry> getAll() {
        cleanup();
        return new ArrayList<>(entries.values());
    }

    /**
     * Get entries matching a pattern.
     */
    public List<BlacklistEntry> findByPagePattern(String pageUrlPattern) {
        return entries.values().stream()
                .filter(e -> Objects.equals(e.getPageUrlPattern(), pageUrlPattern))
                .collect(Collectors.toList());
    }

    /**
     * Get the number of entries.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Clear all entries.
     */
    public void clear() {
        entries.clear();
        logger.info("Blacklist cleared");
        if (persistenceEnabled) {
            persistToDisk();
        }
    }

    /**
     * Remove expired entries.
     */
    private int cleanup() {
        int removed = 0;
        Iterator<Map.Entry<String, BlacklistEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            totalExpired.addAndGet(removed);
            logger.debug("Removed {} expired blacklist entries", removed);
            if (persistenceEnabled) {
                persistToDisk();
            }
        }
        lastCleanupTime = Instant.now();
        return removed;
    }

    /**
     * Scheduled cleanup task.
     */
    private void scheduledCleanup() {
        try {
            int removed = cleanup();
            if (removed > 0) {
                logger.info("Scheduled cleanup removed {} expired entries, {} remaining", removed, entries.size());
            }
        } catch (Exception e) {
            logger.warn("Scheduled cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * Force cleanup and return statistics.
     */
    public CleanupResult forceCleanup() {
        int before = entries.size();
        int removed = cleanup();
        return new CleanupResult(removed, before - removed, lastCleanupTime);
    }

    /**
     * Get blacklist statistics.
     */
    public BlacklistStats getStats() {
        int active = 0;
        int expiringSoon = 0;
        int permanent = 0;
        Instant threshold = Instant.now().plus(Duration.ofHours(1));

        for (BlacklistEntry entry : entries.values()) {
            if (entry.isExpired()) continue;
            active++;
            if (entry.getExpiresAt() == null) {
                permanent++;
            } else if (entry.getExpiresAt().isBefore(threshold)) {
                expiringSoon++;
            }
        }

        return new BlacklistStats(
                active,
                permanent,
                expiringSoon,
                totalExpired.get(),
                totalBlocked.get(),
                lastCleanupTime
        );
    }

    /**
     * Extend TTL for an entry.
     */
    public boolean extendTtl(String id, long additionalSeconds) {
        BlacklistEntry entry = entries.get(id);
        if (entry == null) {
            return false;
        }

        Instant newExpiry;
        if (entry.getExpiresAt() == null) {
            newExpiry = Instant.now().plusSeconds(additionalSeconds);
        } else {
            newExpiry = entry.getExpiresAt().plusSeconds(additionalSeconds);
        }

        BlacklistEntry extended = BlacklistEntry.builder()
                .id(entry.getId())
                .pageUrlPattern(entry.getPageUrlPattern())
                .originalLocator(entry.getOriginalLocatorStrategy(), entry.getOriginalLocatorValue())
                .healedLocator(entry.getHealedLocatorStrategy(), entry.getHealedLocatorValue())
                .reason(entry.getReason())
                .expiresAt(newExpiry)
                .addedBy(entry.getAddedBy())
                .build();

        entries.put(id, extended);
        logger.info("Extended TTL for blacklist entry {}: new expiry {}", id, newExpiry);

        if (persistenceEnabled) {
            persistToDisk();
        }
        return true;
    }

    /**
     * Remove all expired entries and optionally entries older than a duration.
     */
    public int removeOlderThan(Duration age) {
        Instant threshold = Instant.now().minus(age);
        int removed = 0;
        Iterator<Map.Entry<String, BlacklistEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            BlacklistEntry entry = it.next().getValue();
            if (entry.isExpired() || entry.getCreatedAt().isBefore(threshold)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            totalExpired.addAndGet(removed);
            logger.info("Removed {} blacklist entries older than {}", removed, age);
            if (persistenceEnabled) {
                persistToDisk();
            }
        }
        return removed;
    }

    /**
     * Shutdown the scheduled cleanup executor.
     */
    public void shutdown() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("Blacklist cleanup scheduler shut down");
        }
    }

    /**
     * Cleanup result.
     */
    public record CleanupResult(int removedCount, int remainingCount, Instant cleanupTime) {}

    /**
     * Blacklist statistics.
     */
    public record BlacklistStats(
            int activeEntries,
            int permanentEntries,
            int expiringSoonEntries,
            int totalExpiredEntries,
            int totalBlockedHeals,
            Instant lastCleanupTime
    ) {}

    /**
     * Persist to disk.
     */
    private void persistToDisk() {
        if (persistencePath == null) {
            return;
        }

        try {
            Files.createDirectories(persistencePath.getParent());
            List<BlacklistEntryDto> dtos = entries.values().stream()
                    .filter(e -> !e.isExpired())
                    .map(this::toDto)
                    .toList();
            objectMapper.writeValue(persistencePath.toFile(), dtos);
            logger.debug("Persisted {} blacklist entries to disk", dtos.size());
        } catch (IOException e) {
            logger.warn("Failed to persist blacklist: {}", e.getMessage());
        }
    }

    /**
     * Load from disk.
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
            BlacklistEntryDto[] dtos = objectMapper.readValue(file, BlacklistEntryDto[].class);
            int loaded = 0;
            for (BlacklistEntryDto dto : dtos) {
                BlacklistEntry entry = fromDto(dto);
                if (!entry.isExpired()) {
                    entries.put(entry.getId(), entry);
                    loaded++;
                }
            }
            logger.info("Loaded {} blacklist entries from disk", loaded);
        } catch (IOException e) {
            logger.warn("Failed to load blacklist: {}", e.getMessage());
        }
    }

    private BlacklistEntryDto toDto(BlacklistEntry entry) {
        BlacklistEntryDto dto = new BlacklistEntryDto();
        dto.id = entry.getId();
        dto.pageUrlPattern = entry.getPageUrlPattern();
        dto.originalLocatorStrategy = entry.getOriginalLocatorStrategy();
        dto.originalLocatorValue = entry.getOriginalLocatorValue();
        dto.healedLocatorStrategy = entry.getHealedLocatorStrategy();
        dto.healedLocatorValue = entry.getHealedLocatorValue();
        dto.reason = entry.getReason();
        dto.createdAt = entry.getCreatedAt().toEpochMilli();
        dto.expiresAt = entry.getExpiresAt() != null ? entry.getExpiresAt().toEpochMilli() : null;
        dto.addedBy = entry.getAddedBy();
        return dto;
    }

    private BlacklistEntry fromDto(BlacklistEntryDto dto) {
        BlacklistEntry.Builder builder = BlacklistEntry.builder()
                .id(dto.id)
                .pageUrlPattern(dto.pageUrlPattern)
                .originalLocator(dto.originalLocatorStrategy, dto.originalLocatorValue)
                .reason(dto.reason)
                .addedBy(dto.addedBy);

        if (dto.healedLocatorStrategy != null && dto.healedLocatorValue != null) {
            builder.healedLocator(dto.healedLocatorStrategy, dto.healedLocatorValue);
        }

        if (dto.expiresAt != null) {
            long remainingSeconds = (dto.expiresAt - Instant.now().toEpochMilli()) / 1000;
            if (remainingSeconds > 0) {
                builder.ttlSeconds(remainingSeconds);
            }
        }

        return builder.build();
    }

    /**
     * DTO for serialization.
     */
    private static class BlacklistEntryDto {
        public String id;
        public String pageUrlPattern;
        public String originalLocatorStrategy;
        public String originalLocatorValue;
        public String healedLocatorStrategy;
        public String healedLocatorValue;
        public String reason;
        public long createdAt;
        public Long expiresAt;
        public String addedBy;
    }
}
