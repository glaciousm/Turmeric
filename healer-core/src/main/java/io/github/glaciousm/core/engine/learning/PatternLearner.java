package io.github.glaciousm.core.engine.learning;

import io.github.glaciousm.core.feedback.FeedbackApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Learns patterns from user corrections to improve future healing decisions.
 * Tracks locator transformation patterns, element associations, and failure patterns.
 */
public class PatternLearner implements FeedbackApi.FeedbackListener {

    private static final Logger logger = LoggerFactory.getLogger(PatternLearner.class);

    private static final int MAX_PATTERNS_PER_TYPE = 1000;
    private static final double MIN_CONFIDENCE_TO_APPLY = 0.7;

    // Learned patterns
    private final Map<String, LocatorPattern> locatorPatterns = new ConcurrentHashMap<>();
    private final Map<String, ElementAssociation> elementAssociations = new ConcurrentHashMap<>();
    private final Map<String, FailurePattern> failurePatterns = new ConcurrentHashMap<>();

    // Statistics
    private int totalCorrections = 0;
    private int patternsApplied = 0;

    /**
     * Process feedback and learn patterns.
     */
    @Override
    public void onFeedback(FeedbackApi.FeedbackRecord record) {
        switch (record.getFeedbackType()) {
            case CORRECTION -> learnFromCorrection(record);
            case NEGATIVE -> learnFromNegative(record);
            case POSITIVE -> reinforcePattern(record);
            case FALSE_NEGATIVE -> learnFromFalseNegative(record);
        }
    }

    /**
     * Suggest a healed locator based on learned patterns.
     */
    public Optional<PatternSuggestion> suggestHeal(String originalLocator, String context) {
        List<PatternSuggestion> suggestions = new ArrayList<>();

        // Check direct locator patterns
        LocatorPattern directPattern = locatorPatterns.get(originalLocator);
        if (directPattern != null && directPattern.getConfidence() >= MIN_CONFIDENCE_TO_APPLY) {
            suggestions.add(new PatternSuggestion(
                    directPattern.getTargetLocator(),
                    directPattern.getConfidence(),
                    "Direct pattern match",
                    PatternType.DIRECT
            ));
        }

        // Check transformation patterns
        for (LocatorPattern pattern : locatorPatterns.values()) {
            if (pattern.isTransformPattern()) {
                Optional<String> transformed = applyTransformation(originalLocator, pattern);
                if (transformed.isPresent()) {
                    suggestions.add(new PatternSuggestion(
                            transformed.get(),
                            pattern.getConfidence() * 0.9, // Slightly lower confidence for transformations
                            "Transformation pattern: " + pattern.getDescription(),
                            PatternType.TRANSFORMATION
                    ));
                }
            }
        }

        // Check element associations
        for (ElementAssociation assoc : elementAssociations.values()) {
            if (assoc.matches(originalLocator, context)) {
                suggestions.add(new PatternSuggestion(
                        assoc.getSuggestedLocator(),
                        assoc.getConfidence(),
                        "Element association: " + assoc.getDescription(),
                        PatternType.ASSOCIATION
                ));
            }
        }

        // Return best suggestion
        return suggestions.stream()
                .max(Comparator.comparingDouble(PatternSuggestion::confidence));
    }

    /**
     * Check if a proposed heal matches a known failure pattern.
     */
    public boolean isKnownBadHeal(String originalLocator, String proposedLocator) {
        String key = createFailureKey(originalLocator, proposedLocator);
        FailurePattern pattern = failurePatterns.get(key);
        return pattern != null && pattern.getFailureCount() >= 2;
    }

    /**
     * Get confidence adjustment based on learned patterns.
     */
    public double getConfidenceAdjustment(String originalLocator, String proposedLocator) {
        // Check if this exact transformation has worked before
        LocatorPattern pattern = locatorPatterns.get(originalLocator);
        if (pattern != null && pattern.getTargetLocator().equals(proposedLocator)) {
            // Boost confidence based on success count
            return Math.min(0.2, pattern.getSuccessCount() * 0.02);
        }

        // Check if this transformation has failed before
        String failureKey = createFailureKey(originalLocator, proposedLocator);
        FailurePattern failure = failurePatterns.get(failureKey);
        if (failure != null) {
            // Reduce confidence based on failure count
            return -Math.min(0.3, failure.getFailureCount() * 0.05);
        }

        return 0;
    }

