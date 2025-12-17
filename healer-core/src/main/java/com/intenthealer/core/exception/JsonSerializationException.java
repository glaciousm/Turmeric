package com.intenthealer.core.exception;

/**
 * Exception thrown when JSON serialization or deserialization fails.
 */
public class JsonSerializationException extends HealingException {

    public JsonSerializationException(String message) {
        super(message);
    }

    public JsonSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for serialization failure.
     */
    public static JsonSerializationException serializationFailed(Object object, Throwable cause) {
        String typeName = object != null ? object.getClass().getSimpleName() : "null";
        return new JsonSerializationException(
                "Failed to serialize " + typeName + " to JSON", cause);
    }

    /**
     * Creates an exception for deserialization failure.
     */
    public static JsonSerializationException deserializationFailed(Class<?> targetType, Throwable cause) {
        return new JsonSerializationException(
                "Failed to deserialize JSON to " + targetType.getSimpleName(), cause);
    }

    /**
     * Creates an exception for parsing failure.
     */
    public static JsonSerializationException parseFailed(Throwable cause) {
        return new JsonSerializationException("Failed to parse JSON", cause);
    }
}
