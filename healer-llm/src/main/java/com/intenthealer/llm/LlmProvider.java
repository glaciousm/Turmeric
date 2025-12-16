package com.intenthealer.llm;

import com.intenthealer.core.config.LlmConfig;
import com.intenthealer.core.model.*;

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
