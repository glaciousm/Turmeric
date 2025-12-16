package com.intenthealer.llm;

import java.util.Map;

/**
 * Request to an LLM provider for text completion.
 */
public final class LlmRequest {
    private final String prompt;
    private final String systemMessage;
    private final int maxTokens;
    private final double temperature;
    private final Map<String, Object> metadata;

    private LlmRequest(Builder builder) {
        this.prompt = builder.prompt;
        this.systemMessage = builder.systemMessage;
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    public String getPrompt() {
        return prompt;
    }

    /**
     * Alias for getPrompt() for compatibility.
     */
    public String getUserPrompt() {
        return prompt;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

    /**
     * Alias for getSystemMessage() for compatibility.
     */
    public String getSystemPrompt() {
        return systemMessage;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String prompt;
        private String systemMessage;
        private int maxTokens = 1000;
        private double temperature = 0.0;
        private Map<String, Object> metadata;

        private Builder() {}

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(this);
        }
    }
}
