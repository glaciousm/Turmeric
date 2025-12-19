package io.github.glaciousm.core.exception;

/**
 * Exception for LLM-related errors.
 */
public class LlmException extends HealingException {

    private final String provider;
    private final String model;

    public LlmException(String message, String provider, String model) {
        super(message, HealingFailureReason.LLM_UNAVAILABLE);
        this.provider = provider;
        this.model = model;
    }

    public LlmException(String message, Throwable cause, String provider, String model) {
        super(message, cause, HealingFailureReason.LLM_UNAVAILABLE);
        this.provider = provider;
        this.model = model;
    }

    public LlmException(String message, Throwable cause, String provider, String model, HealingFailureReason reason) {
        super(message, cause, reason);
        this.provider = provider;
        this.model = model;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    /**
     * Creates an exception for unavailable provider.
     */
    public static LlmException unavailable(String provider, String model, Throwable cause) {
        String causeMsg = cause != null && cause.getMessage() != null ? ": " + cause.getMessage() : "";
        return new LlmException(
                "LLM provider unavailable: " + provider + "/" + model + causeMsg,
                cause, provider, model, HealingFailureReason.LLM_UNAVAILABLE);
    }

    /**
     * Creates an exception for invalid response.
     */
    public static LlmException invalidResponse(String provider, String model, String details) {
        return new LlmException(
                "Invalid LLM response from " + provider + "/" + model + ": " + details,
                provider, model);
    }

    /**
     * Creates an exception for timeout.
     */
    public static LlmException timeout(String provider, String model, int timeoutSeconds) {
        return new LlmException(
                "LLM request timed out after " + timeoutSeconds + "s: " + provider + "/" + model,
                provider, model);
    }

    /**
     * Creates an exception for connection error.
     */
    public static LlmException connectionError(String provider, String message) {
        return new LlmException(
                "Connection error for " + provider + ": " + message,
                provider, "unknown");
    }

    /**
     * Creates an exception for connection error with cause.
     */
    public static LlmException connectionError(String provider, Throwable cause) {
        return new LlmException(
                "Connection error for " + provider + ": " + cause.getMessage(),
                cause, provider, "unknown");
    }

    /**
     * Creates an exception for cryptographic operation failure.
     */
    public static LlmException cryptoError(String provider, String operation, Throwable cause) {
        return new LlmException(
                operation + " failed for " + provider,
                cause, provider, "unknown");
    }

    /**
     * Creates an exception for rate limiting (HTTP 429).
     */
    public static LlmException rateLimited(String provider, String model) {
        return new LlmException(
                "Rate limited by " + provider + "/" + model + ". Please wait before retrying.",
                provider, model);
    }

    /**
     * Creates an exception for rate limiting with retry-after hint.
     */
    public static LlmException rateLimited(String provider, String model, int retryAfterSeconds) {
        return new LlmException(
                "Rate limited by " + provider + "/" + model + ". Retry after " + retryAfterSeconds + " seconds.",
                provider, model);
    }

    /**
     * Checks if this exception indicates rate limiting.
     */
    public boolean isRateLimited() {
        String msg = getMessage();
        return msg != null && (msg.contains("Rate limited") || msg.contains("429") || msg.contains("rate limit"));
    }

    @Override
    public String toString() {
        return "LlmException{provider='" + provider + "', model='" + model +
               "', message='" + getMessage() + "'}";
    }
}
