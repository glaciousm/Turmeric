package io.github.glaciousm.core.engine.patch;

import io.github.glaciousm.core.config.AutoUpdateConfig;
import io.github.glaciousm.core.model.ValidatedHeal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for automatically updating source code with validated heals.
 * Creates backups before modifications and supports rollback.
 */
public class SourceCodeUpdater {

    private static final Logger logger = LoggerFactory.getLogger(SourceCodeUpdater.class);

    private final AutoUpdateConfig config;
    private final LocatorPatternMatcher patternMatcher;
    private final Path projectRoot;

    /**
     * Result of an update operation.
     */
    public static class UpdateResult {
        private final boolean success;
        private final String filePath;
        private final int lineNumber;
        private final String oldValue;
        private final String newValue;
        private final String backupPath;
        private final String errorMessage;
        private final Instant updatedAt;

        private UpdateResult(boolean success, String filePath, int lineNumber,
                            String oldValue, String newValue, String backupPath,
                            String errorMessage) {
            this.success = success;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.backupPath = backupPath;
            this.errorMessage = errorMessage;
            this.updatedAt = Instant.now();
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public String getBackupPath() {
            return backupPath;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }

        public static UpdateResult success(String filePath, int lineNumber,
                                           String oldValue, String newValue, String backupPath) {
            return new UpdateResult(true, filePath, lineNumber, oldValue, newValue, backupPath, null);
        }

        public static UpdateResult failure(String filePath, int lineNumber, String errorMessage) {
            return new UpdateResult(false, filePath, lineNumber, null, null, null, errorMessage);
        }

        public static UpdateResult skipped(String filePath, String reason) {
            return new UpdateResult(false, filePath, 0, null, null, null, "Skipped: " + reason);
        }

        @Override
        public String toString() {
            if (success) {
                return String.format("Updated %s:%d - '%s' -> '%s'", filePath, lineNumber, oldValue, newValue);
            } else {
                return String.format("Failed %s - %s", filePath, errorMessage);
            }
        }
    }

    public SourceCodeUpdater(AutoUpdateConfig config) {
        this(config, Paths.get(System.getProperty("user.dir")));
    }

    public SourceCodeUpdater(AutoUpdateConfig config, Path projectRoot) {
        this.config = config != null ? config : AutoUpdateConfig.disabled();
        this.patternMatcher = new LocatorPatternMatcher();
        this.projectRoot = projectRoot;
    }

    /**
     * Updates source code for a single validated heal.
     *
     * @param heal the validated heal to apply
     * @return UpdateResult indicating success or failure
     */
    public UpdateResult updateSource(ValidatedHeal heal) {
        if (!config.isEnabled()) {
            return UpdateResult.skipped(heal.getSourceLocation() != null ?
                    heal.getSourceLocation().getFilePath() : "unknown", "Auto-update is disabled");
        }

        if (heal == null || !heal.canAutoUpdate()) {
            return UpdateResult.skipped("unknown", "Heal has no valid source location");
        }

        if (!heal.meetsConfidenceThreshold(config.getMinConfidence())) {
            return UpdateResult.skipped(heal.getSourceLocation().getFilePath(),
                    String.format("Confidence %.2f below threshold %.2f",
                            heal.getConfidence(), config.getMinConfidence()));
        }

        String filePath = heal.getSourceLocation().getFilePath();
        if (config.isExcluded(filePath)) {
            return UpdateResult.skipped(filePath, "File matches exclude pattern");
        }

        return performUpdate(heal);
    }

    /**
     * Applies all validated heals that meet the criteria.
     *
     * @param heals list of validated heals
     * @return list of UpdateResults
     */
    public List<UpdateResult> applyAllValidated(List<ValidatedHeal> heals) {
        if (heals == null || heals.isEmpty()) {
            return Collections.emptyList();
        }

        return heals.stream()
                .map(this::updateSource)
                .collect(Collectors.toList());
    }

