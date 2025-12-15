package com.intenthealer.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intenthealer.llm.LlmProvider;
import com.intenthealer.llm.LlmRequest;
import com.intenthealer.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Provider for custom fine-tuned models.
 *
 * Supports:
 * - OpenAI fine-tuned models
 * - HuggingFace models (local and API)
 * - Custom model endpoints
 * - ONNX/GGUF local models
 */
public class FineTunedModelProvider implements LlmProvider {

    private static final Logger logger = LoggerFactory.getLogger(FineTunedModelProvider.class);

    private final FineTunedConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelAdapter adapter;

    public FineTunedModelProvider(FineTunedConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        this.adapter = createAdapter(config);
    }

    @Override
    public String getName() {
        return "fine-tuned:" + config.modelId();
    }

    @Override
    public boolean isAvailable() {
        try {
            return adapter.healthCheck();
        } catch (Exception e) {
            logger.warn("Fine-tuned model health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        try {
            return adapter.complete(request);
        } catch (Exception e) {
            logger.error("Fine-tuned model completion failed", e);
            return LlmResponse.builder()
                    .success(false)
                    .errorMessage("Fine-tuned model error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public int getMaxTokens() {
        return config.maxTokens();
    }

    @Override
    public double getCostPerToken() {
        return config.costPerToken();
    }

    private ModelAdapter createAdapter(FineTunedConfig config) {
        return switch (config.modelType()) {
            case OPENAI_FINE_TUNED -> new OpenAIFineTunedAdapter(config, httpClient, objectMapper);
            case HUGGINGFACE_API -> new HuggingFaceApiAdapter(config, httpClient, objectMapper);
            case HUGGINGFACE_LOCAL -> new HuggingFaceLocalAdapter(config, objectMapper);
            case CUSTOM_ENDPOINT -> new CustomEndpointAdapter(config, httpClient, objectMapper);
            case ONNX_LOCAL -> new OnnxLocalAdapter(config);
        };
    }

    /**
     * Adapter interface for different model types.
     */
    interface ModelAdapter {
        boolean healthCheck() throws Exception;
        LlmResponse complete(LlmRequest request) throws Exception;
    }

    /**
     * Adapter for OpenAI fine-tuned models.
     */
    static class OpenAIFineTunedAdapter implements ModelAdapter {
        private final FineTunedConfig config;
        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        OpenAIFineTunedAdapter(FineTunedConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
            this.config = config;
            this.httpClient = httpClient;
            this.objectMapper = objectMapper;
        }

        @Override
        public boolean healthCheck() throws Exception {
            // Check if model exists
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/models/" + config.modelId()))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        }

        @Override
        public LlmResponse complete(LlmRequest request) throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.modelId());
            body.put("messages", List.of(
                    Map.of("role", "system", "content", request.getSystemPrompt()),
                    Map.of("role", "user", "content", request.getUserPrompt())
            ));
            body.put("temperature", request.getTemperature());
            body.put("max_tokens", request.getMaxTokens());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return LlmResponse.builder()
                        .success(false)
                        .errorMessage("OpenAI API error: " + response.statusCode())
                        .build();
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("choices").path(0).path("message").path("content").asText();
            int promptTokens = json.path("usage").path("prompt_tokens").asInt();
            int completionTokens = json.path("usage").path("completion_tokens").asInt();

            return LlmResponse.builder()
                    .success(true)
                    .content(content)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .model(config.modelId())
                    .build();
        }
    }

    /**
     * Adapter for HuggingFace Inference API.
     */
    static class HuggingFaceApiAdapter implements ModelAdapter {
        private final FineTunedConfig config;
        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        HuggingFaceApiAdapter(FineTunedConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
            this.config = config;
            this.httpClient = httpClient;
            this.objectMapper = objectMapper;
        }

        @Override
        public boolean healthCheck() throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api-inference.huggingface.co/models/" + config.modelId()))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        }

        @Override
        public LlmResponse complete(LlmRequest request) throws Exception {
            String prompt = config.promptTemplate()
                    .replace("{system}", request.getSystemPrompt())
                    .replace("{user}", request.getUserPrompt());

            Map<String, Object> body = new HashMap<>();
            body.put("inputs", prompt);
            body.put("parameters", Map.of(
                    "max_new_tokens", request.getMaxTokens(),
                    "temperature", request.getTemperature(),
                    "return_full_text", false
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api-inference.huggingface.co/models/" + config.modelId()))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return LlmResponse.builder()
                        .success(false)
                        .errorMessage("HuggingFace API error: " + response.statusCode())
                        .build();
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content;
            if (json.isArray()) {
                content = json.path(0).path("generated_text").asText();
            } else {
                content = json.path("generated_text").asText();
            }

            return LlmResponse.builder()
                    .success(true)
                    .content(content)
                    .model(config.modelId())
                    .build();
        }
    }

    /**
     * Adapter for local HuggingFace models via transformers server.
     */
    static class HuggingFaceLocalAdapter implements ModelAdapter {
        private final FineTunedConfig config;
        private final ObjectMapper objectMapper;

        HuggingFaceLocalAdapter(FineTunedConfig config, ObjectMapper objectMapper) {
            this.config = config;
            this.objectMapper = objectMapper;
        }

        @Override
        public boolean healthCheck() throws Exception {
            // Check if local server is running
            Path modelPath = Path.of(config.modelPath());
            return Files.exists(modelPath) || config.endpointUrl() != null;
        }

        @Override
        public LlmResponse complete(LlmRequest request) throws Exception {
            // This would typically call a local inference server like text-generation-inference
            // For now, return a placeholder indicating local setup needed
            if (config.endpointUrl() == null) {
                return LlmResponse.builder()
                        .success(false)
                        .errorMessage("Local HuggingFace model requires a running inference server. " +
                                "Start with: text-generation-launcher --model-id " + config.modelId())
                        .build();
            }

            // Call local endpoint
            HttpClient client = HttpClient.newHttpClient();
            String prompt = config.promptTemplate()
                    .replace("{system}", request.getSystemPrompt())
                    .replace("{user}", request.getUserPrompt());

            Map<String, Object> body = Map.of(
                    "inputs", prompt,
                    "parameters", Map.of(
                            "max_new_tokens", request.getMaxTokens(),
                            "temperature", request.getTemperature()
                    )
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpointUrl() + "/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("generated_text").asText();

            return LlmResponse.builder()
                    .success(true)
                    .content(content)
                    .model(config.modelId())
                    .build();
        }
    }

    /**
     * Adapter for custom model endpoints.
     */
    static class CustomEndpointAdapter implements ModelAdapter {
        private final FineTunedConfig config;
        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        CustomEndpointAdapter(FineTunedConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
            this.config = config;
            this.httpClient = httpClient;
            this.objectMapper = objectMapper;
        }

        @Override
        public boolean healthCheck() throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpointUrl() + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public LlmResponse complete(LlmRequest request) throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("system_prompt", request.getSystemPrompt());
            body.put("user_prompt", request.getUserPrompt());
            body.put("temperature", request.getTemperature());
            body.put("max_tokens", request.getMaxTokens());

            // Add custom headers if configured
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpointUrl() + "/complete"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

            if (config.apiKey() != null) {
                builder.header("Authorization", "Bearer " + config.apiKey());
            }

            for (Map.Entry<String, String> header : config.customHeaders().entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return LlmResponse.builder()
                        .success(false)
                        .errorMessage("Custom endpoint error: " + response.statusCode())
                        .build();
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("content").asText();
            if (content.isEmpty()) {
                content = json.path("response").asText();
            }
            if (content.isEmpty()) {
                content = json.path("text").asText();
            }

            return LlmResponse.builder()
                    .success(true)
                    .content(content)
                    .model(config.modelId())
                    .build();
        }
    }

    /**
     * Adapter for ONNX local models.
     */
    static class OnnxLocalAdapter implements ModelAdapter {
        private final FineTunedConfig config;

        OnnxLocalAdapter(FineTunedConfig config) {
            this.config = config;
        }

        @Override
        public boolean healthCheck() throws Exception {
            Path modelPath = Path.of(config.modelPath());
            return Files.exists(modelPath);
        }

        @Override
        public LlmResponse complete(LlmRequest request) throws Exception {
            // ONNX inference requires ONNX Runtime
            // This is a placeholder - actual implementation would use onnxruntime-java
            return LlmResponse.builder()
                    .success(false)
                    .errorMessage("ONNX local inference requires onnxruntime dependency. " +
                            "Add ai.onnxruntime:onnxruntime:1.16.0 to your dependencies.")
                    .build();
        }
    }

    /**
     * Configuration for fine-tuned models.
     */
    public record FineTunedConfig(
            String modelId,
            ModelType modelType,
            String apiKey,
            String endpointUrl,
            String modelPath,
            String promptTemplate,
            int maxTokens,
            double costPerToken,
            Map<String, String> customHeaders
    ) {
        public static Builder builder(String modelId) {
            return new Builder(modelId);
        }

        public static class Builder {
            private final String modelId;
            private ModelType modelType = ModelType.CUSTOM_ENDPOINT;
            private String apiKey;
            private String endpointUrl;
            private String modelPath;
            private String promptTemplate = "{system}\n\nUser: {user}\n\nAssistant:";
            private int maxTokens = 2048;
            private double costPerToken = 0.0;
            private Map<String, String> customHeaders = new HashMap<>();

            Builder(String modelId) {
                this.modelId = modelId;
            }

            public Builder modelType(ModelType type) {
                this.modelType = type;
                return this;
            }

            public Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public Builder endpointUrl(String url) {
                this.endpointUrl = url;
                return this;
            }

            public Builder modelPath(String path) {
                this.modelPath = path;
                return this;
            }

            public Builder promptTemplate(String template) {
                this.promptTemplate = template;
                return this;
            }

            public Builder maxTokens(int tokens) {
                this.maxTokens = tokens;
                return this;
            }

            public Builder costPerToken(double cost) {
                this.costPerToken = cost;
                return this;
            }

            public Builder header(String key, String value) {
                this.customHeaders.put(key, value);
                return this;
            }

            public FineTunedConfig build() {
                return new FineTunedConfig(
                        modelId, modelType, apiKey, endpointUrl, modelPath,
                        promptTemplate, maxTokens, costPerToken, customHeaders
                );
            }
        }
    }

    /**
     * Supported fine-tuned model types.
     */
    public enum ModelType {
        /** OpenAI fine-tuned model (ft:gpt-3.5-turbo:...) */
        OPENAI_FINE_TUNED,
        /** HuggingFace Inference API */
        HUGGINGFACE_API,
        /** Local HuggingFace model with text-generation-inference */
        HUGGINGFACE_LOCAL,
        /** Custom REST endpoint */
        CUSTOM_ENDPOINT,
        /** Local ONNX model */
        ONNX_LOCAL
    }
}
