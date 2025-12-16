package com.intenthealer.core.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Configuration for the auto-update feature that automatically updates
 * source code when heals are validated by passing tests.
 */
public class AutoUpdateConfig {

    /**
     * Default minimum confidence threshold for auto-updates (85%).
     */
    public static final double DEFAULT_MIN_CONFIDENCE = 0.85;

    /**
     * Default backup directory relative to project root.
     */
    public static final String DEFAULT_BACKUP_DIR = ".healer/backups";

    private final boolean enabled;
    private final double minConfidence;
    private final boolean requireTestPass;
    private final boolean backupEnabled;
    private final String backupDir;
    private final List<String> excludePatterns;
    private final boolean dryRun;

    @JsonCreator
    public AutoUpdateConfig(
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("minConfidence") Double minConfidence,
            @JsonProperty("requireTestPass") Boolean requireTestPass,
            @JsonProperty("backupEnabled") Boolean backupEnabled,
            @JsonProperty("backupDir") String backupDir,
            @JsonProperty("excludePatterns") List<String> excludePatterns,
            @JsonProperty("dryRun") Boolean dryRun) {
        this.enabled = enabled != null ? enabled : false;
        this.minConfidence = minConfidence != null ? minConfidence : DEFAULT_MIN_CONFIDENCE;
        this.requireTestPass = requireTestPass != null ? requireTestPass : true;
        this.backupEnabled = backupEnabled != null ? backupEnabled : true;
        this.backupDir = backupDir != null ? backupDir : DEFAULT_BACKUP_DIR;
        this.excludePatterns = excludePatterns != null ? List.copyOf(excludePatterns) : List.of();
        this.dryRun = dryRun != null ? dryRun : false;
    }

    /**
     * Returns true if auto-update is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the minimum confidence threshold for auto-updates.
     * Only heals with confidence >= this value will trigger updates.
     */
    public double getMinConfidence() {
        return minConfidence;
    }

    /**
     * Returns true if a passing test is required before auto-update.
     */
    public boolean isRequireTestPass() {
        return requireTestPass;
    }

    /**
     * Returns true if backup files should be created before updates.
     */
    public boolean isBackupEnabled() {
        return backupEnabled;
    }

    /**
     * Returns the directory where backup files are stored.
     */
    public String getBackupDir() {
        return backupDir;
    }

    /**
     * Returns patterns for files that should not be auto-updated.
     */
    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    /**
     * Returns true if dry-run mode is enabled (no actual file changes).
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Checks if a file path should be excluded from auto-update.
     */
    public boolean isExcluded(String filePath) {
        if (filePath == null || excludePatterns.isEmpty()) {
            return false;
        }

        String normalizedPath = filePath.replace('\\', '/');
        for (String pattern : excludePatterns) {
            if (matchesPattern(normalizedPath, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple glob pattern matching.
     */
    private boolean matchesPattern(String path, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("**/", ".*")
                .replace("*", "[^/]*")
                .replace("?", ".");
        return path.matches(regex);
    }

    /**
     * Returns a builder for creating AutoUpdateConfig instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a default disabled configuration.
     */
    public static AutoUpdateConfig disabled() {
        return new AutoUpdateConfig(false, null, null, null, null, null, null);
    }

    /**
     * Returns a default enabled configuration.
     */
    public static AutoUpdateConfig enabled() {
        return builder().enabled(true).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutoUpdateConfig that = (AutoUpdateConfig) o;
        return enabled == that.enabled
            && Double.compare(that.minConfidence, minConfidence) == 0
            && requireTestPass == that.requireTestPass
            && backupEnabled == that.backupEnabled
            && dryRun == that.dryRun
            && Objects.equals(backupDir, that.backupDir)
            && Objects.equals(excludePatterns, that.excludePatterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, minConfidence, requireTestPass, backupEnabled, backupDir, excludePatterns, dryRun);
    }

    @Override
    public String toString() {
        return "AutoUpdateConfig{" +
                "enabled=" + enabled +
                ", minConfidence=" + minConfidence +
                ", requireTestPass=" + requireTestPass +
                ", backupEnabled=" + backupEnabled +
                ", backupDir='" + backupDir + '\'' +
                ", excludePatterns=" + excludePatterns +
                ", dryRun=" + dryRun +
                '}';
    }

    public static final class Builder {
        private boolean enabled = false;
        private double minConfidence = DEFAULT_MIN_CONFIDENCE;
        private boolean requireTestPass = true;
        private boolean backupEnabled = true;
        private String backupDir = DEFAULT_BACKUP_DIR;
        private List<String> excludePatterns = List.of();
        private boolean dryRun = false;

        private Builder() {}

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder minConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
            return this;
        }

        public Builder requireTestPass(boolean requireTestPass) {
            this.requireTestPass = requireTestPass;
            return this;
        }

        public Builder backupEnabled(boolean backupEnabled) {
            this.backupEnabled = backupEnabled;
            return this;
        }

        public Builder backupDir(String backupDir) {
            this.backupDir = backupDir;
            return this;
        }

        public Builder excludePatterns(List<String> excludePatterns) {
            this.excludePatterns = excludePatterns != null ? List.copyOf(excludePatterns) : List.of();
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public AutoUpdateConfig build() {
            return new AutoUpdateConfig(enabled, minConfidence, requireTestPass,
                    backupEnabled, backupDir, excludePatterns, dryRun);
        }
    }
}