    /**
     * Get learned patterns statistics.
     */
    public PatternStats getStats() {
        return new PatternStats(
                locatorPatterns.size(),
                elementAssociations.size(),
                failurePatterns.size(),
                totalCorrections,
                patternsApplied,
                getTopPatterns(5)
        );
    }

    /**
     * Export patterns for persistence.
     */
    public PatternExport exportPatterns() {
        return new PatternExport(
                new ArrayList<>(locatorPatterns.values()),
                new ArrayList<>(elementAssociations.values()),
                new ArrayList<>(failurePatterns.values()),
                Instant.now()
        );
    }

    /**
     * Import patterns from persistence.
     */
    public void importPatterns(PatternExport export) {
        if (export.locatorPatterns() != null) {
            for (LocatorPattern p : export.locatorPatterns()) {
                locatorPatterns.put(p.getSourceLocator(), p);
            }
        }
        if (export.elementAssociations() != null) {
            for (ElementAssociation a : export.elementAssociations()) {
                elementAssociations.put(a.getId(), a);
            }
        }
        if (export.failurePatterns() != null) {
            for (FailurePattern f : export.failurePatterns()) {
                failurePatterns.put(f.getKey(), f);
            }
        }
        logger.info("Imported {} locator patterns, {} associations, {} failure patterns",
                locatorPatterns.size(), elementAssociations.size(), failurePatterns.size());
    }

    /**
     * Clear all learned patterns.
     */
    public void reset() {
        locatorPatterns.clear();
        elementAssociations.clear();
        failurePatterns.clear();
        totalCorrections = 0;
        patternsApplied = 0;
        logger.info("Pattern learner reset");
    }

    // Private learning methods

    private void learnFromCorrection(FeedbackApi.FeedbackRecord record) {
        totalCorrections++;

        String originalLocator = record.getOriginalLocator();
        String healedLocator = record.getHealedLocator();
        String correctLocator = record.getCorrectLocator();

        if (originalLocator == null || correctLocator == null) {
            return;
        }

        // Learn direct pattern: original -> correct
        LocatorPattern pattern = locatorPatterns.computeIfAbsent(
                originalLocator,
                k -> new LocatorPattern(originalLocator, correctLocator)
        );
        pattern.recordSuccess();
        pattern.setTargetLocator(correctLocator);

        // If healed was wrong, record failure pattern
        if (healedLocator != null && !healedLocator.equals(correctLocator)) {
            String failureKey = createFailureKey(originalLocator, healedLocator);
            FailurePattern failure = failurePatterns.computeIfAbsent(
                    failureKey,
                    k -> new FailurePattern(originalLocator, healedLocator)
            );
            failure.recordFailure();
        }

        // Try to learn transformation pattern
        learnTransformationPattern(originalLocator, correctLocator);

        // Learn element association if step text is available
        if (record.getStepText() != null) {
            learnElementAssociation(record.getStepText(), originalLocator, correctLocator);
        }

        enforcePatternLimits();
        logger.debug("Learned correction pattern: {} -> {}", originalLocator, correctLocator);
    }

    private void learnFromNegative(FeedbackApi.FeedbackRecord record) {
        String originalLocator = record.getOriginalLocator();
        String healedLocator = record.getHealedLocator();

        if (originalLocator != null && healedLocator != null) {
            String failureKey = createFailureKey(originalLocator, healedLocator);
            FailurePattern failure = failurePatterns.computeIfAbsent(
                    failureKey,
                    k -> new FailurePattern(originalLocator, healedLocator)
            );
            failure.recordFailure();

            // Also reduce confidence in any matching pattern
            LocatorPattern pattern = locatorPatterns.get(originalLocator);
            if (pattern != null && pattern.getTargetLocator().equals(healedLocator)) {
                pattern.recordFailure();
            }
        }
    }

    private void reinforcePattern(FeedbackApi.FeedbackRecord record) {
        String originalLocator = record.getOriginalLocator();
        String healedLocator = record.getHealedLocator();

        if (originalLocator != null && healedLocator != null) {
            LocatorPattern pattern = locatorPatterns.get(originalLocator);
            if (pattern != null && pattern.getTargetLocator().equals(healedLocator)) {
                pattern.recordSuccess();
                patternsApplied++;
            }
        }
    }

