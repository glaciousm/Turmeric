package io.github.glaciousm.core.model;

import java.util.Objects;

/**
 * Result of an outcome check after action execution.
 */
public final class OutcomeResult {
    private final boolean passed;
    private final String message;
    private final double confidence;

    private OutcomeResult(boolean passed, String message, double confidence) {
        this.passed = passed;
        this.message = message;
        this.confidence = confidence;
    }

    public boolean isPassed() {
        return passed;
    }

    /**
     * Alias for isPassed() for compatibility.
     */
    public boolean isValid() {
        return passed;
    }

    public boolean isFailed() {
        return !passed;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Alias for getMessage() for compatibility.
     */
    public String getReasoning() {
        return message;
    }

    public double getConfidence() {
        return confidence;
    }

    /**
     * Creates a passed result with the given message.
     */
    public static OutcomeResult passed(String message) {
        return new OutcomeResult(true, message, 1.0);
    }

    /**
     * Creates a passed result with confidence level.
     */
    public static OutcomeResult passed(String message, double confidence) {
        return new OutcomeResult(true, message, confidence);
    }

    /**
     * Creates a failed result with the given message.
     */
    public static OutcomeResult failed(String message) {
        return new OutcomeResult(false, message, 1.0);
    }

    /**
     * Creates a failed result with formatted message.
     */
    public static OutcomeResult failed(String format, Object... args) {
        return new OutcomeResult(false, String.format(format, args), 1.0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutcomeResult that = (OutcomeResult) o;
        return passed == that.passed && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passed, message);
    }

    @Override
    public String toString() {
        return "OutcomeResult{passed=" + passed + ", message='" + message + "'}";
    }
}
