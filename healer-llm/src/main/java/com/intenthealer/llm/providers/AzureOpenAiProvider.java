package com.intenthealer.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/**
 * LLM provider implementation for Azure OpenAI Service.
 * Uses Azure-specific endpoints and authentication.
 */
public class AzureOpenAiProvider implements LlmProvider {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiProvider.class);

    private static final String DEFAULT_API_VERSION = "2024-02-15-preview";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public AzureOpenAiProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.promptBuilder = new PromptBuilder();
        this.responseParser = new ResponseParser();
    }

    @Override
    public String getProviderName() {
        return "azure";
    }

    @Override
    public boolean isAvailable() {
        String endpoint = getEndpoint(null);
        String apiKey = getApiKey(null);
        return endpoint != null && !endpoint.isEmpty() && apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public HealDecision evaluateCandidates(
            FailureContext failure,
            UiSnapshot snapshot,
            IntentContract intent,
            LlmConfig config) {

        long startTime = System.currentTimeMillis();

        try {
            String prompt = promptBuilder.buildEvaluationPrompt(failure, snapshot, intent);
            String systemPrompt = promptBuilder.buildSystemPrompt();

            AzureResponse response = callAzure(systemPrompt, prompt, config);

            HealDecision decision = responseParser.parseHealDecision(response.content);
            decision.setLlmLatencyMs(System.currentTimeMillis() - startTime);
            decision.setPromptTokens(response.promptTokens);
            decision.setCompletionTokens(response.completionTokens);
            decision.setLlmCostUsd(calculateCost(response.promptTokens, response.completionTokens, config));

            return decision;

        } catch (IOException e) {
            throw new LlmException("Azure OpenAI connection error: " + e.getMessage(),
                    getProviderName(), getDeployment(config), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request interrupted", getProviderName(), getDeployment(config), e);
        }
    }

    @Override
    public OutcomeResult validateOutcome(
            String expectedOutcome,
            UiSnapshot before,
            UiSnapshot after,
            LlmConfig config) {

        try {
            String prompt = promptBuilder.buildOutcomeValidationPrompt(expectedOutcome, before, after);
            String systemPrompt = "You are a test outcome validator. Determine if the expected outcome was achieved.";

            AzureResponse response = callAzure(systemPrompt, prompt, config);

            return responseParser.parseOutcomeResult(response.content);

        } catch (IOException e) {
            throw new LlmException("Azure OpenAI connection error: " + e.getMessage(),
                    getProviderName(), getDeployment(config), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request interrupted", getProviderName(), getDeployment(config), e);
        }
    }

    private AzureResponse callAzure(String systemPrompt, String userPrompt, LlmConfig config)
            throws IOException, InterruptedException {

        String endpoint = getEndpoint(config);
        String apiKey = getApiKey(config);
        String deployment = getDeployment(config);
        String apiVersion = getApiVersion(config);

        // Build URL
        String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                endpoint.replaceAll("/$", ""), deployment, apiVersion);

        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();

        ArrayNode messages = requestBody.putArray("messages");

        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        double temperature = config != null && config.getTemperature() > 0 ? config.getTemperature() : 0.1;
        requestBody.put("temperature", temperature);

        if (config != null && config.getMaxTokensPerRequest() > 0) {
            requestBody.put("max_tokens", config.getMaxTokensPerRequest());
        }

        String requestJson = objectMapper.writeValueAsString(requestBody);
        int timeoutSeconds = config != null && config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 30;

        logger.debug("Azure OpenAI request to deployment: {}", deployment);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("api-key", apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            logger.error("Azure OpenAI API error: {} - {}", httpResponse.statusCode(), httpResponse.body());
            throw new LlmException("Azure OpenAI API error: " + httpResponse.statusCode(),
                    getProviderName(), deployment);
        }

        JsonNode responseJson = objectMapper.readTree(httpResponse.body());

        AzureResponse response = new AzureResponse();

        // Extract content
        JsonNode choices = responseJson.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).get("message");
            if (message != null) {
                response.content = message.get("content").asText();
            }
        }

        // Extract usage
        JsonNode usage = responseJson.get("usage");
        if (usage != null) {
            response.promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            response.completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
        }

        return response;
    }

    private String getEndpoint(LlmConfig config) {
        if (config != null && config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
            return config.getEndpoint();
        }
        // Try environment variables
        String endpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
        if (endpoint != null && !endpoint.isEmpty()) {
            return endpoint;
        }
        return System.getenv("AZURE_OPENAI_BASE_URL");
    }

    private String getApiKey(LlmConfig config) {
        if (config != null && config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            return config.getApiKey();
        }
        return System.getenv("AZURE_OPENAI_API_KEY");
    }

    private String getDeployment(LlmConfig config) {
        if (config != null && config.getModel() != null && !config.getModel().isEmpty()) {
            return config.getModel();
        }
        String deployment = System.getenv("AZURE_OPENAI_DEPLOYMENT");
        return deployment != null ? deployment : "gpt-4";
    }

    private String getApiVersion(LlmConfig config) {
        // Could be made configurable
        String version = System.getenv("AZURE_OPENAI_API_VERSION");
        return version != null ? version : DEFAULT_API_VERSION;
    }

    private double calculateCost(int promptTokens, int completionTokens, LlmConfig config) {
        // Azure pricing varies by region and deployment
        // These are approximate costs for GPT-4
        String deployment = getDeployment(config);

        double inputCostPer1k;
        double outputCostPer1k;

        if (deployment.contains("gpt-4-32k")) {
            inputCostPer1k = 0.06;
            outputCostPer1k = 0.12;
        } else if (deployment.contains("gpt-4")) {
            inputCostPer1k = 0.03;
            outputCostPer1k = 0.06;
        } else if (deployment.contains("gpt-35-turbo-16k") || deployment.contains("gpt-3.5-turbo-16k")) {
            inputCostPer1k = 0.003;
            outputCostPer1k = 0.004;
        } else {
            // Default to GPT-3.5-turbo pricing
            inputCostPer1k = 0.0015;
            outputCostPer1k = 0.002;
        }

        double inputCost = (promptTokens / 1000.0) * inputCostPer1k;
        double outputCost = (completionTokens / 1000.0) * outputCostPer1k;

        return inputCost + outputCost;
    }

    private static class AzureResponse {
        String content;
        int promptTokens;
        int completionTokens;
    }
}
