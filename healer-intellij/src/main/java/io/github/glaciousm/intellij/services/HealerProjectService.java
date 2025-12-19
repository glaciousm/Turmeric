package io.github.glaciousm.intellij.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import io.github.glaciousm.intellij.settings.HealerSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Project-level service for Intent Healer.
 * Manages heal history, cache, and project-specific state.
 */
public class HealerProjectService implements Disposable {

    private final Project project;
    private final ObjectMapper objectMapper;
    private final List<HealHistoryEntry> healHistory;
    private final List<HealHistoryListener> listeners;
    private final List<BlacklistEntry> blacklistEntries;
    private final List<LocatorStabilityEntry> stabilityEntries;
    private volatile TrustLevelInfo currentTrustLevel;

    public HealerProjectService(@NotNull Project project) {
        this.project = project;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.healHistory = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.blacklistEntries = new CopyOnWriteArrayList<>();
        this.stabilityEntries = new CopyOnWriteArrayList<>();
        this.currentTrustLevel = new TrustLevelInfo("L0_SHADOW", 0, 0, 0.0);

        loadHistory();
        loadBlacklist();
        loadStability();
    }

    public static HealerProjectService getInstance(@NotNull Project project) {
        return project.getService(HealerProjectService.class);
    }

    /**
     * Get recent heal history.
     */
    public List<HealHistoryEntry> getHealHistory() {
        return Collections.unmodifiableList(healHistory);
    }

    /**
     * Get heal history with pagination.
     */
    public List<HealHistoryEntry> getHealHistory(int offset, int limit) {
        int start = Math.min(offset, healHistory.size());
        int end = Math.min(start + limit, healHistory.size());
        return healHistory.subList(start, end);
    }

    /**
     * Add a heal history entry.
     */
    public void addHealEntry(HealHistoryEntry entry) {
        healHistory.add(0, entry);

        HealerSettings settings = HealerSettings.getInstance();
        while (healHistory.size() > settings.maxHistoryEntries) {
            healHistory.remove(healHistory.size() - 1);
        }

        notifyListeners(entry);

        if (settings.persistHealHistory) {
            saveHistory();
        }

        if (settings.enableNotifications) {
            showNotification(entry);
        }
    }

    /**
     * Update current trust level.
     */
    public void updateTrustLevel(TrustLevelInfo trustLevel) {
        this.currentTrustLevel = trustLevel;
        for (HealHistoryListener listener : listeners) {
            listener.onTrustLevelChanged(trustLevel);
        }
    }

    /**
     * Get current trust level.
     */
    public TrustLevelInfo getCurrentTrustLevel() {
        return currentTrustLevel;
    }

    /**
     * Clear heal history.
     */
    public void clearHistory() {
        healHistory.clear();
        saveHistory();
    }

    /**
     * Accept a heal (mark as confirmed correct).
     */
    public void acceptHeal(String healId) {
        for (HealHistoryEntry entry : healHistory) {
            if (entry.id().equals(healId)) {
                HealHistoryEntry updated = new HealHistoryEntry(
                        entry.id(),
                        entry.timestamp(),
                        entry.featureName(),
                        entry.scenarioName(),
                        entry.stepText(),
                        entry.originalLocator(),
                        entry.healedLocator(),
                        entry.confidence(),
                        entry.reasoning(),
                        HealStatus.ACCEPTED
                );
                healHistory.set(healHistory.indexOf(entry), updated);
                saveHistory();
                break;
            }
        }
    }

    /**
     * Reject a heal (mark as incorrect).
     */
    public void rejectHeal(String healId) {
        for (HealHistoryEntry entry : healHistory) {
            if (entry.id().equals(healId)) {
                HealHistoryEntry updated = new HealHistoryEntry(
                        entry.id(),
                        entry.timestamp(),
                        entry.featureName(),
                        entry.scenarioName(),
                        entry.stepText(),
                        entry.originalLocator(),
                        entry.healedLocator(),
                        entry.confidence(),
                        entry.reasoning(),
                        HealStatus.REJECTED
                );
                healHistory.set(healHistory.indexOf(entry), updated);
                saveHistory();
                break;
            }
        }
    }