    private void learnFromFalseNegative(FeedbackApi.FeedbackRecord record) {
        String originalLocator = record.getOriginalLocator();
        String correctLocator = record.getCorrectLocator();

        if (originalLocator != null && correctLocator != null) {
            // Create pattern with lower initial confidence
            LocatorPattern pattern = new LocatorPattern(originalLocator, correctLocator);
            pattern.setConfidence(0.5); // Start lower for false negatives
            locatorPatterns.put(originalLocator, pattern);

            learnTransformationPattern(originalLocator, correctLocator);
        }
    }

    private void learnTransformationPattern(String source, String target) {
        // Try to identify common transformation patterns

        // ID change pattern: #old-id -> #new-id
        if (source.startsWith("#") && target.startsWith("#")) {
            String sourceId = source.substring(1);
            String targetId = target.substring(1);

            // Check for common prefixes/suffixes
            String commonPrefix = findCommonPrefix(sourceId, targetId);
            String commonSuffix = findCommonSuffix(sourceId, targetId);

            if (commonPrefix.length() > 2 || commonSuffix.length() > 2) {
                LocatorPattern transform = new LocatorPattern(source, target);
                transform.setTransformPattern(true);
                transform.setDescription("ID transformation: " + sourceId + " -> " + targetId);
                locatorPatterns.putIfAbsent(source + "_transform", transform);
            }
        }

        // Class change pattern
        if (source.contains(".") && target.contains(".")) {
            // Extract class names
            String sourceClass = extractClassName(source);
            String targetClass = extractClassName(target);

            if (sourceClass != null && targetClass != null && !sourceClass.equals(targetClass)) {
                String key = "class_transform_" + sourceClass;
                LocatorPattern transform = locatorPatterns.computeIfAbsent(
                        key,
                        k -> {
                            LocatorPattern p = new LocatorPattern(source, target);
                            p.setTransformPattern(true);
                            p.setSourceClass(sourceClass);
                            p.setTargetClass(targetClass);
                            p.setDescription("Class transformation: " + sourceClass + " -> " + targetClass);
                            return p;
                        }
                );
                transform.recordSuccess();
            }
        }
    }

    private void learnElementAssociation(String stepText, String originalLocator, String correctLocator) {
        // Extract keywords from step text
        Set<String> keywords = extractKeywords(stepText);

        if (!keywords.isEmpty()) {
            String id = "assoc_" + keywords.hashCode();
            ElementAssociation assoc = elementAssociations.computeIfAbsent(
                    id,
                    k -> new ElementAssociation(id, keywords, correctLocator)
            );
            assoc.addLocatorVariant(originalLocator);
            assoc.recordSuccess();
        }
    }

    private Optional<String> applyTransformation(String locator, LocatorPattern pattern) {
        if (!pattern.isTransformPattern()) {
            return Optional.empty();
        }

        // Apply class transformation
        if (pattern.getSourceClass() != null && pattern.getTargetClass() != null) {
            if (locator.contains(pattern.getSourceClass())) {
                return Optional.of(locator.replace(pattern.getSourceClass(), pattern.getTargetClass()));
            }
        }

        return Optional.empty();
    }

    private String createFailureKey(String original, String healed) {
        return original + "|||" + healed;
    }

    private String findCommonPrefix(String s1, String s2) {
        int minLen = Math.min(s1.length(), s2.length());
        int i = 0;
        while (i < minLen && s1.charAt(i) == s2.charAt(i)) {
            i++;
        }
        return s1.substring(0, i);
    }

    private String findCommonSuffix(String s1, String s2) {
        int minLen = Math.min(s1.length(), s2.length());
        int i = 0;
        while (i < minLen && s1.charAt(s1.length() - 1 - i) == s2.charAt(s2.length() - 1 - i)) {
            i++;
        }
        return s1.substring(s1.length() - i);
    }

    private String extractClassName(String locator) {
        int dotIndex = locator.indexOf('.');
        if (dotIndex >= 0) {
            int endIndex = locator.indexOf(' ', dotIndex);
            if (endIndex < 0) endIndex = locator.indexOf('[', dotIndex);
            if (endIndex < 0) endIndex = locator.length();
            return locator.substring(dotIndex + 1, endIndex);
        }
        return null;
    }

