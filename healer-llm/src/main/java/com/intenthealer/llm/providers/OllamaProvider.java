package com.intenthealer.llm.providers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intenthealer.core.config.LlmConfig;
import com.intenthealer.core.exception.LlmException;
import com.intenthealer.core.model.*;
import com.intenthealer.llm.LlmProvider;
import com.intenthealer.llm.PromptBuilder;
import com.intenthealer.llm.ResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM provider implementation for Ollama (local models).
 * Supports running models locally without API keys.
 */
public class OllamaProvider implements LlmProvider {

    private static final Logger logger = LoggerFactory.getLogger(OllamaProvider.class);

    /**
     * Default Ollama endpoint.
     */
    public static final String DEFAULT_ENDPOINT = "http://localhost:11434";

    /**
     * Default model.
     */
    public static final String DEFAULT_MODEL = "llama3.1";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public OllamaProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.promptBuilder = new PromptBuilder();
        this.responseParser = new ResponseParser();
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() {
        try {
            String endpoint = getEndpoint(null);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public HealDecision evaluateCandidates(
            FailureContext failure,
            UiSnapshot snapshot,
            IntentContract intent,
            LlmConfig config) {

        long startTime = System.currentTimeMillis();
        String endpoint = getEndpoint(config);
        String model = getModel(config);

        try {
            // Build prompt
            String prompt = promptBuilder.buildEvaluationPrompt(failure, snapshot, intent);
            String systemPrompt = promptBuilder.buildSystemPrompt();

            // Make API call
            OllamaResponse response = callOllama(endpoint, model, prompt, systemPrompt, config);

            // Parse response
            HealDecision decision = responseParser.parseHealDecision(response.response);
            decision.setLlmLatencyMs(System.currentTimeMillis() - startTime);
            decision.setPromptTokens(response.promptEvalCount != null ? response.promptEvalCount : 0);
            decision.setCompletionTokens(response.evalCount != null ? response.evalCount : 0);
            decision.setLlmCostUsd(0.0); // Local models are free

            return decision;

        } catch (IOException e) {
            throw new LlmException("Ollama connection error: " + e.getMessage(), getProviderName(), model, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request interrupted", getProviderName(), model, e);
        }
    }

    @Override
    public OutcomeResult validateOutcome(
            String expectedOutcome,
            UiSnapshot before,
            UiSnapshot after,
            LlmConfig config) {

        String endpoint = getEndpoint(config);
        String model = getModel(config);

        try {
            String prompt = promptBuilder.buildOutcomeValidationPrompt(expectedOutcome, before, after);
            String systemPrompt = "You are a test outcome validator. Determine if the expected outcome was achieved.";

            OllamaResponse response = callOllama(endpoint, model, prompt, systemPrompt, config);

            return responseParser.parseOutcomeResult(response.response);

        } catch (IOException e) {
            throw new LlmException("Ollama connection error: " + e.getMessage(), getProviderName(), model, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request interrupted", getProviderName(), model, e);
        }
    }

    private OllamaResponse callOllama(
            String endpoint,
            String model,
            String prompt,
            String systemPrompt,
            LlmConfig config) throws IOException, InterruptedException {

        OllamaRequest request = new OllamaRequest();
        request.model = model;
        request.prompt = prompt;
        request.system = systemPrompt;
        request.stream = false;
        request.options = new OllamaOptions();
        request.options.temperature = config != null && config.getTemperature() > 0
                ? config.getTemperature() : 0.1;

        String requestBody = objectMapper.writeValueAsString(request);
        int timeoutSeconds = config != null && config.getTimeoutSeconds() > 0
                ? config.getTimeoutSeconds() : 60;

        logger.debug("Ollama request to {}: model={}", endpoint, model);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            logger.error("Ollama API error: {} - {}", httpResponse.statusCode(), httpResponse.body());
            throw new LlmException("Ollama API error: " + httpResponse.statusCode(),
                    getProviderName(), model);
        }

        return objectMapper.readValue(httpResponse.body(), OllamaResponse.class);
    }

    /**
     * List available models on the Ollama server.
     */
    public List<String> listModels(String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode models = root.get("models");
                if (models != null && models.isArray()) {
                    return java.util.stream.StreamSupport.stream(models.spliterator(), false)
                            .map(m -> m.get("name").asText())
                            .toList();
                }
            }
            return List.of();
        } catch (Exception e) {
            logger.error("Error listing models: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Pull a model from Ollama library.
     */
    public boolean pullModel(String endpoint, String modelName) {
        try {
            Map<String, Object> body = Map.of("name", modelName, "stream", false);
            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/pull"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            logger.info("Pulling model {}...", modelName);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.error("Error pulling model: {}", e.getMessage());
            return false;
        }
    }

    private String getEndpoint(LlmConfig config) {
        if (config != null && config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
            return config.getEndpoint();
        }
        // Check environment variable
        String envEndpoint = System.getenv("OLLAMA_HOST");
        if (envEndpoint != null && !envEndpoint.isEmpty()) {
            return envEndpoint;
        }
        return DEFAULT_ENDPOINT;
    }

    private String getModel(LlmConfig config) {
        if (config != null && config.getModel() != null && !config.getModel().isEmpty()) {
            return config.getModel();
        }
        // Check environment variable
        String envModel = System.getenv("OLLAMA_MODEL");
        if (envModel != null && !envModel.isEmpty()) {
            return envModel;
        }
        return DEFAULT_MODEL;
    }

    // Request/Response DTOs

    private static class OllamaRequest {
        public String model;
        public String prompt;
        public String system;
        public boolean stream;
        public OllamaOptions options;
    }

    private static class OllamaOptions {
        public double temperature;
    }

    private static class OllamaResponse {
        public String model;
        public String response;
        public boolean done;

        @JsonProperty("prompt_eval_count")
        public Integer promptEvalCount;

        @JsonProperty("eval_count")
        public Integer evalCount;
    }
}