    /**
     * Blacklist a heal (mark as permanently blocked).
     * Blacklisted heals will not be suggested again for the same locator.
     *
     * @param healId the heal entry ID
     * @param reason the reason for blacklisting
     */
    public void blacklistHeal(String healId, String reason) {
        for (HealHistoryEntry entry : healHistory) {
            if (entry.id().equals(healId)) {
                String updatedReasoning = entry.reasoning() +
                    "\n[BLACKLISTED: " + reason + "]";
                HealHistoryEntry updated = new HealHistoryEntry(
                        entry.id(),
                        entry.timestamp(),
                        entry.featureName(),
                        entry.scenarioName(),
                        entry.stepText(),
                        entry.originalLocator(),
                        entry.healedLocator(),
                        entry.confidence(),
                        updatedReasoning,
                        HealStatus.BLACKLISTED
                );
                healHistory.set(healHistory.indexOf(entry), updated);
                saveHistory();
                saveToBlacklistFile(entry.originalLocator(), entry.healedLocator(), reason);
                break;
            }
        }
    }

    /**
     * Check if a locator pair is blacklisted.
     */
    public boolean isBlacklisted(String originalLocator, String healedLocator) {
        return blacklistEntries.stream()
                .anyMatch(e -> e.originalLocator().equals(originalLocator)
                        && e.healedLocator().equals(healedLocator));
    }

    /**
     * Get all blacklist entries.
     */
    public List<BlacklistEntry> getBlacklistEntries() {
        return Collections.unmodifiableList(blacklistEntries);
    }

    private void saveToBlacklistFile(String originalLocator, String healedLocator, String reason) {
        BlacklistEntry entry = new BlacklistEntry(
                UUID.randomUUID().toString(),
                originalLocator,
                healedLocator,
                reason,
                Instant.now()
        );
        blacklistEntries.add(entry);

        Path blacklistPath = getBlacklistPath();
        try {
            Files.createDirectories(blacklistPath.getParent());
            objectMapper.writeValue(blacklistPath.toFile(), blacklistEntries);
        } catch (IOException e) {
            // Log but don't fail
        }
    }

    private void loadBlacklist() {
        Path blacklistPath = getBlacklistPath();
        if (!Files.exists(blacklistPath)) {
            return;
        }

        try {
            BlacklistEntry[] entries = objectMapper.readValue(
                    blacklistPath.toFile(), BlacklistEntry[].class);
            blacklistEntries.clear();
            blacklistEntries.addAll(Arrays.asList(entries));
        } catch (IOException e) {
            // Log but don't fail
        }
    }

