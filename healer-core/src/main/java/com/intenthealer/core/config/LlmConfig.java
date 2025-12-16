package com.intenthealer.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for LLM providers.
 */
public class LlmConfig {

    @JsonProperty("provider")
    private String provider = "openai";

    @JsonProperty("model")
    private String model = "gpt-4o-mini";

    @JsonProperty("api_key_env")
    private String apiKeyEnv = "OPENAI_API_KEY";

    @JsonProperty("base_url")
    private String baseUrl;

    @JsonProperty("timeout_seconds")
    private int timeoutSeconds = 30;

    @JsonProperty("max_retries")
    private int maxRetries = 2;

    @JsonProperty("temperature")
    private double temperature = 0.1;

    @JsonProperty("confidence_threshold")
    private double confidenceThreshold = 0.80;

    @JsonProperty("max_tokens_per_request")
    private int maxTokensPerRequest = 2000;

    @JsonProperty("max_requests_per_test_run")
    private int maxRequestsPerTestRun = 100;

    @JsonProperty("max_cost_per_run_usd")
    private double maxCostPerRunUsd = 5.00;

    @JsonProperty("require_reasoning")
    private boolean requireReasoning = true;

    @JsonProperty("fallback")
    private List<FallbackProvider> fallback = new ArrayList<>();

    public LlmConfig() {
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public void setApiKeyEnv(String apiKeyEnv) {
        this.apiKeyEnv = apiKeyEnv;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Alias for getBaseUrl() for compatibility.
     */
    public String getEndpoint() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Resolve the actual API key from the configured environment variable.
     */
    public String getApiKey() {
        if (apiKeyEnv == null || apiKeyEnv.isEmpty()) {
            return null;
        }
        return System.getenv(apiKeyEnv);
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getMaxTokensPerRequest() {
        return maxTokensPerRequest;
    }

    public void setMaxTokensPerRequest(int maxTokensPerRequest) {
        this.maxTokensPerRequest = maxTokensPerRequest;
    }

    public int getMaxRequestsPerTestRun() {
        return maxRequestsPerTestRun;
    }

    public void setMaxRequestsPerTestRun(int maxRequestsPerTestRun) {
        this.maxRequestsPerTestRun = maxRequestsPerTestRun;
    }

    public double getMaxCostPerRunUsd() {
        return maxCostPerRunUsd;
    }

    public void setMaxCostPerRunUsd(double maxCostPerRunUsd) {
        this.maxCostPerRunUsd = maxCostPerRunUsd;
    }

    public boolean isRequireReasoning() {
        return requireReasoning;
    }

    public void setRequireReasoning(boolean requireReasoning) {
        this.requireReasoning = requireReasoning;
    }

    public List<FallbackProvider> getFallback() {
        return fallback;
    }

    public void setFallback(List<FallbackProvider> fallback) {
        this.fallback = fallback != null ? fallback : new ArrayList<>();
    }

    /**
     * Validate LLM configuration.
     */
    public void validate() {
        if (provider == null || provider.isEmpty()) {
            throw new IllegalArgumentException("LLM provider must be specified");
        }
        if (model == null || model.isEmpty()) {
            throw new IllegalArgumentException("LLM model must be specified");
        }
        if (confidenceThreshold < 0 || confidenceThreshold > 1) {
            throw new IllegalArgumentException("Confidence threshold must be between 0 and 1");
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Temperature must be between 0 and 2");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }

    @Override
    public String toString() {
        return "LlmConfig{provider='" + provider + "', model='" + model +
               "', confidenceThreshold=" + confidenceThreshold + "}";
    }

    /**
     * Fallback provider configuration.
     */
    public static class FallbackProvider {
        @JsonProperty("provider")
        private String provider;

        @JsonProperty("model")
        private String model;

        public FallbackProvider() {
        }

        public FallbackProvider(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        @Override
        public String toString() {
            return "FallbackProvider{provider='" + provider + "', model='" + model + "'}";
        }
    }
}