    /**
     * Rolls back a previous update by restoring from backup.
     *
     * @param result the UpdateResult containing backup information
     * @return true if rollback succeeded
     */
    public boolean rollback(UpdateResult result) {
        if (result == null || !result.isSuccess() || result.getBackupPath() == null) {
            return false;
        }

        try {
            Path backupPath = Paths.get(result.getBackupPath());
            Path originalPath = Paths.get(result.getFilePath());

            if (!Files.exists(backupPath)) {
                logger.error("Backup file not found: {}", backupPath);
                return false;
            }

            Files.copy(backupPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Rolled back {} from backup {}", originalPath, backupPath);
            return true;
        } catch (IOException e) {
            logger.error("Failed to rollback {}: {}", result.getFilePath(), e.getMessage());
            return false;
        }
    }

    /**
     * Rolls back multiple updates.
     *
     * @param results list of UpdateResults to rollback
     * @return number of successful rollbacks
     */
    public int rollbackAll(List<UpdateResult> results) {
        if (results == null) {
            return 0;
        }

        return (int) results.stream()
                .filter(UpdateResult::isSuccess)
                .filter(this::rollback)
                .count();
    }

    /**
     * Performs the actual source code update.
     */
    private UpdateResult performUpdate(ValidatedHeal heal) {
        String filePath = heal.getSourceLocation().getFilePath();
        int lineNumber = heal.getSourceLocation().getLineNumber();

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return UpdateResult.failure(filePath, lineNumber, "Source file not found");
            }

            // Read all lines
            List<String> lines = Files.readAllLines(path);
            if (lineNumber < 1 || lineNumber > lines.size()) {
                return UpdateResult.failure(filePath, lineNumber,
                        "Line number out of range (file has " + lines.size() + " lines)");
            }

            // Get the line to update (0-indexed)
            String originalLine = lines.get(lineNumber - 1);

            // Try to replace the locator
            LocatorPatternMatcher.MatchResult matchResult = patternMatcher.replaceLocator(
                    originalLine,
                    heal.getOriginalLocator(),
                    heal.getHealedLocator(),
                    heal.getLocatorStrategy()
            );

            if (!matchResult.isFound()) {
                return UpdateResult.failure(filePath, lineNumber,
                        "Could not find locator '" + heal.getOriginalLocator() + "' in line");
            }

            // Check if it's a dry run
            if (config.isDryRun()) {
                logger.info("[DRY RUN] Would update {}:{} - '{}' -> '{}'",
                        filePath, lineNumber, heal.getOriginalLocator(), heal.getHealedLocator());
                return UpdateResult.success(filePath, lineNumber,
                        heal.getOriginalLocator(), heal.getHealedLocator(), null);
            }

            // Create backup if enabled
            String backupPath = null;
            if (config.isBackupEnabled()) {
                backupPath = createBackup(path);
                if (backupPath == null) {
                    return UpdateResult.failure(filePath, lineNumber, "Failed to create backup");
                }
            }

            // Update the line
            lines.set(lineNumber - 1, matchResult.getUpdatedLine());

            // Write the updated file
            Files.write(path, lines);

            logger.info("Updated {}:{} - '{}' -> '{}'",
                    filePath, lineNumber, heal.getOriginalLocator(), heal.getHealedLocator());

            return UpdateResult.success(filePath, lineNumber,
                    heal.getOriginalLocator(), heal.getHealedLocator(), backupPath);

        } catch (IOException e) {
            logger.error("Failed to update {}: {}", filePath, e.getMessage());
            return UpdateResult.failure(filePath, lineNumber, "IO error: " + e.getMessage());
        }
    }

    /**
     * Creates a backup of the source file.
     */
    private String createBackup(Path originalPath) {
        try {
            // Create backup directory
            Path backupDir = projectRoot.resolve(config.getBackupDir());
            Files.createDirectories(backupDir);

            // Generate backup filename with timestamp
            String fileName = originalPath.getFileName().toString();
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .format(Instant.now().atZone(java.time.ZoneId.systemDefault()));
            String backupFileName = fileName + "." + timestamp + ".bak";

            // Preserve directory structure in backup
            Path relativePath = projectRoot.relativize(originalPath.getParent());
            Path backupSubDir = backupDir.resolve(relativePath);
            Files.createDirectories(backupSubDir);

            Path backupPath = backupSubDir.resolve(backupFileName);
            Files.copy(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            logger.debug("Created backup: {}", backupPath);
            return backupPath.toString();

        } catch (IOException e) {
            logger.error("Failed to create backup for {}: {}", originalPath, e.getMessage());
            return null;
        }
    }

    /**
     * Lists all backup files in the backup directory.
     */
    public List<Path> listBackups() {
        try {
            Path backupDir = projectRoot.resolve(config.getBackupDir());
            if (!Files.exists(backupDir)) {
                return Collections.emptyList();
            }

            return Files.walk(backupDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".bak"))
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

        } catch (IOException e) {
            logger.error("Failed to list backups: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Cleans up old backup files.
     *
     * @param keepCount number of recent backups to keep per file
     * @return number of backups deleted
     */
    public int cleanupBackups(int keepCount) {
        try {
            Path backupDir = projectRoot.resolve(config.getBackupDir());
            if (!Files.exists(backupDir)) {
                return 0;
            }

            // Group backups by original file
            Map<String, List<Path>> backupsByFile = Files.walk(backupDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".bak"))
                    .collect(Collectors.groupingBy(p -> {
                        String name = p.getFileName().toString();
                        // Remove timestamp and .bak suffix to get original filename
                        int lastDot = name.lastIndexOf('.');
                        if (lastDot > 0) {
                            String withoutBak = name.substring(0, lastDot);
                            lastDot = withoutBak.lastIndexOf('.');
                            if (lastDot > 0) {
                                return withoutBak.substring(0, lastDot);
                            }
                        }
                        return name;
                    }));

            int deleted = 0;
            for (List<Path> backups : backupsByFile.values()) {
                // Sort by modification time (newest first)
                backups.sort((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException e) {
                        return 0;
                    }
                });

                // Delete all but the most recent keepCount
                for (int i = keepCount; i < backups.size(); i++) {
                    try {
                        Files.delete(backups.get(i));
                        deleted++;
                    } catch (IOException e) {
                        logger.warn("Failed to delete old backup: {}", backups.get(i));
                    }
                }
            }

            logger.info("Cleaned up {} old backup files", deleted);
            return deleted;

        } catch (IOException e) {
            logger.error("Failed to cleanup backups: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the configuration used by this updater.
     */
    public AutoUpdateConfig getConfig() {
        return config;
    }
}
