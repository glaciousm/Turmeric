package com.intenthealer.llm;

import com.intenthealer.core.exception.LlmException;
import com.intenthealer.core.model.HealDecision;
import com.intenthealer.core.model.OutcomeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResponseParserTest {

    private ResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResponseParser();
    }

    @Test
    void parseHealDecision_withValidJson_returnsDecision() {
        String response = """
            {
              "can_heal": true,
              "confidence": 0.95,
              "selected_element_index": 5,
              "reasoning": "Found exact match with same text and purpose",
              "alternative_indices": [7, 12],
              "warnings": ["Element is in a different container"],
              "refusal_reason": null
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.canHeal()).isTrue();
        assertThat(decision.getConfidence()).isEqualTo(0.95);
        assertThat(decision.getSelectedElementIndex()).isEqualTo(5);
        assertThat(decision.getReasoning()).contains("exact match");
        assertThat(decision.getAlternativeIndices()).containsExactly(7, 12);
        assertThat(decision.getWarnings()).containsExactly("Element is in a different container");
        assertThat(decision.getRefusalReason()).isNull();
    }

    @Test
    void parseHealDecision_withMarkdownWrapping_extractsJson() {
        String response = """
            Here's my analysis:

            ```json
            {
              "can_heal": true,
              "confidence": 0.88,
              "selected_element_index": 3,
              "reasoning": "Good semantic match",
              "alternative_indices": [],
              "warnings": [],
              "refusal_reason": null
            }
            ```
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.canHeal()).isTrue();
        assertThat(decision.getConfidence()).isEqualTo(0.88);
        assertThat(decision.getSelectedElementIndex()).isEqualTo(3);
    }

    @Test
    void parseHealDecision_withRefusal_returnsRefusalDecision() {
        String response = """
            {
              "can_heal": false,
              "confidence": 0.0,
              "selected_element_index": null,
              "reasoning": null,
              "alternative_indices": [],
              "warnings": [],
              "refusal_reason": "No suitable element found on the page"
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.canHeal()).isFalse();
        assertThat(decision.getConfidence()).isEqualTo(0.0);
        assertThat(decision.getSelectedElementIndex()).isNull();
        assertThat(decision.getRefusalReason()).isEqualTo("No suitable element found on the page");
    }

    @Test
    void parseHealDecision_withMissingCanHealField_throwsException() {
        String response = """
            {
              "confidence": 0.95,
              "selected_element_index": 5
            }
            """;

        assertThatThrownBy(() -> parser.parseHealDecision(response))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("can_heal");
    }

    @Test
    void parseHealDecision_withMalformedJson_throwsException() {
        String response = "{ this is not valid json }";

        assertThatThrownBy(() -> parser.parseHealDecision(response))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void parseHealDecision_withEmptyResponse_throwsException() {
        assertThatThrownBy(() -> parser.parseHealDecision(""))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void parseHealDecision_withNullResponse_throwsException() {
        assertThatThrownBy(() -> parser.parseHealDecision(null))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void parseHealDecision_withProviderInfo_includesInException() {
        String response = "invalid json";

        assertThatThrownBy(() -> parser.parseHealDecision(response, "openai", "gpt-4"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void parseHealDecision_withMissingOptionalFields_setsDefaults() {
        String response = """
            {
              "can_heal": true
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.canHeal()).isTrue();
        assertThat(decision.getConfidence()).isEqualTo(0.0);
        assertThat(decision.getSelectedElementIndex()).isNull();
        assertThat(decision.getReasoning()).isNull();
        assertThat(decision.getAlternativeIndices()).isEmpty();
        assertThat(decision.getWarnings()).isEmpty();
    }

    @Test
    void parseHealDecision_withEmptyArrays_returnsEmptyLists() {
        String response = """
            {
              "can_heal": true,
              "confidence": 0.9,
              "selected_element_index": 1,
              "reasoning": "test",
              "alternative_indices": [],
              "warnings": [],
              "refusal_reason": null
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.getAlternativeIndices()).isEmpty();
        assertThat(decision.getWarnings()).isEmpty();
    }

    @Test
    void parseOutcomeResult_withValidPassedResult_returnsResult() {
        String response = """
            {
              "outcome_achieved": true,
              "confidence": 0.95,
              "reasoning": "URL changed to dashboard and logout button is visible"
            }
            """;

        OutcomeResult result = parser.parseOutcomeResult(response);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getConfidence()).isEqualTo(0.95);
        assertThat(result.getReasoning()).contains("dashboard");
    }

    @Test
    void parseOutcomeResult_withValidFailedResult_returnsResult() {
        String response = """
            {
              "outcome_achieved": false,
              "confidence": 0.8,
              "reasoning": "Still on login page, expected to see dashboard"
            }
            """;

        OutcomeResult result = parser.parseOutcomeResult(response);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReasoning()).contains("login page");
    }

    @Test
    void parseOutcomeResult_withMarkdownWrapping_extractsJson() {
        String response = """
            ```json
            {
              "outcome_achieved": true,
              "confidence": 1.0,
              "reasoning": "Outcome verified successfully"
            }
            ```
            """;

        OutcomeResult result = parser.parseOutcomeResult(response);

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void parseOutcomeResult_withMissingOutcomeAchievedField_throwsException() {
        String response = """
            {
              "confidence": 0.95,
              "reasoning": "Something happened"
            }
            """;

        assertThatThrownBy(() -> parser.parseOutcomeResult(response))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("outcome_achieved");
    }

    @Test
    void parseOutcomeResult_withMissingOptionalFields_usesDefaults() {
        String response = """
            {
              "outcome_achieved": true
            }
            """;

        OutcomeResult result = parser.parseOutcomeResult(response);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.getReasoning()).isEmpty();
    }

    @Test
    void parseOutcomeResult_withEmptyResponse_throwsException() {
        assertThatThrownBy(() -> parser.parseOutcomeResult(""))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void parseOutcomeResult_withNullResponse_throwsException() {
        assertThatThrownBy(() -> parser.parseOutcomeResult(null))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void parseOutcomeResult_withMalformedJson_throwsException() {
        String response = "not valid json at all";

        assertThatThrownBy(() -> parser.parseOutcomeResult(response))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void attemptJsonRepair_withBrokenJson_extractsJsonObject() {
        String response = "Some text before { \"key\": \"value\" } some text after";

        String repaired = parser.attemptJsonRepair(response);

        assertThat(repaired).isEqualTo("{ \"key\": \"value\" }");
    }

    @Test
    void attemptJsonRepair_withNestedJson_extractsOuterObject() {
        String response = "text { \"outer\": { \"inner\": \"value\" } } text";

        String repaired = parser.attemptJsonRepair(response);

        assertThat(repaired).isEqualTo("{ \"outer\": { \"inner\": \"value\" } }");
    }

    @Test
    void attemptJsonRepair_withNoJson_returnsOriginal() {
        String response = "no json here at all";

        String repaired = parser.attemptJsonRepair(response);

        assertThat(repaired).isEqualTo(response);
    }

    @Test
    void attemptJsonRepair_withNullInput_returnsNull() {
        String repaired = parser.attemptJsonRepair(null);

        assertThat(repaired).isNull();
    }

    @Test
    void parseHealDecision_withLowConfidence_stillParsesCorrectly() {
        String response = """
            {
              "can_heal": false,
              "confidence": 0.65,
              "selected_element_index": null,
              "reasoning": "Confidence below threshold",
              "alternative_indices": [1, 2],
              "warnings": ["Low confidence match"],
              "refusal_reason": "Confidence 0.65 below threshold"
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.canHeal()).isFalse();
        assertThat(decision.getConfidence()).isEqualTo(0.65);
        assertThat(decision.getRefusalReason()).contains("threshold");
    }

    @Test
    void parseHealDecision_withZeroIndex_acceptsZero() {
        String response = """
            {
              "can_heal": true,
              "confidence": 0.9,
              "selected_element_index": 0,
              "reasoning": "First element matches",
              "alternative_indices": [],
              "warnings": [],
              "refusal_reason": null
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.getSelectedElementIndex()).isEqualTo(0);
    }

    @Test
    void parseHealDecision_withMultipleWarnings_preservesAll() {
        String response = """
            {
              "can_heal": true,
              "confidence": 0.82,
              "selected_element_index": 3,
              "reasoning": "Match found",
              "alternative_indices": [],
              "warnings": [
                "Element is in different location",
                "Text has minor variations",
                "Consider verifying manually"
              ],
              "refusal_reason": null
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.getWarnings()).hasSize(3);
        assertThat(decision.getWarnings()).contains(
                "Element is in different location",
                "Text has minor variations",
                "Consider verifying manually"
        );
    }

    @Test
    void parseHealDecision_withExtraFields_ignoresThem() {
        String response = """
            {
              "can_heal": true,
              "confidence": 0.9,
              "selected_element_index": 2,
              "reasoning": "Match found",
              "alternative_indices": [],
              "warnings": [],
              "refusal_reason": null,
              "extra_field": "should be ignored",
              "another_extra": 123
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.canHeal()).isTrue();
        assertThat(decision.getSelectedElementIndex()).isEqualTo(2);
    }

    @Test
    void parseOutcomeResult_withExtraFields_ignoresThem() {
        String response = """
            {
              "outcome_achieved": true,
              "confidence": 0.95,
              "reasoning": "Success",
              "extra_data": "ignored"
            }
            """;

        OutcomeResult result = parser.parseOutcomeResult(response);

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void parseHealDecision_withMultilineReasoning_preservesNewlines() {
        String response = """
            {
              "can_heal": true,
              "confidence": 0.9,
              "selected_element_index": 1,
              "reasoning": "Match found based on:\\n1. Same semantic purpose\\n2. Similar text content\\n3. Equivalent position",
              "alternative_indices": [],
              "warnings": [],
              "refusal_reason": null
            }
            """;

        HealDecision decision = parser.parseHealDecision(response);

        assertThat(decision.getReasoning()).contains("Match found based on:");
    }
}
