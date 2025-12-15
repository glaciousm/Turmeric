package com.intenthealer.llm;

import com.intenthealer.core.config.LlmConfig;
import com.intenthealer.core.exception.LlmException;
import com.intenthealer.core.model.*;
import com.intenthealer.llm.providers.AnthropicProvider;
import com.intenthealer.llm.providers.OllamaProvider;
import com.intenthealer.llm.providers.OpenAiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

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
    }

    /**
     * Register a custom LLM provider.
     */
    public void registerProvider(String name, LlmProvider provider) {
        providers.put(name.toLowerCase(), provider);
    }

    /**
     * Evaluate candidates using the configured LLM provider with fallback support.
     */
    public HealDecision evaluateCandidates(
            FailureContext failure,
            UiSnapshot snapshot,
            IntentContract intent,
            LlmConfig config) {

        // Try primary provider
        LlmProvider primaryProvider = getProvider(config.getProvider());
        if (primaryProvider != null) {
            try {
                return primaryProvider.evaluateCandidates(failure, snapshot, intent, config);
            } catch (LlmException e) {
                logger.warn("Primary LLM provider failed: {}", e.getMessage());
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
                    return fallbackProvider.evaluateCandidates(failure, snapshot, intent, fallbackLlmConfig);
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
}
