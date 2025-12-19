package io.github.glaciousm.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * The LLM's decision about how to heal a test failure.
 * This is the structured output from the LLM evaluation.
 */
public final class HealDecision {
    private final boolean canHeal;
    private final double confidence;
    private final Integer selectedElementIndex;
    private final String reasoning;
    private final List<Integer> alternativeIndices;
    private final List<String> warnings;
    private final String refusalReason;

    @JsonCreator
    public HealDecision(
            @JsonProperty("can_heal") boolean canHeal,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("selected_element_index") Integer selectedElementIndex,
            @JsonProperty("reasoning") String reasoning,
            @JsonProperty("alternative_indices") List<Integer> alternativeIndices,
            @JsonProperty("warnings") List<String> warnings,
            @JsonProperty("refusal_reason") String refusalReason) {
        this.canHeal = canHeal;
        this.confidence = confidence;
        this.selectedElementIndex = selectedElementIndex;
        this.reasoning = reasoning;
        this.alternativeIndices = alternativeIndices != null ? List.copyOf(alternativeIndices) : List.of();
        this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
        this.refusalReason = refusalReason;
    }

    /**
     * Whether the LLM believes healing is possible.
     */
    public boolean canHeal() {
        return canHeal;
    }

    /**
     * Confidence level (0.0 - 1.0) in the selected element.
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Index of the selected element in the UI snapshot.
     */
    public Integer getSelectedElementIndex() {
        return selectedElementIndex;
    }

    /**
     * Natural language explanation of the decision.
     */
    public String getReasoning() {
        return reasoning;
    }

    /**
     * Other element indices that could potentially work.
     */
    public List<Integer> getAlternativeIndices() {
        return alternativeIndices;
    }

    /**
     * Any warnings about this heal decision.
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * If canHeal is false, explanation of why.
     */
    public String getRefusalReason() {
        return refusalReason;
    }

    /**
     * Creates a decision indicating healing is possible.
     */
    public static HealDecision canHeal(int elementIndex, double confidence, String reasoning) {
        return new HealDecision(true, confidence, elementIndex, reasoning, List.of(), List.of(), null);
    }

    /**
     * Creates a decision indicating healing is not possible.
     */
    public static HealDecision cannotHeal(String reason) {
        return new HealDecision(false, 0.0, null, null, List.of(), List.of(), reason);
    }

    /**
     * Creates a decision indicating low confidence.
     */
    public static HealDecision lowConfidence(double confidence, String reasoning) {
        return new HealDecision(false, confidence, null, reasoning, List.of(), List.of(),
                "Confidence " + String.format("%.2f", confidence) + " below threshold");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HealDecision that = (HealDecision) o;
        return canHeal == that.canHeal &&
               Double.compare(confidence, that.confidence) == 0 &&
               Objects.equals(selectedElementIndex, that.selectedElementIndex) &&
               Objects.equals(reasoning, that.reasoning) &&
               Objects.equals(alternativeIndices, that.alternativeIndices) &&
               Objects.equals(warnings, that.warnings) &&
               Objects.equals(refusalReason, that.refusalReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canHeal, confidence, selectedElementIndex, reasoning,
                alternativeIndices, warnings, refusalReason);
    }

    @Override
    public String toString() {
        if (canHeal) {
            return "HealDecision{canHeal=true, confidence=" + String.format("%.2f", confidence) +
                   ", elementIndex=" + selectedElementIndex + ", reasoning='" + reasoning + "'}";
        } else {
            return "HealDecision{canHeal=false, refusalReason='" + refusalReason + "'}";
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean canHeal;
        private double confidence;
        private Integer selectedElementIndex;
        private String reasoning;
        private List<Integer> alternativeIndices;
        private List<String> warnings;
        private String refusalReason;

        private Builder() {
        }

        public Builder canHeal(boolean canHeal) {
            this.canHeal = canHeal;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder selectedElementIndex(Integer selectedElementIndex) {
            this.selectedElementIndex = selectedElementIndex;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder alternativeIndices(List<Integer> alternativeIndices) {
            this.alternativeIndices = alternativeIndices;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder refusalReason(String refusalReason) {
            this.refusalReason = refusalReason;
            return this;
        }

        public HealDecision build() {
            return new HealDecision(canHeal, confidence, selectedElementIndex, reasoning,
                    alternativeIndices, warnings, refusalReason);
        }
    }
}
