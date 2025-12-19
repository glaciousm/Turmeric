package io.github.glaciousm.core.exception;

import io.github.glaciousm.core.model.GuardrailResult;

/**
 * Exception thrown when a guardrail blocks a healing attempt.
 */
public class GuardrailViolationException extends HealingException {

    private final GuardrailResult.GuardrailType guardrailType;

    public GuardrailViolationException(String message) {
        super(message, HealingFailureReason.GUARDRAIL_VIOLATION);
        this.guardrailType = GuardrailResult.GuardrailType.GENERAL;
    }

    public GuardrailViolationException(String message, GuardrailResult.GuardrailType guardrailType) {
        super(message, HealingFailureReason.GUARDRAIL_VIOLATION);
        this.guardrailType = guardrailType;
    }

    public GuardrailViolationException(GuardrailResult result) {
        super(result.getReason(), HealingFailureReason.GUARDRAIL_VIOLATION);
        this.guardrailType = result.getType();
    }

    public GuardrailResult.GuardrailType getGuardrailType() {
        return guardrailType;
    }

    /**
     * Creates an exception for low confidence.
     */
    public static GuardrailViolationException lowConfidence(double confidence, double threshold) {
        return new GuardrailViolationException(
                String.format("Confidence %.2f below threshold %.2f", confidence, threshold),
                GuardrailResult.GuardrailType.LOW_CONFIDENCE);
    }

    /**
     * Creates an exception for forbidden keyword.
     */
    public static GuardrailViolationException forbiddenKeyword(String keyword) {
        return new GuardrailViolationException(
                "Element contains forbidden keyword: " + keyword,
                GuardrailResult.GuardrailType.FORBIDDEN_KEYWORD);
    }

    /**
     * Creates an exception for destructive action.
     */
    public static GuardrailViolationException destructiveAction(String action) {
        return new GuardrailViolationException(
                "Destructive action not allowed: " + action,
                GuardrailResult.GuardrailType.DESTRUCTIVE_ACTION);
    }

    /**
     * Creates an exception for assertion step.
     */
    public static GuardrailViolationException assertionStep() {
        return new GuardrailViolationException(
                "Assertion steps cannot be healed",
                GuardrailResult.GuardrailType.ASSERTION_STEP);
    }

    @Override
    public String toString() {
        return "GuardrailViolationException{type=" + guardrailType +
               ", message='" + getMessage() + "'}";
    }
}