    private Path getBlacklistPath() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            basePath = System.getProperty("user.home");
        }
        HealerSettings settings = HealerSettings.getInstance();
        return Path.of(basePath, settings.healCacheDirectory, "blacklist.json");
    }

    // ========== Locator Stability Management ==========

    /**
     * Get all locator stability entries.
     */
    public List<LocatorStabilityEntry> getLocatorStabilities() {
        return Collections.unmodifiableList(stabilityEntries);
    }

    /**
     * Get stability summary counts.
     */
    public StabilitySummary getStabilitySummary() {
        int total = stabilityEntries.size();
        int stable = 0;
        int moderate = 0;
        int unstable = 0;

        for (LocatorStabilityEntry entry : stabilityEntries) {
            switch (entry.level()) {
                case "VERY_STABLE", "STABLE" -> stable++;
                case "MODERATE" -> moderate++;
                case "UNSTABLE", "VERY_UNSTABLE" -> unstable++;
            }
        }

        return new StabilitySummary(total, stable, moderate, unstable);
    }

    /**
     * Update or add a locator stability entry.
     */
    public void updateLocatorStability(LocatorStabilityEntry entry) {
        stabilityEntries.removeIf(e -> e.locator().equals(entry.locator()));
        stabilityEntries.add(entry);
        saveStability();
    }

    private void loadStability() {
        Path stabilityPath = getStabilityPath();
        if (!Files.exists(stabilityPath)) {
            return;
        }

        try {
            LocatorStabilityEntry[] entries = objectMapper.readValue(
                    stabilityPath.toFile(), LocatorStabilityEntry[].class);
            stabilityEntries.clear();
            stabilityEntries.addAll(Arrays.asList(entries));
        } catch (IOException e) {
            // Log but don't fail
        }
    }

    private void saveStability() {
        Path stabilityPath = getStabilityPath();
        try {
            Files.createDirectories(stabilityPath.getParent());
            objectMapper.writeValue(stabilityPath.toFile(), stabilityEntries);
        } catch (IOException e) {
            // Log but don't fail
        }
    }

    private Path getStabilityPath() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            basePath = System.getProperty("user.home");
        }
        HealerSettings settings = HealerSettings.getInstance();
        return Path.of(basePath, settings.healCacheDirectory, "locator-stability.json");
    }

    /**
     * Add a listener for heal history changes.
     */
    public void addListener(HealHistoryListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(HealHistoryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Refresh from disk.
     */
    public void refresh() {
        loadHistory();
        for (HealHistoryListener listener : listeners) {
            listener.onHistoryRefreshed();
        }
    }

    private void loadHistory() {
        Path historyPath = getHistoryPath();
        if (!Files.exists(historyPath)) {
            return;
        }

        try {
            HealHistoryEntry[] entries = objectMapper.readValue(
                    historyPath.toFile(), HealHistoryEntry[].class);
            healHistory.clear();
            healHistory.addAll(Arrays.asList(entries));
        } catch (IOException e) {
            // Log but don't fail
        }
    }

    private void saveHistory() {
        Path historyPath = getHistoryPath();
        try {
            Files.createDirectories(historyPath.getParent());
            objectMapper.writeValue(historyPath.toFile(), healHistory);
        } catch (IOException e) {
            // Log but don't fail
        }
    }

    private Path getHistoryPath() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            basePath = System.getProperty("user.home");
        }
        HealerSettings settings = HealerSettings.getInstance();
        return Path.of(basePath, settings.healCacheDirectory, "heal-history.json");
    }

    private void notifyListeners(HealHistoryEntry entry) {
        for (HealHistoryListener listener : listeners) {
            listener.onHealAdded(entry);
        }
    }

    private void showNotification(HealHistoryEntry entry) {
        String title = "Heal Applied";
        String content = String.format(
                "%s: %s -> %s (%.0f%% confidence)",
                entry.stepText(),
                entry.originalLocator(),
                entry.healedLocator(),
                entry.confidence() * 100
        );

        NotificationGroupManager.getInstance()
                .getNotificationGroup("Intent Healer Notifications")
                .createNotification(title, content, NotificationType.INFORMATION)
                .notify(project);
    }

    @Override
    public void dispose() {
        listeners.clear();
    }

    /**
     * Heal history entry.
     */
    public record HealHistoryEntry(
            String id,
            Instant timestamp,
            String featureName,
            String scenarioName,
            String stepText,
            String originalLocator,
            String healedLocator,
            double confidence,
            String reasoning,
            HealStatus status
    ) {}

    /**
     * Heal status.
     */
    public enum HealStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        BLACKLISTED
    }

    /**
     * Trust level info.
     */
    public record TrustLevelInfo(
            String level,
            int consecutiveSuccesses,
            int failuresInWindow,
            double successRate
    ) {}

    /**
     * Listener for heal history changes.
     */
    public interface HealHistoryListener {
        default void onHealAdded(HealHistoryEntry entry) {}
        default void onTrustLevelChanged(TrustLevelInfo trustLevel) {}
        default void onHistoryRefreshed() {}
    }

    /**
     * Blacklist entry for permanently blocking specific locator healings.
     */
    public record BlacklistEntry(
            String id,
            String originalLocator,
            String healedLocator,
            String reason,
            Instant timestamp
    ) {}

    /**
     * Locator stability tracking entry.
     */
    public record LocatorStabilityEntry(
            String locator,
            double score,
            String level,
            int successes,
            int failures,
            int heals
    ) {}

    /**
     * Summary of stability counts by level.
     */
    public record StabilitySummary(
            int total,
            int stable,
            int moderate,
            int unstable
    ) {}
}
