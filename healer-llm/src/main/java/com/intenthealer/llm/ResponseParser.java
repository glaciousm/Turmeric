package com.intenthealer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.intenthealer.core.exception.LlmException;
import com.intenthealer.core.model.HealDecision;
import com.intenthealer.core.model.OutcomeResult;
import com.intenthealer.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses LLM responses into structured objects.
 */
public class ResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(ResponseParser.class);

    /**
     * Parse a heal decision from LLM response (no provider/model info).
     */
    public HealDecision parseHealDecision(String response) {
        return parseHealDecision(response, "unknown", "unknown");
    }

    /**
     * Parse a heal decision from LLM response.
     */
    public HealDecision parseHealDecision(String response, String provider, String model) {
        if (response == null || response.isEmpty()) {
            throw LlmException.invalidResponse(provider, model, "Empty response");
        }

        // Extract JSON from response (may be wrapped in markdown)
        String jsonContent = JsonUtils.extractJsonFromMarkdown(response);

        Optional<JsonNode> jsonOpt = JsonUtils.tryParseJson(jsonContent);
        if (jsonOpt.isEmpty()) {
            logger.warn("Failed to parse LLM response as JSON: {}", truncate(response, 200));
            throw LlmException.invalidResponse(provider, model, "Invalid JSON: " + truncate(response, 100));
        }

        JsonNode json = jsonOpt.get();
        return parseHealDecisionFromJson(json, provider, model);
    }

    private HealDecision parseHealDecisionFromJson(JsonNode json, String provider, String model) {
        HealDecision.Builder builder = HealDecision.builder();

        // can_heal (required)
        if (!json.has("can_heal")) {
            throw LlmException.invalidResponse(provider, model, "Missing 'can_heal' field");
        }
        builder.canHeal(json.get("can_heal").asBoolean());

        // confidence
        double confidence = 0.0;
        if (json.has("confidence")) {
            confidence = json.get("confidence").asDouble();
        }
        builder.confidence(confidence);

        // selected_element_index
        if (json.has("selected_element_index") && !json.get("selected_element_index").isNull()) {
            builder.selectedElementIndex(json.get("selected_element_index").asInt());
        }

        // reasoning
        if (json.has("reasoning") && !json.get("reasoning").isNull()) {
            builder.reasoning(json.get("reasoning").asText());
        }

        // alternative_indices
        if (json.has("alternative_indices") && json.get("alternative_indices").isArray()) {
            List<Integer> alternatives = new ArrayList<>();
            for (JsonNode idx : json.get("alternative_indices")) {
                alternatives.add(idx.asInt());
            }
            builder.alternativeIndices(alternatives);
        }

        // warnings
        if (json.has("warnings") && json.get("warnings").isArray()) {
            List<String> warnings = new ArrayList<>();
            for (JsonNode warning : json.get("warnings")) {
                warnings.add(warning.asText());
            }
            builder.warnings(warnings);
        }

        // refusal_reason
        if (json.has("refusal_reason") && !json.get("refusal_reason").isNull()) {
            builder.refusalReason(json.get("refusal_reason").asText());
        }

        return builder.build();
    }

    /**
     * Parse an outcome result from LLM response (no provider/model info).
     */
    public OutcomeResult parseOutcomeResult(String response) {
        return parseOutcomeResult(response, "unknown", "unknown");
    }

    /**
     * Parse an outcome result from LLM response.
     */
    public OutcomeResult parseOutcomeResult(String response, String provider, String model) {
        if (response == null || response.isEmpty()) {
            throw LlmException.invalidResponse(provider, model, "Empty response");
        }

        String jsonContent = JsonUtils.extractJsonFromMarkdown(response);

        Optional<JsonNode> jsonOpt = JsonUtils.tryParseJson(jsonContent);
        if (jsonOpt.isEmpty()) {
            logger.warn("Failed to parse outcome validation response: {}", truncate(response, 200));
            throw LlmException.invalidResponse(provider, model, "Invalid JSON: " + truncate(response, 100));
        }

        JsonNode json = jsonOpt.get();
        return parseOutcomeResultFromJson(json, provider, model);
    }

    private OutcomeResult parseOutcomeResultFromJson(JsonNode json, String provider, String model) {
        // outcome_achieved (required)
        if (!json.has("outcome_achieved")) {
            throw LlmException.invalidResponse(provider, model, "Missing 'outcome_achieved' field");
        }

        boolean achieved = json.get("outcome_achieved").asBoolean();
        double confidence = json.has("confidence") ? json.get("confidence").asDouble() : 1.0;
        String reasoning = json.has("reasoning") ? json.get("reasoning").asText() : "";

        if (achieved) {
            return OutcomeResult.passed(reasoning, confidence);
        } else {
            return OutcomeResult.failed(reasoning);
        }
    }

    /**
     * Try to repair a malformed JSON response.
     */
    public String attemptJsonRepair(String response) {
        if (response == null) return null;

        String trimmed = response.trim();

        // Try to find JSON object boundaries
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');

        if (start != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return response;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
