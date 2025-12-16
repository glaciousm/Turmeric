package com.intenthealer.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intenthealer.llm.LlmProvider;
import com.intenthealer.llm.LlmRequest;
import com.intenthealer.llm.LlmResponse;
import com.intenthealer.llm.PromptBuilder;
import com.intenthealer.llm.ResponseParser;
import com.intenthealer.llm.util.SecurityUtils;
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
 * <p>Supports multiple model backends:</p>
 * <ul>
 *   <li><b>OPENAI_FINE_TUNED</b> - OpenAI fine-tuned models (ft:gpt-3.5-turbo:...).
 *       Requires OpenAI API key. Fully functional.</li>
 *   <li><b>HUGGINGFACE_API</b> - HuggingFace Inference API.
 *       Requires HuggingFace API token. Fully functional.</li>
 *   <li><b>HUGGINGFACE_LOCAL</b> - Local HuggingFace models via inference server.
 *       Requires a running text-generation-inference server. See setup instructions below.</li>
 *   <li><b>CUSTOM_ENDPOINT</b> - Any REST API endpoint following a simple protocol.
 *       Fully functional with any compatible backend.</li>
 *   <li><b>ONNX_LOCAL</b> - Local ONNX models for low-latency inference.
 *       Requires additional dependency (onnxruntime). See setup instructions below.</li>
 * </ul>
 *
 * <h3>Setting up HuggingFace Local</h3>
 * <p>To run local HuggingFace models, you need to start a text-generation-inference server:</p>
 * <pre>{@code
 * # Install text-generation-inference
 * pip install text-generation-inference
 *
 * # Start the server with your model
 * text-generation-launcher --model-id your-model-id --port 8080
 *
 * # Or using Docker
 * docker run --gpus all -p 8080:80 \
 *   ghcr.io/huggingface/text-generation-inference:latest \
 *   --model-id your-model-id
 *
 * # Configure the provider
 * FineTunedConfig config = FineTunedConfig.builder("your-model-id")
 *     .modelType(ModelType.HUGGINGFACE_LOCAL)
 *     .endpointUrl("http://localhost:8080")
 *     .build();
 * }</pre>
 *
 * <h3>Setting up ONNX Local</h3>
 * <p>For ONNX inference, add the onnxruntime dependency to your project:</p>
 * <pre>{@code
 * <!-- pom.xml -->
 * <dependency>
 *     <groupId>ai.onnxruntime</groupId>
 *     <artifactId>onnxruntime</artifactId>
 *     <version>1.16.0</version>
 * </dependency>
 * <!-- For GPU support: -->
 * <!-- <dependency>
 *     <groupId>ai.onnxruntime</groupId>
 *     <artifactId>onnxruntime_gpu</artifactId>
 *     <version>1.16.0</version>
 * </dependency> -->
 *
 * // Configure the provider
 * FineTunedConfig config = FineTunedConfig.builder("my-onnx-model")
 *     .modelType(ModelType.ONNX_LOCAL)
 *     .modelPath("/path/to/model.onnx")
 *     .build();
 * }</pre>
 *
 * <p><b>Note:</b> ONNX inference requires implementing the tokenization and model execution
 * logic specific to your model architecture. The current implementation provides guidance
 * on setting up the dependency but requires custom implementation for specific models.</p>
 *
 * @see FineTunedConfig
 * @see ModelType
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
    public String getProviderName() {
        return getName();
    }

    @Override
    public com.intenthealer.core.model.HealDecision evaluateCandidates(
            com.intenthealer.core.model.FailureContext failure,
            com.intenthealer.core.model.UiSnapshot snapshot,
            com.intenthealer.core.model.IntentContract intent,
            com.intenthealer.core.config.LlmConfig config) {
        // Create a PromptBuilder instance
        PromptBuilder promptBuilder = new PromptBuilder();

        // Build a healing prompt
        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        // Create an LlmRequest with the prompt
        LlmRequest request = LlmRequest.builder()
                .prompt(prompt)
                .systemMessage(promptBuilder.buildSystemPrompt())
                .maxTokens(this.config.maxTokens())
                .temperature(0.0)
                .build();

        // Call this.complete(request) which uses the adapter
        LlmResponse response = this.complete(request);

        // Parse the response using ResponseParser to get a HealDecision
        if (!response.isSuccess()) {
            String errorMsg = response.getErrorMessage().orElse("Unknown error");
            logger.warn("Fine-tuned model completion failed: {}", SecurityUtils.sanitizeErrorMessage(errorMsg));
            return com.intenthealer.core.model.HealDecision.builder()
                    .canHeal(false)
                    .confidence(0.0)
                    .refusalReason("LLM request failed: " + SecurityUtils.sanitizeErrorMessage(errorMsg))
                    .build();
        }

        ResponseParser responseParser = new ResponseParser();
        return responseParser.parseHealDecision(response.getContent().orElse(""), getName(), this.config.modelId());
    }

    @Override
    public com.intenthealer.core.model.OutcomeResult validateOutcome(
            String expectedOutcome,
            com.intenthealer.core.model.UiSnapshot before,
            com.intenthealer.core.model.UiSnapshot after,
            com.intenthealer.core.config.LlmConfig config) {
        // Create a PromptBuilder instance
        PromptBuilder promptBuilder = new PromptBuilder();

        // Build an outcome validation prompt
        String prompt = promptBuilder.buildOutcomeValidationPrompt(expectedOutcome, before, after);

        // Create an LlmRequest with the prompt
        LlmRequest request = LlmRequest.builder()
                .prompt(prompt)
                .systemMessage(promptBuilder.buildSystemPrompt())
                .maxTokens(this.config.maxTokens())
                .temperature(0.0)
                .build();

        // Call this.complete(request)
        LlmResponse response = this.complete(request);

        // Parse the response to create an OutcomeResult
        if (!response.isSuccess()) {
            String errorMsg = response.getErrorMessage().orElse("Unknown error");
            logger.warn("Fine-tuned model outcome validation failed: {}", SecurityUtils.sanitizeErrorMessage(errorMsg));
            return com.intenthealer.core.model.OutcomeResult.failed(
                    "LLM request failed: " + SecurityUtils.sanitizeErrorMessage(errorMsg));
        }

        ResponseParser responseParser = new ResponseParser();
        return responseParser.parseOutcomeResult(response.getContent().orElse(""), getName(), this.config.modelId());
    }

    @Override
    public boolean isAvailable() {
        try {
            return adapter.healthCheck();
        } catch (Exception e) {
            logger.warn("Fine-tuned model health check failed: {}", SecurityUtils.sanitizeErrorMessage(e.getMessage()));
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
                    .errorMessage(SecurityUtils.sanitizeErrorMessage("Fine-tuned model error: " + e.getMessage()))
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
                        .errorMessage(SecurityUtils.sanitizeErrorMessage("OpenAI API error: " + response.statusCode()))
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
                        .errorMessage(SecurityUtils.sanitizeErrorMessage("HuggingFace API error: " + response.statusCode()))
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
     * Adapter for local HuggingFace models via text-generation-inference server.
     *
     * <p>This adapter connects to a running text-generation-inference (TGI) server.
     * TGI is HuggingFace's optimized inference server for transformer models.</p>
     *
     * <p><b>Requirements:</b></p>
     * <ul>
     *   <li>A running TGI server (see class documentation for setup)</li>
     *   <li>Configure endpointUrl to point to the TGI server</li>
     * </ul>
     *
     * <p><b>Supported endpoints:</b></p>
     * <ul>
     *   <li>{@code /generate} - Standard TGI generate endpoint</li>
     *   <li>{@code /health} - Health check (optional)</li>
     * </ul>
     */
    static class HuggingFaceLocalAdapter implements ModelAdapter {
        private static final Logger log = LoggerFactory.getLogger(HuggingFaceLocalAdapter.class);

        private final FineTunedConfig config;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        HuggingFaceLocalAdapter(FineTunedConfig config, ObjectMapper objectMapper) {
            this.config = config;
            this.objectMapper = objectMapper;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
        }

        @Override
        public boolean healthCheck() throws Exception {
            if (config.endpointUrl() != null) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(config.endpointUrl() + "/health"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    return response.statusCode() == 200;
                } catch (Exception e) {
                    log.debug("Health check failed for endpoint {}: {}", config.endpointUrl(), SecurityUtils.sanitizeErrorMessage(e.getMessage()));
                    return false;
                }
            }

            // Check if model path exists (for documentation purposes)
            if (config.modelPath() != null) {
                Path modelPath = Path.of(config.modelPath());
                return Files.exists(modelPath);
            }

            return false;
        }

        @Override
        public LlmResponse complete(LlmRequest request) throws Exception {
            if (config.endpointUrl() == null) {
                String instructions = String.format("""
                    Local HuggingFace inference requires a running text-generation-inference server.

                    Quick setup with Docker:
                      docker run --gpus all -p 8080:80 \\
                        ghcr.io/huggingface/text-generation-inference:latest \\
                        --model-id %s

                    Then configure endpointUrl: "http://localhost:8080"

                    See: https://huggingface.co/docs/text-generation-inference
                    """, config.modelId());

                return LlmResponse.builder()
                        .success(false)
                        .errorMessage(instructions)
                        .build();
            }

            String prompt = config.promptTemplate()
                    .replace("{system}", request.getSystemPrompt())
                    .replace("{user}", request.getUserPrompt());

            Map<String, Object> body = Map.of(
                    "inputs", prompt,
                    "parameters", Map.of(
                            "max_new_tokens", request.getMaxTokens(),
                            "temperature", request.getTemperature(),
                            "do_sample", request.getTemperature() > 0
                    )
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpointUrl() + "/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return LlmResponse.builder()
                        .success(false)
                        .errorMessage(SecurityUtils.sanitizeErrorMessage("TGI server error: " + response.statusCode() + " - " + response.body()))
                        .build();
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("generated_text").asText();
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
                        .errorMessage(SecurityUtils.sanitizeErrorMessage("Custom endpoint error: " + response.statusCode()))
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
     *
     * <p>This adapter is designed for running ONNX-exported models locally for
     * low-latency inference without network calls. ONNX Runtime provides efficient
     * inference across CPU and GPU.</p>
     *
     * <p><b>Requirements:</b></p>
     * <ul>
     *   <li>Add onnxruntime dependency to your project</li>
     *   <li>Export your model to ONNX format</li>
     *   <li>Implement tokenization for your specific model</li>
     * </ul>
     *
     * <p><b>Note:</b> Due to the model-specific nature of tokenization and output parsing,
     * this adapter provides a template for integration. You will need to extend it
     * with model-specific logic for your use case.</p>
     *
     * <p>For production use, consider using the CUSTOM_ENDPOINT type with a local
     * inference server (like Triton, TorchServe, or TGI) which handles these
     * complexities automatically.</p>
     */
    static class OnnxLocalAdapter implements ModelAdapter {
        private static final Logger log = LoggerFactory.getLogger(OnnxLocalAdapter.class);

        private final FineTunedConfig config;

        OnnxLocalAdapter(FineTunedConfig config) {
            this.config = config;
        }

        @Override
        public boolean healthCheck() throws Exception {
            if (config.modelPath() == null) {
                log.debug("ONNX model path not configured");
                return false;
            }
            Path modelPath = Path.of(config.modelPath());
            boolean exists = Files.exists(modelPath);
            if (!exists) {
                log.debug("ONNX model not found at: {}", modelPath);
            }
            return exists;
        }

        @Override
        public LlmResponse complete(LlmRequest request) throws Exception {
            // Check if model file exists
            if (config.modelPath() == null || !Files.exists(Path.of(config.modelPath()))) {
                return LlmResponse.builder()
                        .success(false)
                        .errorMessage(SecurityUtils.sanitizeErrorMessage("ONNX model file not found: " + config.modelPath()))
                        .build();
            }

            // ONNX Runtime integration requires model-specific implementation
            String instructions = """
                ONNX local inference requires additional setup:

                1. Add dependency to pom.xml:
                   <dependency>
                       <groupId>ai.onnxruntime</groupId>
                       <artifactId>onnxruntime</artifactId>
                       <version>1.16.0</version>
                   </dependency>
                   <!-- For GPU: onnxruntime_gpu -->

                2. ONNX inference requires model-specific tokenization logic.
                   Each model has its own tokenizer (BPE, SentencePiece, etc.)

                3. For simpler integration, consider:
                   - Using HUGGINGFACE_LOCAL with text-generation-inference
                   - Using CUSTOM_ENDPOINT with Triton Inference Server
                   - Using CUSTOM_ENDPOINT with vLLM

                These servers handle tokenization automatically and expose
                a simple REST API.

                Model path: %s
                """.formatted(config.modelPath());

            return LlmResponse.builder()
                    .success(false)
                    .errorMessage(instructions)
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
