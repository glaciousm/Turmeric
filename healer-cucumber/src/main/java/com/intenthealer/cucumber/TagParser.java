package com.intenthealer.cucumber;

import com.intenthealer.core.model.HealPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses @heal tags from Cucumber scenarios and features.
 *
 * Supported tags:
 * - @heal:off - Disable healing
 * - @heal:suggest - Suggest heals but don't apply
 * - @heal:auto_safe - Auto-apply safe heals (default)
 * - @heal:auto_all - Auto-apply all heals
 * - @heal:destructive - Allow destructive actions
 * - @heal:confidence:0.9 - Set custom confidence threshold
 */
public class TagParser {

    private static final Logger logger = LoggerFactory.getLogger(TagParser.class);

    private static final Pattern HEAL_TAG_PATTERN = Pattern.compile(
            "@heal:([a-z_]+)(?::([\\d.]+))?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, HealPolicy> POLICY_MAP = Map.of(
            "off", HealPolicy.OFF,
            "suggest", HealPolicy.SUGGEST,
            "auto_safe", HealPolicy.AUTO_SAFE,
            "auto", HealPolicy.AUTO_SAFE,
            "auto_all", HealPolicy.AUTO_ALL,
            "all", HealPolicy.AUTO_ALL
    );

    /**
     * Parse heal settings from a collection of tags.
     */
    public HealSettings parse(Collection<String> tags) {
        HealSettings.Builder builder = HealSettings.builder();

        for (String tag : tags) {
            parseTag(tag, builder);
        }

        return builder.build();
    }

    /**
     * Parse heal settings from multiple tag sources (feature, scenario, step).
     * Later sources override earlier ones.
     */
    public HealSettings parse(Collection<String> featureTags,
                               Collection<String> scenarioTags,
                               Collection<String> stepTags) {
        HealSettings.Builder builder = HealSettings.builder();

        // Apply in order of increasing priority
        if (featureTags != null) {
            for (String tag : featureTags) {
                parseTag(tag, builder);
            }
        }

        if (scenarioTags != null) {
            for (String tag : scenarioTags) {
                parseTag(tag, builder);
            }
        }

        if (stepTags != null) {
            for (String tag : stepTags) {
                parseTag(tag, builder);
            }
        }

        return builder.build();
    }

    /**
     * Parse a single tag and apply to builder.
     */
    private void parseTag(String tag, HealSettings.Builder builder) {
        if (tag == null || !tag.toLowerCase().startsWith("@heal:")) {
            return;
        }

        Matcher matcher = HEAL_TAG_PATTERN.matcher(tag);
        if (!matcher.matches()) {
            logger.warn("Invalid heal tag format: {}", tag);
            return;
        }

        String directive = matcher.group(1).toLowerCase();
        String value = matcher.group(2);

        switch (directive) {
            case "off", "suggest", "auto_safe", "auto", "auto_all", "all" -> {
                HealPolicy policy = POLICY_MAP.get(directive);
                if (policy != null) {
                    builder.policy(policy);
                    logger.debug("Set heal policy to {} from tag {}", policy, tag);
                }
            }
            case "destructive" -> {
                builder.allowDestructive(true);
                logger.debug("Enabled destructive actions from tag {}", tag);
            }
            case "confidence" -> {
                if (value != null) {
                    try {
                        double confidence = Double.parseDouble(value);
                        builder.confidenceThreshold(confidence);
                        logger.debug("Set confidence threshold to {} from tag {}", confidence, tag);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid confidence value in tag {}: {}", tag, value);
                    }
                }
            }
            case "max_attempts" -> {
                if (value != null) {
                    try {
                        int maxAttempts = Integer.parseInt(value);
                        builder.maxAttempts(maxAttempts);
                        logger.debug("Set max attempts to {} from tag {}", maxAttempts, tag);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid max_attempts value in tag {}: {}", tag, value);
                    }
                }
            }
            case "timeout" -> {
                if (value != null) {
                    try {
                        int timeout = Integer.parseInt(value);
                        builder.timeoutSeconds(timeout);
                        logger.debug("Set timeout to {}s from tag {}", timeout, tag);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid timeout value in tag {}: {}", tag, value);
                    }
                }
            }
            default -> logger.debug("Unknown heal directive: {}", directive);
        }
    }

    /**
     * Check if any tags disable healing.
     */
    public boolean isHealingDisabled(Collection<String> tags) {
        HealSettings settings = parse(tags);
        return settings.getPolicy() == HealPolicy.OFF;
    }

    /**
     * Heal settings parsed from tags.
     */
    public static class HealSettings {
        private final HealPolicy policy;
        private final boolean allowDestructive;
        private final Double confidenceThreshold;
        private final Integer maxAttempts;
        private final Integer timeoutSeconds;

        private HealSettings(Builder builder) {
            this.policy = builder.policy;
            this.allowDestructive = builder.allowDestructive;
            this.confidenceThreshold = builder.confidenceThreshold;
            this.maxAttempts = builder.maxAttempts;
            this.timeoutSeconds = builder.timeoutSeconds;
        }

        public static Builder builder() {
            return new Builder();
        }

        public HealPolicy getPolicy() {
            return policy != null ? policy : HealPolicy.AUTO_SAFE;
        }

        public boolean isAllowDestructive() {
            return allowDestructive;
        }

        public Optional<Double> getConfidenceThreshold() {
            return Optional.ofNullable(confidenceThreshold);
        }

        public Optional<Integer> getMaxAttempts() {
            return Optional.ofNullable(maxAttempts);
        }

        public Optional<Integer> getTimeoutSeconds() {
            return Optional.ofNullable(timeoutSeconds);
        }

        public boolean isHealingEnabled() {
            return getPolicy() != HealPolicy.OFF;
        }

        public boolean isAutoHealEnabled() {
            HealPolicy p = getPolicy();
            return p == HealPolicy.AUTO_SAFE || p == HealPolicy.AUTO_ALL;
        }

        @Override
        public String toString() {
            return "HealSettings{policy=" + policy +
                   ", allowDestructive=" + allowDestructive +
                   ", confidenceThreshold=" + confidenceThreshold + "}";
        }

        public static class Builder {
            private HealPolicy policy;
            private boolean allowDestructive;
            private Double confidenceThreshold;
            private Integer maxAttempts;
            private Integer timeoutSeconds;

            public Builder policy(HealPolicy policy) {
                this.policy = policy;
                return this;
            }

            public Builder allowDestructive(boolean allow) {
                this.allowDestructive = allow;
                return this;
            }

            public Builder confidenceThreshold(double threshold) {
                this.confidenceThreshold = threshold;
                return this;
            }

            public Builder maxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
                return this;
            }

            public Builder timeoutSeconds(int timeout) {
                this.timeoutSeconds = timeout;
                return this;
            }

            public HealSettings build() {
                return new HealSettings(this);
            }
        }
    }
}
