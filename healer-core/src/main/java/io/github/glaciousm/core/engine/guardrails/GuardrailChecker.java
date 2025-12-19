package io.github.glaciousm.core.engine.guardrails;

import io.github.glaciousm.core.config.GuardrailConfig;
import io.github.glaciousm.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Checks guardrails before and after LLM evaluation.
 * Guardrails prevent healing of dangerous or inappropriate actions.
 */
public class GuardrailChecker {

    private static final Logger logger = LoggerFactory.getLogger(GuardrailChecker.class);

    private final GuardrailConfig config;

    public GuardrailChecker(GuardrailConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Pre-LLM guardrails - checked before even asking the LLM.
     */
    public GuardrailResult checkPreLlm(FailureContext failure, IntentContract intent) {
        // Never heal assertion steps
        if (failure.isAssertionStep() || "Then".equalsIgnoreCase(failure.getStepKeyword())) {
            logger.info("Guardrail: Refusing to heal assertion step");
            return GuardrailResult.assertionStep();
        }

        // Never heal if policy is OFF
        if (intent.getPolicy() == HealPolicy.OFF) {
            logger.info("Guardrail: Healing disabled for this intent");
            return GuardrailResult.refuse(GuardrailResult.GuardrailType.POLICY_OFF,
                    "Healing disabled for this intent");
        }

        // Check if action type is destructive and not allowed
        if (isDestructiveAction(failure.getActionType())) {
            if (!intent.isDestructiveAllowed()) {
                logger.info("Guardrail: Refusing destructive action");
                return GuardrailResult.destructiveAction(failure.getActionType().name());
            }
        }

        // Check step text for forbidden keywords (pre-filter)
        String stepText = failure.getStepText();
        if (stepText != null && config.containsForbiddenKeyword(stepText)) {
            // This is just a warning, not a refusal - LLM will make final decision
            logger.warn("Step text contains potentially forbidden keyword");
        }

        return GuardrailResult.proceed();
    }

    /**
     * Post-LLM guardrails - validate the LLM's choice before executing.
     */
    public GuardrailResult checkPostLlm(HealDecision decision, ElementSnapshot chosenElement, UiSnapshot snapshot) {
        // Check confidence threshold
        if (decision.getConfidence() < config.getMinConfidence()) {
            logger.info("Guardrail: Confidence {:.2f} below threshold {:.2f}",
                    decision.getConfidence(), config.getMinConfidence());
            return GuardrailResult.lowConfidence(decision.getConfidence(), config.getMinConfidence());
        }

        // Check for forbidden keywords in chosen element
        if (chosenElement != null) {
            String elementText = chosenElement.getNormalizedText();
            if (config.containsForbiddenKeyword(elementText)) {
                for (String keyword : config.getForbiddenKeywords()) {
                    if (elementText.toLowerCase().contains(keyword.toLowerCase())) {
                        logger.info("Guardrail: Element contains forbidden keyword: {}", keyword);
                        return GuardrailResult.forbiddenKeyword(keyword);
                    }
                }
            }

            // Check aria-label and title for forbidden keywords
            if (chosenElement.getAriaLabel() != null &&
                config.containsForbiddenKeyword(chosenElement.getAriaLabel())) {
                logger.info("Guardrail: Element aria-label contains forbidden keyword");
                return GuardrailResult.forbiddenKeyword("aria-label content");
            }

            // Check element is actually interactable
            if (!chosenElement.isVisible()) {
                logger.info("Guardrail: Element is not visible");
                return GuardrailResult.refuse(GuardrailResult.GuardrailType.NOT_INTERACTABLE,
                        "Chosen element is not visible");
            }

            if (!chosenElement.isEnabled()) {
                logger.info("Guardrail: Element is not enabled");
                return GuardrailResult.refuse(GuardrailResult.GuardrailType.NOT_INTERACTABLE,
                        "Chosen element is not enabled");
            }
        }

        return GuardrailResult.proceed();
    }

    /**
     * Check if an action type is potentially destructive.
     */
    private boolean isDestructiveAction(ActionType actionType) {
        // Most actions are not inherently destructive
        // The destructiveness comes from what element is being acted upon
        return false;
    }

    /**
     * Check URL against forbidden patterns.
     */
    public GuardrailResult checkUrl(String currentUrl) {
        for (String pattern : config.getForbiddenUrlPatterns()) {
            if (currentUrl.matches(pattern)) {
                logger.info("Guardrail: URL matches forbidden pattern: {}", pattern);
                return GuardrailResult.refuse(GuardrailResult.GuardrailType.GENERAL,
                        "Current URL matches forbidden pattern");
            }
        }
        return GuardrailResult.proceed();
    }
}
