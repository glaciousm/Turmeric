package io.github.glaciousm.core.engine.notification;

/**
 * Configuration for the notification service.
 */
public class NotificationConfig {

    private boolean enabled = false;
    private String slackWebhookUrl;
    private String teamsWebhookUrl;
    private String customWebhookUrl;
    private NotificationService.NotificationLevel notificationLevel = NotificationService.NotificationLevel.ALL;
    private double confidenceThreshold = 0.75;
    private boolean sendSummary = true;
    private String summarySchedule = "daily"; // daily, weekly, on-failure

    public NotificationConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public String getSlackWebhookUrl() { return slackWebhookUrl; }
    public String getTeamsWebhookUrl() { return teamsWebhookUrl; }
    public String getCustomWebhookUrl() { return customWebhookUrl; }
    public NotificationService.NotificationLevel getNotificationLevel() { return notificationLevel; }
    public double getConfidenceThreshold() { return confidenceThreshold; }
    public boolean isSendSummary() { return sendSummary; }
    public String getSummarySchedule() { return summarySchedule; }

    /**
     * Builder for NotificationConfig.
     */
    public static class Builder {
        private final NotificationConfig config = new NotificationConfig();

        public Builder enabled(boolean enabled) {
            config.enabled = enabled;
            return this;
        }

        public Builder slackWebhookUrl(String url) {
            config.slackWebhookUrl = url;
            return this;
        }

        public Builder teamsWebhookUrl(String url) {
            config.teamsWebhookUrl = url;
            return this;
        }

        public Builder customWebhookUrl(String url) {
            config.customWebhookUrl = url;
            return this;
        }

        public Builder notificationLevel(NotificationService.NotificationLevel level) {
            config.notificationLevel = level;
            return this;
        }

        public Builder confidenceThreshold(double threshold) {
            config.confidenceThreshold = threshold;
            return this;
        }

        public Builder sendSummary(boolean sendSummary) {
            config.sendSummary = sendSummary;
            return this;
        }

        public Builder summarySchedule(String schedule) {
            config.summarySchedule = schedule;
            return this;
        }

        public NotificationConfig build() {
            return config;
        }
    }
}