    private Set<String> extractKeywords(String text) {
        Set<String> stopWords = Set.of("a", "an", "the", "in", "on", "at", "to", "for", "of", "with", "i", "user", "when", "then", "and", "given");

        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 2 && !stopWords.contains(w))
                .collect(Collectors.toSet());
    }

    private List<LocatorPattern> getTopPatterns(int limit) {
        return locatorPatterns.values().stream()
                .sorted((a, b) -> Integer.compare(b.getSuccessCount(), a.getSuccessCount()))
                .limit(limit)
                .toList();
    }

    private void enforcePatternLimits() {
        if (locatorPatterns.size() > MAX_PATTERNS_PER_TYPE) {
            // Remove lowest confidence patterns
            List<String> toRemove = locatorPatterns.entrySet().stream()
                    .sorted(Comparator.comparingDouble(e -> e.getValue().getConfidence()))
                    .limit(locatorPatterns.size() - MAX_PATTERNS_PER_TYPE)
                    .map(Map.Entry::getKey)
                    .toList();
            toRemove.forEach(locatorPatterns::remove);
        }
    }

    // Inner types

    public enum PatternType {
        DIRECT,
        TRANSFORMATION,
        ASSOCIATION
    }

    public record PatternSuggestion(
            String suggestedLocator,
            double confidence,
            String explanation,
            PatternType type
    ) {}

    public record PatternStats(
            int locatorPatternCount,
            int associationCount,
            int failurePatternCount,
            int totalCorrections,
            int patternsApplied,
            List<LocatorPattern> topPatterns
    ) {}

    public record PatternExport(
            List<LocatorPattern> locatorPatterns,
            List<ElementAssociation> elementAssociations,
            List<FailurePattern> failurePatterns,
            Instant exportedAt
    ) {}

    public static class LocatorPattern {
        private String sourceLocator;
        private String targetLocator;
        private double confidence = 0.6;
        private int successCount = 0;
        private int failureCount = 0;
        private boolean transformPattern = false;
        private String description;
        private String sourceClass;
        private String targetClass;
        private Instant lastUsed;

        public LocatorPattern(String source, String target) {
            this.sourceLocator = source;
            this.targetLocator = target;
        }

        public void recordSuccess() {
            successCount++;
            confidence = Math.min(0.99, confidence + 0.05);
            lastUsed = Instant.now();
        }

        public void recordFailure() {
            failureCount++;
            confidence = Math.max(0.1, confidence - 0.1);
        }

        // Getters and setters
        public String getSourceLocator() { return sourceLocator; }
        public String getTargetLocator() { return targetLocator; }
        public void setTargetLocator(String target) { this.targetLocator = target; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public boolean isTransformPattern() { return transformPattern; }
        public void setTransformPattern(boolean transform) { this.transformPattern = transform; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSourceClass() { return sourceClass; }
        public void setSourceClass(String sourceClass) { this.sourceClass = sourceClass; }
        public String getTargetClass() { return targetClass; }
        public void setTargetClass(String targetClass) { this.targetClass = targetClass; }
    }

    public static class ElementAssociation {
        private String id;
        private Set<String> keywords;
        private String suggestedLocator;
        private Set<String> locatorVariants = new HashSet<>();
        private double confidence = 0.5;
        private int useCount = 0;

        public ElementAssociation(String id, Set<String> keywords, String locator) {
            this.id = id;
            this.keywords = keywords;
            this.suggestedLocator = locator;
        }

        public boolean matches(String locator, String context) {
            if (locatorVariants.contains(locator)) {
                return true;
            }
            if (context != null) {
                Set<String> contextKeywords = Arrays.stream(context.toLowerCase().split("\\W+"))
                        .collect(Collectors.toSet());
                long matchCount = keywords.stream().filter(contextKeywords::contains).count();
                return matchCount >= keywords.size() * 0.7;
            }
            return false;
        }

        public void addLocatorVariant(String locator) {
            locatorVariants.add(locator);
        }

        public void recordSuccess() {
            useCount++;
            confidence = Math.min(0.95, confidence + 0.03);
        }

        public String getId() { return id; }
        public String getSuggestedLocator() { return suggestedLocator; }
        public double getConfidence() { return confidence; }
        public String getDescription() { return "Keywords: " + String.join(", ", keywords); }
    }

    public static class FailurePattern {
        private String key;
        private String originalLocator;
        private String failedLocator;
        private int failureCount = 0;
        private Instant lastFailure;

        public FailurePattern(String original, String failed) {
            this.key = original + "|||" + failed;
            this.originalLocator = original;
            this.failedLocator = failed;
        }

        public void recordFailure() {
            failureCount++;
            lastFailure = Instant.now();
        }

        public String getKey() { return key; }
        public int getFailureCount() { return failureCount; }
    }
}
