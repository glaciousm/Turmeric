package io.github.glaciousm.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for circuit breaker behavior.
 */
public class CircuitBreakerConfig {

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("false_heal_rate_threshold")
    private double falseHealRateThreshold = 0.01;  // 1%

    @JsonProperty("false_heal_rate_window_days")
    private int falseHealRateWindowDays = 7;

    @JsonProperty("false_heal_rate_min_samples")
    private int falseHealRateMinSamples = 20;

    @JsonProperty("consecutive_failures_threshold")
    private int consecutiveFailuresThreshold = 3;

    @JsonProperty("failure_threshold")
    private int failureThreshold = 3;

    @JsonProperty("success_threshold_to_close")
    private int successThresholdToClose = 2;

    @JsonProperty("p95_latency_threshold_ms")
    private int p95LatencyThresholdMs = 10000;

    @JsonProperty("latency_window_hours")
    private int latencyWindowHours = 1;

    @JsonProperty("daily_cost_limit_usd")
    private double dailyCostLimitUsd = 10.00;

    @JsonProperty("cooldown_minutes")
    private int cooldownMinutes = 30;

    @JsonProperty("open_duration_seconds")
    private long openDurationSeconds = 1800; // 30 minutes default

    @JsonProperty("half_open_max_attempts")
    private int halfOpenMaxAttempts = 3;

    @JsonProperty("test_heals_required")
    private int testHealsRequired = 3;

    public CircuitBreakerConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getFalseHealRateThreshold() {
        return falseHealRateThreshold;
    }

    public void setFalseHealRateThreshold(double falseHealRateThreshold) {
        this.falseHealRateThreshold = falseHealRateThreshold;
    }

    public int getFalseHealRateWindowDays() {
        return falseHealRateWindowDays;
    }

    public void setFalseHealRateWindowDays(int falseHealRateWindowDays) {
        this.falseHealRateWindowDays = falseHealRateWindowDays;
    }

    public int getFalseHealRateMinSamples() {
        return falseHealRateMinSamples;
    }

    public void setFalseHealRateMinSamples(int falseHealRateMinSamples) {
        this.falseHealRateMinSamples = falseHealRateMinSamples;
    }

    public int getConsecutiveFailuresThreshold() {
        return consecutiveFailuresThreshold;
    }

    public void setConsecutiveFailuresThreshold(int consecutiveFailuresThreshold) {
        this.consecutiveFailuresThreshold = consecutiveFailuresThreshold;
        this.failureThreshold = consecutiveFailuresThreshold;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
        this.consecutiveFailuresThreshold = failureThreshold;
    }

    public int getSuccessThresholdToClose() {
        return successThresholdToClose;
    }

    public void setSuccessThresholdToClose(int successThresholdToClose) {
        this.successThresholdToClose = successThresholdToClose;
    }

    public int getP95LatencyThresholdMs() {
        return p95LatencyThresholdMs;
    }

    public void setP95LatencyThresholdMs(int p95LatencyThresholdMs) {
        this.p95LatencyThresholdMs = p95LatencyThresholdMs;
    }

    public int getLatencyWindowHours() {
        return latencyWindowHours;
    }

    public void setLatencyWindowHours(int latencyWindowHours) {
        this.latencyWindowHours = latencyWindowHours;
    }

    public double getDailyCostLimitUsd() {
        return dailyCostLimitUsd;
    }

    public void setDailyCostLimitUsd(double dailyCostLimitUsd) {
        this.dailyCostLimitUsd = dailyCostLimitUsd;
    }

    public int getCooldownMinutes() {
        return cooldownMinutes;
    }

    public void setCooldownMinutes(int cooldownMinutes) {
        this.cooldownMinutes = cooldownMinutes;
        this.openDurationSeconds = cooldownMinutes * 60L;
    }

    public long getOpenDurationSeconds() {
        return openDurationSeconds;
    }

    public void setOpenDurationSeconds(long openDurationSeconds) {
        this.openDurationSeconds = openDurationSeconds;
    }

    public int getHalfOpenMaxAttempts() {
        return halfOpenMaxAttempts;
    }

    public void setHalfOpenMaxAttempts(int halfOpenMaxAttempts) {
        this.halfOpenMaxAttempts = halfOpenMaxAttempts;
    }

    public int getTestHealsRequired() {
        return testHealsRequired;
    }

    public void setTestHealsRequired(int testHealsRequired) {
        this.testHealsRequired = testHealsRequired;
    }

    @Override
    public String toString() {
        return "CircuitBreakerConfig{enabled=" + enabled +
               ", failureThreshold=" + failureThreshold +
               ", openDurationSeconds=" + openDurationSeconds + "}";
    }
}
