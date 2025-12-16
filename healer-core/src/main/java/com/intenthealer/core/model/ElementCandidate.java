package com.intenthealer.core.model;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents a candidate element that could be used for healing.
 */
public final class ElementCandidate {
    private final String locator;
    private final double confidence;
    private final String explanation;
    private final String tagName;
    private final Map<String, String> attributes;

    private ElementCandidate(Builder builder) {
        this.locator = builder.locator;
        this.confidence = builder.confidence;
        this.explanation = builder.explanation;
        this.tagName = builder.tagName;
        this.attributes = builder.attributes != null ? Map.copyOf(builder.attributes) : Map.of();
    }

    public String getLocator() {
        return locator;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getTagName() {
        return tagName;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String locator;
        private double confidence;
        private String explanation;
        private String tagName;
        private Map<String, String> attributes = new HashMap<>();

        private Builder() {}

        public Builder locator(String locator) {
            this.locator = locator;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder explanation(String explanation) {
            this.explanation = explanation;
            return this;
        }

        public Builder tagName(String tagName) {
            this.tagName = tagName;
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }

        public ElementCandidate build() {
            return new ElementCandidate(this);
        }
    }
}
