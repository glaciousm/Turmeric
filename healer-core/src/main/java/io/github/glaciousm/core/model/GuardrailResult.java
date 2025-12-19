package io.github.glaciousm.core.model;

import java.util.Objects;

/**
 * Result of a guardrail check.
 * Guardrails determine if healing should proceed or be refused.
 */
public final class GuardrailResult {
    private final boolean proceed;
    private final String reason;
    private final GuardrailType type;

    private GuardrailResult(boolean proceed, String reason, GuardrailType type) {
        this.proceed = proceed;
        this.reason = reason;
        this.type = type;
    }

    /**
     * Whether healing can proceed.
     */
    public boolean canProceed() {
        return proceed;
    }

    /**
     * Whether healing was refused.
     */
    public boolean isRefused() {
        return !proceed;
    }

    /**
     * The reason for refusal (if refused).
     */
    public String getReason() {
        return reason;
    }

    /**
     * The type of guardrail that triggered (if refused).
     */
    public GuardrailType getType() {
        return type;
    }

    /**
     * Creates a result allowing healing to proceed.
     */
    public static GuardrailResult proceed() {
        return new GuardrailResult(true, null, null);
    }

    /**
     * Creates a result refusing healing with a reason.
     */
    public static GuardrailResult refuse(String reason) {
        return new GuardrailResult(false, reason, GuardrailType.GENERAL);
    }

    /**
     * Creates a result refusing healing with formatted reason.
     */
    public static GuardrailResult refuse(String format, Object... args) {
        return new GuardrailResult(false, String.format(format, args), GuardrailType.GENERAL);
    }

    /**
     * Creates a result refusing due to a specific guardrail type.
     */
    public static GuardrailResult refuse(GuardrailType type, String reason) {
        return new GuardrailResult(false, reason, type);
    }

    /**
     * Creates a result refusing due to low confidence.
     */
    public static GuardrailResult lowConfidence(double confidence, double threshold) {
        return new GuardrailResult(false,
                String.format("Confidence %.2f below threshold %.2f", confidence, threshold),
                GuardrailType.LOW_CONFIDENCE);
    }

    /**
     * Creates a result refusing due to destructive action.
     */
    public static GuardrailResult destructiveAction(String action) {
        return new GuardrailResult(false,
                "Destructive action not allowed: " + action,
                GuardrailType.DESTRUCTIVE_ACTION);
    }

    /**
     * Creates a result refusing due to forbidden keyword.
     */
    public static GuardrailResult forbiddenKeyword(String keyword) {
        return new GuardrailResult(false,
                "Chosen element contains forbidden keyword: " + keyword,
                GuardrailType.FORBIDDEN_KEYWORD);
    }

    /**
     * Creates a result refusing due to assertion step.
     */
    public static GuardrailResult assertionStep() {
        return new GuardrailResult(false,
                "Assertion steps cannot be healed",
                GuardrailType.ASSERTION_STEP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuardrailResult that = (GuardrailResult) o;
        return proceed == that.proceed && Objects.equals(reason, that.reason) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(proceed, reason, type);
    }

    @Override
    public String toString() {
        if (proceed) {
            return "GuardrailResult{proceed=true}";
        }
        return "GuardrailResult{proceed=false, type=" + type + ", reason='" + reason + "'}";
    }

    /**
     * Types of guardrails that can block healing.
     */
    public enum GuardrailType {
        GENERAL,
        LOW_CONFIDENCE,
        DESTRUCTIVE_ACTION,
        FORBIDDEN_KEYWORD,
        ASSERTION_STEP,
        POLICY_OFF,
        NOT_INTERACTABLE,
        COST_LIMIT,
        CIRCUIT_BREAKER
    }
}
