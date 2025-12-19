package io.github.glaciousm.intellij.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import io.github.glaciousm.intellij.settings.HealerSettings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches for new heal events from the healing engine in real-time.
 * Monitors the healer-reports directory for new JSON report files and
 * pushes heal events to the IntelliJ plugin UI.
 */
public class HealEventWatcher implements Disposable {

    private static final Logger logger = LoggerFactory.getLogger(HealEventWatcher.class);

    private final Project project;
    private final HealerProjectService healerService;
    private final ObjectMapper objectMapper;
    private final List<HealEventListener> listeners;
    private final AtomicBoolean watching;
    private final Set<String> processedFiles;

    private ExecutorService watcherExecutor;
    private ScheduledExecutorService pollingExecutor;
    private WatchService watchService;
    private Path watchedDirectory;

    public HealEventWatcher(@NotNull Project project) {
        this.project = project;
        this.healerService = HealerProjectService.getInstance(project);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.listeners = new CopyOnWriteArrayList<>();
        this.watching = new AtomicBoolean(false);
        this.processedFiles = ConcurrentHashMap.newKeySet();
    }

    /**
     * Start watching for heal events.
     */
    public void startWatching() {
        if (watching.getAndSet(true)) {
            logger.info("Already watching for heal events");
            return;
        }

        HealerSettings settings = HealerSettings.getInstance();
        String basePath = project.getBasePath();
        if (basePath == null) {
            basePath = System.getProperty("user.home");
        }

        watchedDirectory = Path.of(basePath, settings.healReportsDirectory);

        try {
            Files.createDirectories(watchedDirectory);
        } catch (IOException e) {
            logger.warn("Could not create reports directory: {}", watchedDirectory, e);
        }

        // Start file system watcher
        watcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "healer-event-watcher");
            t.setDaemon(true);
            return t;
        });
        watcherExecutor.submit(this::watchDirectory);

        // Start polling as backup (some file systems don't support watch)
        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "healer-event-poller");
            t.setDaemon(true);
            return t;
        });
        pollingExecutor.scheduleAtFixedRate(
                this::pollForNewFiles,
                5, // initial delay
                settings.watchPollingIntervalSeconds,
                TimeUnit.SECONDS
        );

        logger.info("Started watching for heal events in: {}", watchedDirectory);
        notifyWatchingStarted();
    }

    /**
     * Stop watching for heal events.
     */
    public void stopWatching() {
        if (!watching.getAndSet(false)) {
            return;
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.warn("Error closing watch service", e);
            }
        }

        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
        }

        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
        }

        logger.info("Stopped watching for heal events");
        notifyWatchingStopped();
    }

    /**
     * Check if currently watching.
     */
    public boolean isWatching() {
        return watching.get();
    }

    /**
     * Add a listener for heal events.
     */
    public void addListener(HealEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(HealEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Watch directory using Java NIO WatchService.
     */
    private void watchDirectory() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchedDirectory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            while (watching.get()) {
                WatchKey key;
                try {
                    key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key == null) {
                        continue;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    Path filePath = watchedDirectory.resolve(fileName);

                    if (isHealReportFile(filePath)) {
                        processHealReportFile(filePath);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Error setting up watch service", e);
        }
    }

    /**
     * Poll for new files (backup mechanism).
     */
    private void pollForNewFiles() {
        if (!watching.get() || !Files.exists(watchedDirectory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(watchedDirectory, "healer-report-*.json")) {
            for (Path file : stream) {
                if (!processedFiles.contains(file.toString())) {
                    processHealReportFile(file);
                }
            }
        } catch (IOException e) {
            logger.debug("Error polling for files", e);
        }
    }

    /**
     * Check if a file is a heal report file.
     */
    private boolean isHealReportFile(Path file) {
        String name = file.getFileName().toString();
        return name.startsWith("healer-report-") && name.endsWith(".json");
    }

    /**
     * Process a heal report file and extract events.
     */
    private void processHealReportFile(Path file) {
        String filePath = file.toString();
        if (processedFiles.contains(filePath)) {
            return;
        }

        // Mark as processed to avoid duplicates
        processedFiles.add(filePath);

        try {
            // Wait briefly for file to be fully written
            Thread.sleep(100);

            HealReportData report = objectMapper.readValue(file.toFile(), HealReportData.class);

            if (report.events != null) {
                for (HealEventData event : report.events) {
                    HealerProjectService.HealHistoryEntry entry = convertToHistoryEntry(event);
                    if (entry != null) {
                        healerService.addHealEntry(entry);
                        notifyHealEventReceived(entry);
                    }
                }
            }

            logger.debug("Processed heal report: {} ({} events)",
                    file.getFileName(), report.events != null ? report.events.size() : 0);

        } catch (IOException e) {
            logger.warn("Error reading heal report: {}", file, e);
            // Remove from processed so it can be retried
            processedFiles.remove(filePath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Convert heal event data to history entry.
     */
    private HealerProjectService.HealHistoryEntry convertToHistoryEntry(HealEventData event) {
        if (event == null) {
            return null;
        }

        String id = event.eventId != null ? event.eventId : UUID.randomUUID().toString();
        Instant timestamp = event.timestamp != null ? event.timestamp : Instant.now();

        String originalLocator = "";
        if (event.failure != null && event.failure.originalLocator != null) {
            originalLocator = event.failure.originalLocator;
        }

        String healedLocator = "";
        double confidence = 0;
        String reasoning = "";
        if (event.decision != null) {
            healedLocator = event.decision.healedLocator != null ? event.decision.healedLocator : "";
            confidence = event.decision.confidence;
            reasoning = event.decision.reasoning != null ? event.decision.reasoning : "";
        }

        // Also get healed locator from result if not in decision
        if (healedLocator.isEmpty() && event.result != null && event.result.healedLocator != null) {
            healedLocator = event.result.healedLocator;
        }

        return new HealerProjectService.HealHistoryEntry(
                id,
                timestamp,
                event.feature != null ? event.feature : "",
                event.scenario != null ? event.scenario : "",
                event.step != null ? event.step : "",
                originalLocator,
                healedLocator,
                confidence,
                reasoning,
                HealerProjectService.HealStatus.PENDING
        );
    }

    private void notifyWatchingStarted() {
        for (HealEventListener listener : listeners) {
            listener.onWatchingStarted(watchedDirectory);
        }
    }

    private void notifyWatchingStopped() {
        for (HealEventListener listener : listeners) {
            listener.onWatchingStopped();
        }
    }

    private void notifyHealEventReceived(HealerProjectService.HealHistoryEntry entry) {
        for (HealEventListener listener : listeners) {
            listener.onHealEventReceived(entry);
        }
    }

    @Override
    public void dispose() {
        stopWatching();
        listeners.clear();
    }

    /**
     * Listener interface for heal events.
     */
    public interface HealEventListener {
        default void onWatchingStarted(Path directory) {}
        default void onWatchingStopped() {}
        default void onHealEventReceived(HealerProjectService.HealHistoryEntry entry) {}
    }

    // Data classes for JSON parsing (simplified versions of report model)

    private static class HealReportData {
        public Instant timestamp;
        public List<HealEventData> events;
    }

    private static class HealEventData {
        public String eventId;
        public Instant timestamp;
        public String feature;
        public String scenario;
        public String step;
        public FailureData failure;
        public DecisionData decision;
        public ResultData result;
    }

    private static class FailureData {
        public String originalLocator;
        public String exceptionType;
        public String message;
    }

    private static class DecisionData {
        public String healedLocator;
        public double confidence;
        public String reasoning;
    }

    private static class ResultData {
        public String status;
        public String healedLocator;
    }
}
