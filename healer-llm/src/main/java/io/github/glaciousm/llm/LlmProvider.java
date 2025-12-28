package io.github.glaciousm.llm;

import io.github.glaciousm.core.config.LlmConfig;
import io.github.glaciousm.core.model.*;

/**
 * Interface for LLM provider implementations.
 * Providers communicate with external LLM services to make healing decisions.
 */
public interface LlmProvider {

    /**
     * Evaluate candidate elements and return a heal decision.
     *
     * @param failure  The failure context
     * @param snapshot The current UI snapshot
     * @param intent   The intent contract for the step
     * @param config   LLM configuration
     * @return The heal decision from the LLM
     */
    HealDecision evaluateCandidates(
            FailureContext failure,
            UiSnapshot snapshot,
            IntentContract intent,
            LlmConfig config);

    /**
     * Validate outcome using LLM reasoning.
     *
     * @param expectedOutcome Description of expected outcome
     * @param before          UI snapshot before action
     * @param after           UI snapshot after action
     * @param config          LLM configuration
     * @return Result of outcome validation
     */
    OutcomeResult validateOutcome(
            String expectedOutcome,
            UiSnapshot before,
            UiSnapshot after,
            LlmConfig config);

    /**
     * Get the name of this provider.
     */
    String getProviderName();

    /**
     * Alias for getProviderName() for compatibility.
     */
    default String getName() {
        return getProviderName();
    }

    /**
     * Check if the provider is available and properly configured.
     */
    boolean isAvailable();

    /**
     * Check if the provider is available with the given configuration.
     * This allows checking config values (like base_url) before falling back to env vars.
     */
    default boolean isAvailable(LlmConfig config) {
        return isAvailable();
    }

    /**
     * Check if this provider supports vision/multimodal inputs.
     * Providers that support vision can analyze screenshots alongside DOM data.
     *
     * @return true if the provider supports vision capabilities
     */
    default boolean supportsVision() {
        return false;
    }

    /**
     * Check if a specific model supports vision capabilities.
     * Some providers have both vision and non-vision models.
     *
     * @param model the model name to check
     * @return true if the model supports vision
     */
    default boolean isVisionModel(String model) {
        return false;
    }

    /**
     * Complete a request using the LLM.
     * Default implementation throws UnsupportedOperationException.
     */
    default LlmResponse complete(LlmRequest request) {
        throw new UnsupportedOperationException("Raw completion not supported by this provider");
    }

    /**
     * Get the maximum tokens supported by this provider.
     * Default returns 4000.
     */
    default int getMaxTokens() {
        return 4000;
    }

    /**
     * Get the cost per token for this provider.
     * Default returns 0 (no cost tracking).
     */
    default double getCostPerToken() {
        return 0.0;
    }
}
