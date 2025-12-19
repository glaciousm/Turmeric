package io.github.glaciousm.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.glaciousm.core.exception.JsonSerializationException;

import java.util.Optional;

/**
 * Utility class for JSON operations.
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER;
    private static final ObjectMapper PRETTY_MAPPER;

    static {
        MAPPER = createMapper();
        PRETTY_MAPPER = createMapper();
        PRETTY_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private JsonUtils() {
        // Utility class
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Get the shared ObjectMapper instance.
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * Serialize an object to JSON string.
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw JsonSerializationException.serializationFailed(value, e);
        }
    }

    /**
     * Serialize an object to pretty-printed JSON string.
     */
    public static String toPrettyJson(Object value) {
        try {
            return PRETTY_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw JsonSerializationException.serializationFailed(value, e);
        }
    }

    /**
     * Deserialize a JSON string to an object.
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw JsonSerializationException.deserializationFailed(type, e);
        }
    }

    /**
     * Try to deserialize a JSON string, returning empty on failure.
     */
    public static <T> Optional<T> tryFromJson(String json, Class<T> type) {
        try {
            return Optional.of(MAPPER.readValue(json, type));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Parse JSON string to JsonNode.
     */
    public static JsonNode parseJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw JsonSerializationException.parseFailed(e);
        }
    }

    /**
     * Try to parse JSON string, returning empty on failure.
     */
    public static Optional<JsonNode> tryParseJson(String json) {
        try {
            return Optional.of(MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Extract JSON from a string that may contain markdown code blocks.
     */
    public static String extractJsonFromMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Try to find JSON in markdown code blocks
        String trimmed = text.trim();

        // Check for ```json ... ``` or ``` ... ```
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            if (start == -1) return trimmed;

            int end = trimmed.lastIndexOf("```");
            if (end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }

        // Check if it starts with { or [
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        // Try to find JSON object in the text
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1);
        }

        return text;
    }

    /**
     * Checks if a string is valid JSON.
     */
    public static boolean isValidJson(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            MAPPER.readTree(text);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Deep copy an object via JSON serialization.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T object) {
        if (object == null) {
            return null;
        }
        String json = toJson(object);
        return (T) fromJson(json, object.getClass());
    }
}
