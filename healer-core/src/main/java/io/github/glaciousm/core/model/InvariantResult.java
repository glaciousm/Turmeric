package io.github.glaciousm.core.model;

import java.util.Objects;

/**
 * Result of an invariant check.
 */
public final class InvariantResult {
    private final boolean satisfied;
    private final String message;
    private final String violationDetails;

    private InvariantResult(boolean satisfied, String message, String violationDetails) {
        this.satisfied = satisfied;
        this.message = message;
        this.violationDetails = violationDetails;
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public boolean isViolated() {
        return !satisfied;
    }

    public String getMessage() {
        return message;
    }

    public String getViolationDetails() {
        return violationDetails;
    }

    /**
     * Creates a satisfied result.
     */
    public static InvariantResult satisfied() {
        return new InvariantResult(true, "Invariant satisfied", null);
    }

    /**
     * Creates a satisfied result with message.
     */
    public static InvariantResult satisfied(String message) {
        return new InvariantResult(true, message, null);
    }

    /**
     * Creates a violated result with message.
     */
    public static InvariantResult violated(String message) {
        return new InvariantResult(false, message, null);
    }

    /**
     * Creates a violated result with formatted message.
     */
    public static InvariantResult violated(String format, Object... args) {
        return new InvariantResult(false, String.format(format, args), null);
    }

    /**
     * Creates a violated result with details.
     */
    public static InvariantResult violated(String message, String details) {
        return new InvariantResult(false, message, details);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvariantResult that = (InvariantResult) o;
        return satisfied == that.satisfied && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(satisfied, message);
    }

    @Override
    public String toString() {
        return "InvariantResult{satisfied=" + satisfied + ", message='" + message + "'}";
    }
}
