package io.github.glaciousm.llm;

import io.github.glaciousm.core.config.LlmConfig;
import io.github.glaciousm.core.exception.LlmException;
import io.github.glaciousm.core.model.*;
import io.github.glaciousm.llm.providers.AnthropicProvider;
import io.github.glaciousm.llm.providers.AzureOpenAiProvider;
import io.github.glaciousm.llm.providers.BedrockProvider;
import io.github.glaciousm.llm.providers.MockLlmProvider;
import io.github.glaciousm.llm.providers.OllamaProvider;
import io.github.glaciousm.llm.providers.OpenAiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Orchestrates LLM calls with fallback support and error handling.
 */
public class LlmOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(LlmOrchestrator.class);

    private final Map<String, LlmProvider> providers = new HashMap<>();
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public LlmOrchestrator() {
        this.promptBuilder = new PromptBuilder();
        this.responseParser = new ResponseParser();
        registerDefaultProviders();
    }

    private void registerDefaultProviders() {
        providers.put("openai", new OpenAiProvider());
        providers.put("anthropic", new AnthropicProvider());
        providers.put("ollama", new OllamaProvider());
        providers.put("local", new OllamaProvider()); // Alias for ollama
        providers.put("azure", new AzureOpenAiProvider());
        providers.put("azure-openai", new AzureOpenAiProvider()); // Alias for azure
        providers.put("bedrock", new BedrockProvider());
        providers.put("aws", new BedrockProvider()); // Alias for bedrock
        providers.put("mock", new MockLlmProvider()); // Mock provider for testing without real LLM
    }

    /**
     * Register a custom LLM provider.
     */
    public void registerProvider(String name, LlmProvider provider) {
        providers.put(name.toLowerCase(), provider);
    }

    /**
     * Check if a provider is available (has required API keys, etc.).
     *
     * @param providerName the name of the provider to check
     * @return true if the provider is available and configured
     */
    public boolean isProviderAvailable(String providerName) {
        return isProviderAvailable(providerName, null);
    }

    /**
     * Check if a provider is available with the given configuration.
     *
     * @param providerName the name of the provider to check
     * @param config the LLM configuration (to check base_url, api_key_env, etc.)
     * @return true if the provider is available and configured
     */
    public boolean isProviderAvailable(String providerName, LlmConfig config) {
        if (providerName == null || providerName.isEmpty()) {
            return false;
        }
        LlmProvider provider = providers.get(providerName.toLowerCase());
        if (provider == null) {
            return false;
        }
        return provider.isAvailable(config);
    }

    /**
     * Evaluate candidates using the configured LLM provider with fallback support.
     */
    public HealDecision evaluateCandidates(
            FailureContext failure,
            UiSnapshot snapshot,
            IntentContract intent,
            LlmConfig config) {

        // Try primary provider with retry
        LlmProvider primaryProvider = getProvider(config.getProvider());
        if (primaryProvider != null) {
            try {
                return executeWithRetry(
                        () -> primaryProvider.evaluateCandidates(failure, snapshot, intent, config),
                        config.getMaxRetries(),
                        config.getProvider()
                );
            } catch (LlmException e) {
                logger.warn("Primary LLM provider failed after retries: {}", e.getMessage());
                // Fall through to try fallbacks
            }
        }

        // Try fallback providers
        for (LlmConfig.FallbackProvider fallbackConfig : config.getFallback()) {
            LlmProvider fallbackProvider = getProvider(fallbackConfig.getProvider());
            if (fallbackProvider != null) {
                try {
                    logger.info("Trying fallback provider: {}/{}",
                            fallbackConfig.getProvider(), fallbackConfig.getModel());

                    LlmConfig fallbackLlmConfig = createFallbackConfig(config, fallbackConfig);
                    return executeWithRetry(
                            () -> fallbackProvider.evaluateCandidates(failure, snapshot, intent, fallbackLlmConfig),
                            fallbackLlmConfig.getMaxRetries(),
                            fallbackConfig.getProvider()
                    );
                } catch (LlmException e) {
                    logger.warn("Fallback provider {} failed: {}",
                            fallbackConfig.getProvider(), e.getMessage());
                    // Continue to next fallback
                }
            }
        }

        throw new LlmException("All LLM providers failed", config.getProvider(), config.getModel());
    }

    /**
     * Validate outcome using LLM reasoning.
     */
    public OutcomeResult validateOutcome(
            String expectedOutcome,
            UiSnapshot before,
            UiSnapshot after,
            LlmConfig config) {

        LlmProvider provider = getProvider(config.getProvider());
        if (provider == null) {
            throw new LlmException("Unknown provider: " + config.getProvider(),
                    config.getProvider(), config.getModel());
        }

        return provider.validateOutcome(expectedOutcome, before, after, config);
    }

    /**
     * Get the prompt builder for external use.
     */
    public PromptBuilder getPromptBuilder() {
        return promptBuilder;
    }

    /**
     * Get the response parser for external use.
     */
    public ResponseParser getResponseParser() {
        return responseParser;
    }

    private LlmProvider getProvider(String name) {
        return providers.get(name.toLowerCase());
    }

    private LlmConfig createFallbackConfig(LlmConfig original, LlmConfig.FallbackProvider fallback) {
        LlmConfig config = new LlmConfig();
        config.setProvider(fallback.getProvider());
        config.setModel(fallback.getModel());
        config.setTimeoutSeconds(original.getTimeoutSeconds());
        config.setMaxRetries(original.getMaxRetries());
        config.setTemperature(original.getTemperature());
        config.setConfidenceThreshold(original.getConfidenceThreshold());
        config.setMaxTokensPerRequest(original.getMaxTokensPerRequest());
        config.setRequireReasoning(original.isRequireReasoning());
        return config;
    }

    /**
     * Execute an LLM operation with exponential backoff retry on rate limiting.
     *
     * @param operation   The LLM operation to execute
     * @param maxRetries  Maximum number of retry attempts (0 = no retries)
     * @param providerName Provider name for logging
     * @return The result of the operation
     * @throws LlmException if all attempts fail
     */
    private <T> T executeWithRetry(Supplier<T> operation, int maxRetries, String providerName) {
        int attempts = 0;
        int maxAttempts = Math.max(1, maxRetries + 1); // At least 1 attempt
        long baseDelayMs = 1000; // Start with 1 second
        long maxDelayMs = 32000; // Cap at 32 seconds
        LlmException lastException = null;

        while (attempts < maxAttempts) {
            try {
                return operation.get();
            } catch (LlmException e) {
                lastException = e;
                attempts++;

                // Only retry on rate limiting errors
                if (!isRetryable(e)) {
                    logger.debug("Non-retryable error from {}: {}", providerName, e.getMessage());
                    throw e;
                }

                if (attempts >= maxAttempts) {
                    logger.warn("All {} retry attempts exhausted for {}", maxAttempts, providerName);
                    throw e;
                }

                // Calculate exponential backoff delay with jitter
                long delay = Math.min(baseDelayMs * (1L << (attempts - 1)), maxDelayMs);
                long jitter = (long) (delay * 0.1 * Math.random()); // Add 0-10% jitter
                delay += jitter;

                logger.info("Rate limited by {}. Attempt {}/{}, retrying in {}ms...",
                        providerName, attempts, maxAttempts, delay);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException("Retry interrupted", ie, providerName, "unknown");
                }
            }
        }

        // Should not reach here, but just in case
        throw lastException != null ? lastException :
                new LlmException("Operation failed with no exception", providerName, "unknown");
    }

    /**
     * Determines if an exception is retryable (rate limiting or transient errors).
     */
    private boolean isRetryable(LlmException e) {
        if (e.isRateLimited()) {
            return true;
        }

        // Also retry on common transient errors
        String msg = e.getMessage();
        if (msg != null) {
            String lowerMsg = msg.toLowerCase();
            return lowerMsg.contains("timeout") ||
                   lowerMsg.contains("connection reset") ||
                   lowerMsg.contains("service unavailable") ||
                   lowerMsg.contains("502") ||
                   lowerMsg.contains("503") ||
                   lowerMsg.contains("504");
        }
        return false;
    }
}
