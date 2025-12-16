package com.intenthealer.core.engine.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Notification service for sending heal alerts to external channels.
 * Supports Slack, Microsoft Teams, and custom webhooks.
 */
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final List<NotificationChannel> channels;
    private final NotificationConfig config;

    public NotificationService(NotificationConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "notification-sender");
            t.setDaemon(true);
            return t;
        });
        this.channels = new CopyOnWriteArrayList<>();

        // Register configured channels
        if (config.getSlackWebhookUrl() != null) {
            channels.add(new SlackChannel(config.getSlackWebhookUrl()));
        }
        if (config.getTeamsWebhookUrl() != null) {
            channels.add(new TeamsChannel(config.getTeamsWebhookUrl()));
        }
        if (config.getCustomWebhookUrl() != null) {
            channels.add(new CustomWebhookChannel(config.getCustomWebhookUrl()));
        }
    }

    /**
     * Add a custom notification channel.
     */
    public void addChannel(NotificationChannel channel) {
        channels.add(channel);
    }

    /**
     * Send a heal notification.
     */
    public CompletableFuture<Void> notifyHeal(HealNotification notification) {
        if (!config.isEnabled() || channels.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // Check notification level
        if (!shouldNotify(notification)) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            for (NotificationChannel channel : channels) {
                try {
                    channel.send(notification, httpClient, objectMapper);
                } catch (Exception e) {
                    logger.warn("Failed to send notification to {}: {}",
                            channel.getName(), e.getMessage());
                }
            }
        }, executor);
    }

    /**
     * Send a batch summary notification.
     */
    public CompletableFuture<Void> notifySummary(HealSummary summary) {
        if (!config.isEnabled() || channels.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            for (NotificationChannel channel : channels) {
                try {
                    channel.sendSummary(summary, httpClient, objectMapper);
                } catch (Exception e) {
                    logger.warn("Failed to send summary to {}: {}",
                            channel.getName(), e.getMessage());
                }
            }
        }, executor);
    }

    private boolean shouldNotify(HealNotification notification) {
        return switch (config.getNotificationLevel()) {
            case ALL -> true;
            case FAILURES_ONLY -> !notification.success();
            case LOW_CONFIDENCE -> notification.confidence() < config.getConfidenceThreshold();
            case NONE -> false;
        };
    }

    /**
     * Shutdown the notification service.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Notification channel interface.
     */
    public interface NotificationChannel {
        String getName();
        void send(HealNotification notification, HttpClient client, ObjectMapper mapper) throws Exception;
        void sendSummary(HealSummary summary, HttpClient client, ObjectMapper mapper) throws Exception;
    }

    /**
     * Slack notification channel.
     */
    public static class SlackChannel implements NotificationChannel {
        private final String webhookUrl;

        public SlackChannel(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        @Override
        public String getName() {
            return "Slack";
        }

        @Override
        public void send(HealNotification notification, HttpClient client, ObjectMapper mapper) throws Exception {
            String emoji = notification.success() ? ":white_check_mark:" : ":warning:";
            String color = notification.success() ? "good" : "warning";

            Map<String, Object> attachment = Map.of(
                    "color", color,
                    "title", String.format("%s Heal %s", emoji, notification.success() ? "Applied" : "Failed"),
                    "fields", List.of(
                            Map.of("title", "Feature", "value", notification.featureName(), "short", true),
                            Map.of("title", "Scenario", "value", notification.scenarioName(), "short", true),
                            Map.of("title", "Step", "value", notification.stepText(), "short", false),
                            Map.of("title", "Original", "value", "`" + notification.originalLocator() + "`", "short", false),
                            Map.of("title", "Healed", "value", "`" + notification.healedLocator() + "`", "short", false),
                            Map.of("title", "Confidence", "value", String.format("%.1f%%", notification.confidence() * 100), "short", true),
                            Map.of("title", "Trust Level", "value", notification.trustLevel(), "short", true)
                    ),
                    "footer", "Intent Healer",
                    "ts", notification.timestamp().getEpochSecond()
            );

            Map<String, Object> payload = Map.of("attachments", List.of(attachment));
            sendWebhook(client, mapper, payload);
        }

        @Override
        public void sendSummary(HealSummary summary, HttpClient client, ObjectMapper mapper) throws Exception {
            String emoji = summary.failureCount() == 0 ? ":chart_with_upwards_trend:" : ":chart_with_downwards_trend:";

            Map<String, Object> attachment = Map.of(
                    "color", summary.failureCount() == 0 ? "good" : "warning",
                    "title", String.format("%s Heal Summary", emoji),
                    "fields", List.of(
                            Map.of("title", "Total Heals", "value", String.valueOf(summary.totalHeals()), "short", true),
                            Map.of("title", "Success Rate", "value", String.format("%.1f%%", summary.successRate()), "short", true),
                            Map.of("title", "Successes", "value", String.valueOf(summary.successCount()), "short", true),
                            Map.of("title", "Failures", "value", String.valueOf(summary.failureCount()), "short", true),
                            Map.of("title", "Avg Confidence", "value", String.format("%.1f%%", summary.avgConfidence() * 100), "short", true),
                            Map.of("title", "Trust Level", "value", summary.currentTrustLevel(), "short", true)
                    ),
                    "footer", "Intent Healer | " + summary.period(),
                    "ts", Instant.now().getEpochSecond()
            );

            Map<String, Object> payload = Map.of("attachments", List.of(attachment));
            sendWebhook(client, mapper, payload);
        }

        private void sendWebhook(HttpClient client, ObjectMapper mapper, Map<String, Object> payload) throws Exception {
            String json = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Slack webhook failed: " + response.statusCode());
            }
        }
    }

    /**
     * Microsoft Teams notification channel.
     */
    public static class TeamsChannel implements NotificationChannel {
        private final String webhookUrl;

        public TeamsChannel(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        @Override
        public String getName() {
            return "Teams";
        }

        @Override
        public void send(HealNotification notification, HttpClient client, ObjectMapper mapper) throws Exception {
            String themeColor = notification.success() ? "00FF00" : "FFA500";

            Map<String, Object> payload = Map.of(
                    "@type", "MessageCard",
                    "@context", "http://schema.org/extensions",
                    "themeColor", themeColor,
                    "summary", "Heal " + (notification.success() ? "Applied" : "Failed"),
                    "sections", List.of(Map.of(
                            "activityTitle", String.format("Heal %s", notification.success() ? "Applied ✓" : "Failed ⚠"),
                            "facts", List.of(
                                    Map.of("name", "Feature", "value", notification.featureName()),
                                    Map.of("name", "Scenario", "value", notification.scenarioName()),
                                    Map.of("name", "Step", "value", notification.stepText()),
                                    Map.of("name", "Original", "value", notification.originalLocator()),
                                    Map.of("name", "Healed", "value", notification.healedLocator()),
                                    Map.of("name", "Confidence", "value", String.format("%.1f%%", notification.confidence() * 100)),
                                    Map.of("name", "Trust Level", "value", notification.trustLevel())
                            ),
                            "markdown", true
                    ))
            );

            sendWebhook(client, mapper, payload);
        }

        @Override
        public void sendSummary(HealSummary summary, HttpClient client, ObjectMapper mapper) throws Exception {
            String themeColor = summary.failureCount() == 0 ? "00FF00" : "FFA500";

            Map<String, Object> payload = Map.of(
                    "@type", "MessageCard",
                    "@context", "http://schema.org/extensions",
                    "themeColor", themeColor,
                    "summary", "Heal Summary",
                    "sections", List.of(Map.of(
                            "activityTitle", "📊 Heal Summary - " + summary.period(),
                            "facts", List.of(
                                    Map.of("name", "Total Heals", "value", String.valueOf(summary.totalHeals())),
                                    Map.of("name", "Success Rate", "value", String.format("%.1f%%", summary.successRate())),
                                    Map.of("name", "Successes", "value", String.valueOf(summary.successCount())),
                                    Map.of("name", "Failures", "value", String.valueOf(summary.failureCount())),
                                    Map.of("name", "Avg Confidence", "value", String.format("%.1f%%", summary.avgConfidence() * 100)),
                                    Map.of("name", "Trust Level", "value", summary.currentTrustLevel())
                            ),
                            "markdown", true
                    ))
            );

            sendWebhook(client, mapper, payload);
        }

        private void sendWebhook(HttpClient client, ObjectMapper mapper, Map<String, Object> payload) throws Exception {
            String json = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Teams webhook failed: " + response.statusCode());
            }
        }
    }

    /**
     * Custom webhook notification channel.
     */
    public static class CustomWebhookChannel implements NotificationChannel {
        private final String webhookUrl;

        public CustomWebhookChannel(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        @Override
        public String getName() {
            return "Custom Webhook";
        }

        @Override
        public void send(HealNotification notification, HttpClient client, ObjectMapper mapper) throws Exception {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "heal");
            payload.put("success", notification.success());
            payload.put("timestamp", notification.timestamp().toString());
            payload.put("feature", notification.featureName());
            payload.put("scenario", notification.scenarioName());
            payload.put("step", notification.stepText());
            payload.put("originalLocator", notification.originalLocator());
            payload.put("healedLocator", notification.healedLocator());
            payload.put("confidence", notification.confidence());
            payload.put("trustLevel", notification.trustLevel());
            payload.put("reasoning", notification.reasoning());

            sendWebhook(client, mapper, payload);
        }

        @Override
        public void sendSummary(HealSummary summary, HttpClient client, ObjectMapper mapper) throws Exception {
            Map<String, Object> payload = Map.of(
                    "type", "summary",
                    "period", summary.period(),
                    "totalHeals", summary.totalHeals(),
                    "successCount", summary.successCount(),
                    "failureCount", summary.failureCount(),
                    "successRate", summary.successRate(),
                    "avgConfidence", summary.avgConfidence(),
                    "currentTrustLevel", summary.currentTrustLevel()
            );

            sendWebhook(client, mapper, payload);
        }

        private void sendWebhook(HttpClient client, ObjectMapper mapper, Map<String, Object> payload) throws Exception {
            String json = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Custom webhook failed: " + response.statusCode());
            }
        }
    }

    /**
     * Heal notification data.
     */
    public record HealNotification(
            Instant timestamp,
            String featureName,
            String scenarioName,
            String stepText,
            String originalLocator,
            String healedLocator,
            double confidence,
            String trustLevel,
            String reasoning,
            boolean success
    ) {}

    /**
     * Heal summary data.
     */
    public record HealSummary(
            String period,
            int totalHeals,
            int successCount,
            int failureCount,
            double successRate,
            double avgConfidence,
            String currentTrustLevel
    ) {}

    /**
     * Notification level configuration.
     */
    public enum NotificationLevel {
        ALL,
        FAILURES_ONLY,
        LOW_CONFIDENCE,
        NONE
    }
}
