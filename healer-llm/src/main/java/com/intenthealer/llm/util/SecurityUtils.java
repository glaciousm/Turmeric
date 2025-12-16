package com.intenthealer.llm.util;

/**
 * Security utilities for protecting sensitive information.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Sanitize an error message to remove potential API keys or secrets.
     */
    public static String sanitizeErrorMessage(String message) {
        if (message == null) return null;

        // Mask potential API keys (various formats)
        // OpenAI: sk-...
        // Anthropic: sk-ant-...
        // Generic: Bearer tokens, API keys
        String sanitized = message;

        // OpenAI API keys
        sanitized = sanitized.replaceAll("sk-[a-zA-Z0-9]{20,}", "sk-***REDACTED***");

        // Anthropic API keys
        sanitized = sanitized.replaceAll("sk-ant-[a-zA-Z0-9-]{20,}", "sk-ant-***REDACTED***");

        // Generic API key patterns
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key[\"']?\\s*[:=]\\s*[\"']?)[a-zA-Z0-9-_]{16,}", "$1***REDACTED***");

        // Bearer tokens
        sanitized = sanitized.replaceAll("(?i)(Bearer\\s+)[a-zA-Z0-9-_.]{20,}", "$1***REDACTED***");

        // Authorization headers
        sanitized = sanitized.replaceAll("(?i)(Authorization[\"']?\\s*[:=]\\s*[\"']?)[^\"'\\s]{20,}", "$1***REDACTED***");

        return sanitized;
    }

    /**
     * Mask an API key for safe logging (show first 4 and last 4 chars).
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 12) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
