package io.github.glaciousm.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.glaciousm.core.config.LlmConfig;
import io.github.glaciousm.core.exception.LlmException;
import io.github.glaciousm.core.model.*;
import io.github.glaciousm.llm.LlmProvider;
import io.github.glaciousm.llm.PromptBuilder;
import io.github.glaciousm.llm.ResponseParser;
import io.github.glaciousm.llm.util.HttpClientFactory;
import io.github.glaciousm.llm.util.SecurityUtils;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Anthropic Claude LLM provider implementation.
 */
public class AnthropicProvider implements LlmProvider {

    private static final Logger logger = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String API_VERSION = "2023-06-01";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final ResponseParser responseParser = new ResponseParser();

    @Override
    public HealDecision evaluateCandidates(
            FailureContext failure,
            UiSnapshot snapshot,
            IntentContract intent,
            LlmConfig config) {

        String apiKey = getApiKey(config);
        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        logger.debug("Calling Anthropic with model: {}", config.getModel());

        String response = callApi(prompt, config, apiKey);
        return responseParser.parseHealDecision(response, getProviderName(), config.getModel());
    }

    @Override
    public OutcomeResult validateOutcome(
            String expectedOutcome,
            UiSnapshot before,
            UiSnapshot after,
            LlmConfig config) {

        String apiKey = getApiKey(config);
        String prompt = promptBuilder.buildOutcomeValidationPrompt(expectedOutcome, before, after);

        String response = callApi(prompt, config, apiKey);
        return responseParser.parseOutcomeResult(response, getProviderName(), config.getModel());
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public boolean isAvailable() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        return apiKey != null && !apiKey.isEmpty();
    }

    private String callApi(String prompt, LlmConfig config, String apiKey) {
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL;
        String url = baseUrl + "/messages";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getModel());
        requestBody.put("max_tokens", config.getMaxTokensPerRequest());

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", API_VERSION)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

        int retries = 0;
        int maxRetries = config.getMaxRetries();
        Exception lastException = null;

        // Get a cached client with the configured timeout
        OkHttpClient client = HttpClientFactory.getClientWithReadTimeout(config.getTimeoutSeconds());

        while (retries <= maxRetries) {
            try {
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "unknown";
                        int statusCode = response.code();
                        // Retry on server errors (5xx), throw immediately on client errors (4xx)
                        if (statusCode >= 500) {
                            throw new IOException("Server error: " + statusCode + " - " + errorBody);
                        }
                        throw new LlmException(SecurityUtils.sanitizeErrorMessage("Anthropic API error: " + statusCode + " - " + errorBody),
                                getProviderName(), config.getModel());
                    }

                    String responseBody = response.body().string();
                    return extractContentFromResponse(responseBody);
                }
            } catch (IOException e) {
                lastException = e;
                retries++;
                if (retries <= maxRetries) {
                    logger.warn("Anthropic request failed, retrying ({}/{}): {}", retries, maxRetries, SecurityUtils.sanitizeErrorMessage(e.getMessage()));
                    try {
                        Thread.sleep(1000L * retries);  // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw LlmException.unavailable(getProviderName(), config.getModel(), lastException);
    }

    private String extractContentFromResponse(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode content = json.get("content");
            if (content != null && content.isArray() && content.size() > 0) {
                JsonNode firstContent = content.get(0);
                if (firstContent.has("text")) {
                    return firstContent.get("text").asText();
                }
            }
            throw new LlmException("Invalid Anthropic response structure",
                    getProviderName(), "unknown");
        } catch (IOException e) {
            throw new LlmException("Failed to parse Anthropic response", e,
                    getProviderName(), "unknown");
        }
    }

    private String getApiKey(LlmConfig config) {
        // Try config-specified env var, fall back to ANTHROPIC_API_KEY
        String envVar = "ANTHROPIC_API_KEY";
        if (config.getApiKeyEnv() != null && config.getApiKeyEnv().contains("ANTHROPIC")) {
            envVar = config.getApiKeyEnv();
        }

        // Check environment variable first, then system property (for tests)
        String apiKey = System.getenv(envVar);
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getProperty(envVar);
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new LlmException("API key not found. Set " + envVar + " environment variable.",
                    getProviderName(), config.getModel());
        }
        return apiKey;
    }
}
