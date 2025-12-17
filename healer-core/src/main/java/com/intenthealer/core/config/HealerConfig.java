package com.intenthealer.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.intenthealer.core.engine.notification.NotificationConfig;
import com.intenthealer.core.engine.sharing.PatternSharingService.SharingConfig;
import com.intenthealer.core.model.HealPolicy;

/**
 * Main configuration class for the Intent Healer.
 */
public class HealerConfig {

    @JsonProperty("mode")
    private HealPolicy mode = HealPolicy.AUTO_SAFE;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("llm")
    private LlmConfig llm = new LlmConfig();

    @JsonProperty("guardrails")
    private GuardrailConfig guardrails = new GuardrailConfig();

    @JsonProperty("snapshot")
    private SnapshotConfig snapshot = new SnapshotConfig();

    @JsonProperty("cache")
    private CacheConfig cache = new CacheConfig();

    @JsonProperty("report")
    private ReportConfig report = new ReportConfig();

    @JsonProperty("circuit_breaker")
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    @JsonProperty("auto_update")
    private AutoUpdateConfig autoUpdate = AutoUpdateConfig.disabled();

    @JsonProperty("notification")
    private NotificationConfig notification = new NotificationConfig();

    @JsonProperty("sharing")
    private SharingConfig sharing = SharingConfig.defaults();

    public HealerConfig() {
    }

    public HealPolicy getMode() {
        return mode;
    }

    public void setMode(HealPolicy mode) {
        this.mode = mode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public GuardrailConfig getGuardrails() {
        return guardrails;
    }

    public void setGuardrails(GuardrailConfig guardrails) {
        this.guardrails = guardrails;
    }

    public SnapshotConfig getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(SnapshotConfig snapshot) {
        this.snapshot = snapshot;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    public ReportConfig getReport() {
        return report;
    }

    public void setReport(ReportConfig report) {
        this.report = report;
    }

    public CircuitBreakerConfig getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public AutoUpdateConfig getAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(AutoUpdateConfig autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public NotificationConfig getNotification() {
        return notification;
    }

    public void setNotification(NotificationConfig notification) {
        this.notification = notification;
    }

    public SharingConfig getSharing() {
        return sharing;
    }

    public void setSharing(SharingConfig sharing) {
        this.sharing = sharing;
    }

    /**
     * Apply default configuration values.
     */
    public void applyDefaults() {
        if (llm == null) llm = new LlmConfig();
        if (guardrails == null) guardrails = new GuardrailConfig();
        if (snapshot == null) snapshot = new SnapshotConfig();
        if (cache == null) cache = new CacheConfig();
        if (report == null) report = new ReportConfig();
        if (circuitBreaker == null) circuitBreaker = new CircuitBreakerConfig();
        if (autoUpdate == null) autoUpdate = AutoUpdateConfig.disabled();
        if (notification == null) notification = new NotificationConfig();
        if (sharing == null) sharing = SharingConfig.defaults();
    }

    /**
     * Validate the configuration.
     */
    public void validate() {
        if (llm != null) llm.validate();
        if (guardrails != null) guardrails.validate();
    }

    @Override
    public String toString() {
        return "HealerConfig{mode=" + mode + ", enabled=" + enabled + ", llm=" + llm + "}";
    }
}
