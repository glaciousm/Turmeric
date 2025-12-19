package io.github.glaciousm.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.glaciousm.cli.util.CliOutput;
import io.github.glaciousm.report.model.HealEvent;
import io.github.glaciousm.report.model.HealReport;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CLI command for watching heal events in real-time.
 * Monitors a directory for new heal reports and displays events as they occur.
 */
public class WatchCommand {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public WatchCommand() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Watch a directory for new heal events.
     */
    public void watch(String directory) throws IOException, InterruptedException {
        Path dirPath = Path.of(directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        CliOutput.println("""
            ╔═══════════════════════════════════════════════════════════════╗
            ║               INTENT HEALER - LIVE MONITOR                    ║
            ╚═══════════════════════════════════════════════════════════════╝
            """);
        CliOutput.println("  Watching: " + dirPath.toAbsolutePath());
        CliOutput.println("  Press Ctrl+C to stop");
        CliOutput.println("");
        CliOutput.println("  ─────────────────────────────────────────────────────────────");

        // Track already processed files
        Set<String> processedFiles = new HashSet<>();

        // Initial scan of existing files
        if (Files.exists(dirPath)) {
            try (var stream = Files.list(dirPath)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                      .forEach(p -> processedFiles.add(p.toString()));
            }
        }

        // Add shutdown hook for graceful exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            CliOutput.println("\n  Stopping watch...");
        }));

        // Watch for new files
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            dirPath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

            while (running.get()) {
                WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path filePath = dirPath.resolve(filename);

                    if (filename.toString().endsWith(".json") &&
                        !processedFiles.contains(filePath.toString())) {

                        // Small delay to ensure file is fully written
                        Thread.sleep(100);

                        processNewFile(filePath);
                        processedFiles.add(filePath.toString());
                    }
                }

                key.reset();
            }
        }
    }

    /**
     * Process a new heal report file and display its events.
     */
    private void processNewFile(Path filePath) {
        try {
            HealReport report = objectMapper.readValue(filePath.toFile(), HealReport.class);

            for (HealEvent event : report.getEvents()) {
                displayEvent(event);
            }
        } catch (Exception e) {
            CliOutput.warn("  Failed to process: " + filePath.getFileName() + " - " + e.getMessage());
        }
    }

    /**
     * Display a single heal event in a formatted way.
     */
    private void displayEvent(HealEvent event) {
        String timestamp = LocalDateTime.ofInstant(
            event.getTimestamp() != null ? event.getTimestamp() : Instant.now(),
            ZoneId.systemDefault()
        ).format(TIME_FORMAT);

        String status = event.getResult() != null ? event.getResult().getStatus() : "UNKNOWN";
        String statusIcon = switch (status) {
            case "SUCCESS" -> "✅";
            case "REFUSED" -> "⚠️";
            case "FAILED" -> "❌";
            default -> "❓";
        };

        double confidence = event.getDecision() != null ? event.getDecision().getConfidence() * 100 : 0;

        CliOutput.println("");
        CliOutput.println("  " + statusIcon + " [" + timestamp + "] " + truncate(event.getStep(), 50));
        CliOutput.println("     Feature:    " + truncate(event.getFeature(), 45));
        CliOutput.println("     Scenario:   " + truncate(event.getScenario(), 45));

        if (event.getFailure() != null && event.getFailure().getOriginalLocator() != null) {
            CliOutput.println("     Original:   " + truncate(event.getFailure().getOriginalLocator(), 45));
        }

        if (event.getResult() != null && event.getResult().getHealedLocator() != null) {
            CliOutput.println("     Healed to:  " + truncate(event.getResult().getHealedLocator(), 45));
        }

        CliOutput.println("     Confidence: " + String.format("%.0f%%", confidence));

        if (event.getDecision() != null && event.getDecision().getReasoning() != null) {
            CliOutput.println("     Reasoning:  " + truncate(event.getDecision().getReasoning(), 50));
        }

        CliOutput.println("  ─────────────────────────────────────────────────────────────");
    }

    /**
     * Watch with default directory.
     */
    public void watch() throws IOException, InterruptedException {
        watch("./healer-reports");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
