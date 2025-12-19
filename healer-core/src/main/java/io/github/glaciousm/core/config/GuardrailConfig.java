package io.github.glaciousm.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for healing guardrails.
 */
public class GuardrailConfig {

    @JsonProperty("min_confidence")
    private double minConfidence = 0.80;

    @JsonProperty("max_heal_attempts_per_step")
    private int maxHealAttemptsPerStep = 2;

    @JsonProperty("max_heals_per_scenario")
    private int maxHealsPerScenario = 5;

    @JsonProperty("forbidden_keywords")
    private List<String> forbiddenKeywords = new ArrayList<>(List.of(
            // English
            "delete", "remove", "cancel", "unsubscribe", "terminate", "deactivate",
            "permanently", "irreversible", "close account",
            // Japanese
            "削除", "取り消し",
            // German
            "löschen",
            // French
            "supprimer",
            // Spanish
            "eliminar",
            // Russian
            "удалить",
            // Hebrew
            "מחק",
            // Arabic
            "حذف"
    ));

    @JsonProperty("error_selectors")
    private List<String> errorSelectors = new ArrayList<>(List.of(
            ".error-banner",
            ".alert-danger",
            ".error-message",
            "[role='alert']",
            ".notification-error"
    ));

    @JsonProperty("forbidden_url_patterns")
    private List<String> forbiddenUrlPatterns = new ArrayList<>();

    @JsonProperty("allow_js_click")
    private boolean allowJsClick = false;

    public GuardrailConfig() {
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public int getMaxHealAttemptsPerStep() {
        return maxHealAttemptsPerStep;
    }

    public void setMaxHealAttemptsPerStep(int maxHealAttemptsPerStep) {
        this.maxHealAttemptsPerStep = maxHealAttemptsPerStep;
    }

    public int getMaxHealsPerScenario() {
        return maxHealsPerScenario;
    }

    public void setMaxHealsPerScenario(int maxHealsPerScenario) {
        this.maxHealsPerScenario = maxHealsPerScenario;
    }

    public List<String> getForbiddenKeywords() {
        return forbiddenKeywords;
    }

    public void setForbiddenKeywords(List<String> forbiddenKeywords) {
        this.forbiddenKeywords = forbiddenKeywords != null ? forbiddenKeywords : new ArrayList<>();
    }

    public List<String> getErrorSelectors() {
        return errorSelectors;
    }

    public void setErrorSelectors(List<String> errorSelectors) {
        this.errorSelectors = errorSelectors != null ? errorSelectors : new ArrayList<>();
    }

    public List<String> getForbiddenUrlPatterns() {
        return forbiddenUrlPatterns;
    }

    public void setForbiddenUrlPatterns(List<String> forbiddenUrlPatterns) {
        this.forbiddenUrlPatterns = forbiddenUrlPatterns != null ? forbiddenUrlPatterns : new ArrayList<>();
    }

    public boolean isAllowJsClick() {
        return allowJsClick;
    }

    public void setAllowJsClick(boolean allowJsClick) {
        this.allowJsClick = allowJsClick;
    }

    /**
     * Check if an action involves a destructive/forbidden keyword.
     */
    public boolean containsForbiddenKeyword(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lowerText = text.toLowerCase();
        return forbiddenKeywords.stream()
                .anyMatch(keyword -> lowerText.contains(keyword.toLowerCase()));
    }

    /**
     * Validate guardrail configuration.
     */
    public void validate() {
        if (minConfidence < 0 || minConfidence > 1) {
            throw new IllegalArgumentException("Min confidence must be between 0 and 1");
        }
        if (maxHealAttemptsPerStep <= 0) {
            throw new IllegalArgumentException("Max heal attempts per step must be positive");
        }
        if (maxHealsPerScenario <= 0) {
            throw new IllegalArgumentException("Max heals per scenario must be positive");
        }
    }

    @Override
    public String toString() {
        return "GuardrailConfig{minConfidence=" + minConfidence +
               ", forbiddenKeywords=" + forbiddenKeywords.size() + " keywords}";
    }
}
