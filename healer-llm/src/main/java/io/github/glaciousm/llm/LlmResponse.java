package io.github.glaciousm.llm;

import java.util.Optional;

/**
 * Response from an LLM provider.
 */
public final class LlmResponse {
    private final boolean success;
    private final String content;
    private final String errorMessage;
    private final int promptTokens;
    private final int completionTokens;
    private final long latencyMs;

    private LlmResponse(Builder builder) {
        this.success = builder.success;
        this.content = builder.content;
        this.errorMessage = builder.errorMessage;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.latencyMs = builder.latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public Optional<String> getContent() {
        return Optional.ofNullable(content);
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return promptTokens + completionTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LlmResponse error(String errorMessage) {
        return builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static LlmResponse success(String content) {
        return builder()
                .success(true)
                .content(content)
                .build();
    }

    public static final class Builder {
        private boolean success;
        private String content;
        private String errorMessage;
        private int promptTokens;
        private int completionTokens;
        private long latencyMs;
        private String model;

        private Builder() {}

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder promptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public LlmResponse build() {
            return new LlmResponse(this);
        }
    }
}
