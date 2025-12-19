package io.github.glaciousm.core.exception;

/**
 * Exception thrown when configuration loading or validation fails.
 */
public class ConfigurationException extends HealingException {

    private final String configPath;

    public ConfigurationException(String message) {
        super(message, HealingFailureReason.CONFIGURATION_ERROR);
        this.configPath = null;
    }

    public ConfigurationException(String message, String configPath) {
        super(message, HealingFailureReason.CONFIGURATION_ERROR);
        this.configPath = configPath;
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause, HealingFailureReason.CONFIGURATION_ERROR);
        this.configPath = null;
    }

    public ConfigurationException(String message, Throwable cause, String configPath) {
        super(message, cause, HealingFailureReason.CONFIGURATION_ERROR);
        this.configPath = configPath;
    }

    public String getConfigPath() {
        return configPath;
    }

    /**
     * Creates an exception for file loading failure.
     */
    public static ConfigurationException loadFailed(String path, Throwable cause) {
        return new ConfigurationException(
                "Failed to load configuration from " + path, cause, path);
    }

    /**
     * Creates an exception for stream loading failure.
     */
    public static ConfigurationException streamLoadFailed(Throwable cause) {
        return new ConfigurationException(
                "Failed to load configuration from stream", cause);
    }

    /**
     * Creates an exception for validation failure.
     */
    public static ConfigurationException validationFailed(String message) {
        return new ConfigurationException("Configuration validation failed: " + message);
    }

    /**
     * Creates an exception for missing required property.
     */
    public static ConfigurationException missingProperty(String propertyName) {
        return new ConfigurationException("Missing required configuration property: " + propertyName);
    }
}
