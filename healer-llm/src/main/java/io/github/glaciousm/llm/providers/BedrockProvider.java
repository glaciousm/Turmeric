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
import io.github.glaciousm.llm.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * LLM provider implementation for AWS Bedrock.
 * Supports Claude and other models through AWS Bedrock service.
 */
public class BedrockProvider implements LlmProvider {

    private static final Logger logger = LoggerFactory.getLogger(BedrockProvider.class);

    private static final String SERVICE = "bedrock";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_MODEL = "anthropic.claude-3-5-sonnet-20241022-v2:0";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public BedrockProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.promptBuilder = new PromptBuilder();
        this.responseParser = new ResponseParser();
    }

    @Override
    public String getProviderName() {
        return "bedrock";
    }

    @Override
    public boolean isAvailable() {
        String accessKey = getAccessKey();
        String secretKey = getSecretKey();
        return accessKey != null && !accessKey.isEmpty() &&
                secretKey != null && !secretKey.isEmpty();
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

            BedrockResponse response = invokeModel(systemPrompt, prompt, config);

            HealDecision decision = responseParser.parseHealDecision(response.content);
            logger.debug("Bedrock response: latency={}ms, tokens={}/{}",
                    System.currentTimeMillis() - startTime, response.inputTokens, response.outputTokens);

            return decision;

        } catch (Exception e) {
            throw LlmException.connectionError(getProviderName(), SecurityUtils.sanitizeErrorMessage(e.getMessage()));
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

            BedrockResponse response = invokeModel(systemPrompt, prompt, config);

            return responseParser.parseOutcomeResult(response.content);

        } catch (Exception e) {
            throw LlmException.connectionError(getProviderName(), SecurityUtils.sanitizeErrorMessage(e.getMessage()));
        }
    }

    private BedrockResponse invokeModel(String systemPrompt, String userPrompt, LlmConfig config)
            throws IOException, InterruptedException {

        String region = getRegion(config);
        String modelId = getModel(config);

        // Determine endpoint based on model
        String host = String.format("bedrock-runtime.%s.amazonaws.com", region);
        String endpoint = String.format("https://%s/model/%s/invoke", host, modelId);

        // Build request body based on model type
        String requestBody;
        if (modelId.startsWith("anthropic.")) {
            requestBody = buildAnthropicRequest(systemPrompt, userPrompt, config);
        } else if (modelId.startsWith("amazon.titan")) {
            requestBody = buildTitanRequest(systemPrompt, userPrompt, config);
        } else if (modelId.startsWith("meta.llama")) {
            requestBody = buildLlamaRequest(systemPrompt, userPrompt, config);
        } else {
            // Default to Anthropic format
            requestBody = buildAnthropicRequest(systemPrompt, userPrompt, config);
        }

        int timeoutSeconds = config != null && config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 60;

        // Sign request with AWS Signature V4
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String contentHash = sha256Hash(requestBody);

        String canonicalHeaders = "content-type:application/json\n" +
                "host:" + host + "\n" +
                "x-amz-content-sha256:" + contentHash + "\n" +
                "x-amz-date:" + amzDate + "\n";

        String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";

        String canonicalRequest = "POST\n" +
                "/model/" + modelId + "/invoke\n" +
                "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                contentHash;

        String credentialScope = dateStamp + "/" + region + "/" + SERVICE + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" +
                amzDate + "\n" +
                credentialScope + "\n" +
                sha256Hash(canonicalRequest);

        byte[] signingKey = getSignatureKey(getSecretKey(), dateStamp, region, SERVICE);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        String authorizationHeader = "AWS4-HMAC-SHA256 " +
                "Credential=" + getAccessKey() + "/" + credentialScope + ", " +
                "SignedHeaders=" + signedHeaders + ", " +
                "Signature=" + signature;

        logger.debug("Bedrock request to model: {}", modelId);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("X-Amz-Date", amzDate)
                .header("X-Amz-Content-Sha256", contentHash)
                .header("Authorization", authorizationHeader)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        // Add session token if using temporary credentials
        String sessionToken = getSessionToken();
        if (sessionToken != null && !sessionToken.isEmpty()) {
            requestBuilder.header("X-Amz-Security-Token", sessionToken);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            logger.error("Bedrock API error: {} - {}", httpResponse.statusCode(), SecurityUtils.sanitizeErrorMessage(httpResponse.body()));
            throw new LlmException(SecurityUtils.sanitizeErrorMessage("Bedrock API error: " + httpResponse.statusCode()),
                    getProviderName(), modelId);
        }

        return parseResponse(httpResponse.body(), modelId);
    }

    private String buildAnthropicRequest(String systemPrompt, String userPrompt, LlmConfig config) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();

        request.put("anthropic_version", "bedrock-2023-05-31");

        int maxTokens = config != null && config.getMaxTokensPerRequest() > 0
                ? config.getMaxTokensPerRequest() : 4096;
        request.put("max_tokens", maxTokens);

        double temperature = config != null && config.getTemperature() > 0 ? config.getTemperature() : 0.1;
        request.put("temperature", temperature);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            request.put("system", systemPrompt);
        }

        ArrayNode messages = request.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        return objectMapper.writeValueAsString(request);
    }

    private String buildTitanRequest(String systemPrompt, String userPrompt, LlmConfig config) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();

        String fullPrompt = systemPrompt != null ? systemPrompt + "\n\n" + userPrompt : userPrompt;
        request.put("inputText", fullPrompt);

        ObjectNode textGenConfig = request.putObject("textGenerationConfig");
        double temperature = config != null && config.getTemperature() > 0 ? config.getTemperature() : 0.1;
        textGenConfig.put("temperature", temperature);

        int maxTokens = config != null && config.getMaxTokensPerRequest() > 0
                ? config.getMaxTokensPerRequest() : 4096;
        textGenConfig.put("maxTokenCount", maxTokens);

        return objectMapper.writeValueAsString(request);
    }

    private String buildLlamaRequest(String systemPrompt, String userPrompt, LlmConfig config) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();

        String fullPrompt = String.format("<s>[INST] <<SYS>>\n%s\n<</SYS>>\n\n%s [/INST]",
                systemPrompt != null ? systemPrompt : "You are a helpful assistant.",
                userPrompt);
        request.put("prompt", fullPrompt);

        double temperature = config != null && config.getTemperature() > 0 ? config.getTemperature() : 0.1;
        request.put("temperature", temperature);

        int maxTokens = config != null && config.getMaxTokensPerRequest() > 0
                ? config.getMaxTokensPerRequest() : 4096;
        request.put("max_gen_len", maxTokens);

        return objectMapper.writeValueAsString(request);
    }

    private BedrockResponse parseResponse(String responseBody, String modelId) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        BedrockResponse response = new BedrockResponse();

        if (modelId.startsWith("anthropic.")) {
            JsonNode content = root.get("content");
            if (content != null && content.isArray() && !content.isEmpty()) {
                response.content = content.get(0).get("text").asText();
            }
            JsonNode usage = root.get("usage");
            if (usage != null) {
                response.inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
                response.outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
            }
        } else if (modelId.startsWith("amazon.titan")) {
            JsonNode results = root.get("results");
            if (results != null && results.isArray() && !results.isEmpty()) {
                response.content = results.get(0).get("outputText").asText();
            }
        } else if (modelId.startsWith("meta.llama")) {
            response.content = root.has("generation") ? root.get("generation").asText() : "";
        } else {
            // Try common response fields
            if (root.has("completion")) {
                response.content = root.get("completion").asText();
            } else if (root.has("content")) {
                response.content = root.get("content").asText();
            }
        }

        return response;
    }

    private String getRegion(LlmConfig config) {
        if (config != null && config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
            // Extract region from endpoint if provided
            String endpoint = config.getEndpoint();
            if (endpoint.contains(".")) {
                String[] parts = endpoint.split("\\.");
                for (String part : parts) {
                    if (part.matches("us-[a-z]+-\\d|eu-[a-z]+-\\d|ap-[a-z]+-\\d")) {
                        return part;
                    }
                }
            }
        }
        String region = System.getenv("AWS_REGION");
        if (region == null || region.isEmpty()) {
            region = System.getenv("AWS_DEFAULT_REGION");
        }
        return region != null && !region.isEmpty() ? region : DEFAULT_REGION;
    }

    private String getModel(LlmConfig config) {
        if (config != null && config.getModel() != null && !config.getModel().isEmpty()) {
            return config.getModel();
        }
        String model = System.getenv("BEDROCK_MODEL_ID");
        return model != null && !model.isEmpty() ? model : DEFAULT_MODEL;
    }

    private String getAccessKey() {
        return System.getenv("AWS_ACCESS_KEY_ID");
    }

    private String getSecretKey() {
        return System.getenv("AWS_SECRET_ACCESS_KEY");
    }

    private String getSessionToken() {
        return System.getenv("AWS_SESSION_TOKEN");
    }

    private double calculateCost(int inputTokens, int outputTokens, String modelId) {
        double inputCostPer1k;
        double outputCostPer1k;

        if (modelId.contains("claude-3-5-sonnet") || modelId.contains("claude-3-sonnet")) {
            inputCostPer1k = 0.003;
            outputCostPer1k = 0.015;
        } else if (modelId.contains("claude-3-haiku")) {
            inputCostPer1k = 0.00025;
            outputCostPer1k = 0.00125;
        } else if (modelId.contains("claude-3-opus")) {
            inputCostPer1k = 0.015;
            outputCostPer1k = 0.075;
        } else if (modelId.contains("titan")) {
            inputCostPer1k = 0.0008;
            outputCostPer1k = 0.0016;
        } else if (modelId.contains("llama")) {
            inputCostPer1k = 0.00075;
            outputCostPer1k = 0.001;
        } else {
            // Default pricing
            inputCostPer1k = 0.003;
            outputCostPer1k = 0.015;
        }

        return (inputTokens / 1000.0) * inputCostPer1k + (outputTokens / 1000.0) * outputCostPer1k;
    }

    // AWS Signature V4 helpers

    private String sha256Hash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw LlmException.cryptoError(getProviderName(), "SHA-256 hashing", e);
        }
    }

    private byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw LlmException.cryptoError(getProviderName(), "HMAC-SHA256", e);
        }
    }

    private String hmacSha256Hex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmacSha256(key, data));
    }

    private byte[] getSignatureKey(String secretKey, String dateStamp, String region, String service) {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static class BedrockResponse {
        String content;
        int inputTokens;
        int outputTokens;
    }
}
