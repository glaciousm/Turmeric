package com.intenthealer.core.engine.patch;

import com.intenthealer.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for tracking heals during test execution.
 * Stores pending heals and marks them as validated when tests pass.
 * Provides validated heals for auto-update processing.
 */
public class ValidatedHealRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ValidatedHealRegistry.class);

    /**
     * Pending heals awaiting validation, keyed by test ID.
     */
    private final ConcurrentHashMap<String, List<PendingHeal>> pendingHeals = new ConcurrentHashMap<>();

    /**
     * Validated heals ready for auto-update, keyed by test ID.
     */
    private final ConcurrentHashMap<String, List<ValidatedHeal>> validatedHeals = new ConcurrentHashMap<>();

    /**
     * All validated heals across all tests (for reporting).
     */
    private final List<ValidatedHeal> allValidatedHeals = Collections.synchronizedList(new ArrayList<>());

    /**
     * Records a heal that occurred during test execution.
     * The heal is stored as pending until the test passes.
     *
     * @param testId         unique identifier for the test
     * @param healResult     the result of the healing attempt
     * @param failureContext the context in which the heal occurred
     */
    public void recordHeal(String testId, HealResult healResult, FailureContext failureContext) {
        if (testId == null || healResult == null || !healResult.isSuccess()) {
            return;
        }

        PendingHeal pendingHeal = new PendingHeal(
                healResult.getId(),
                failureContext.getSourceLocation(),
                failureContext.getOriginalLocator(),
                healResult.getHealedLocator().orElse(null),
                healResult.getConfidence(),
                failureContext.getScenarioName(),
                healResult.getReasoning().orElse(null),
                Instant.now()
        );

        pendingHeals.computeIfAbsent(testId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(pendingHeal);

        logger.debug("Recorded pending heal {} for test {}", healResult.getId(), testId);
    }

    /**
     * Marks all pending heals for a test as validated (test passed).
     *
     * @param testId   unique identifier for the test
     * @param testName the name of the test that passed
     */
    public void markAsValidated(String testId, String testName) {
        List<PendingHeal> pending = pendingHeals.remove(testId);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        List<ValidatedHeal> validated = pending.stream()
                .map(p -> p.toValidatedHeal(testName))
                .collect(Collectors.toList());

        validatedHeals.computeIfAbsent(testId, k -> Collections.synchronizedList(new ArrayList<>()))
                .addAll(validated);

        allValidatedHeals.addAll(validated);

        logger.info("Marked {} heals as validated for test {}", validated.size(), testName);
    }

    /**
     * Discards all pending heals for a test (test failed).
     *
     * @param testId unique identifier for the test
     */
    public void discardPending(String testId) {
        List<PendingHeal> discarded = pendingHeals.remove(testId);
        if (discarded != null && !discarded.isEmpty()) {
            logger.debug("Discarded {} pending heals for failed test {}", discarded.size(), testId);
        }
    }

    /**
     * Gets all validated heals for a specific test.
     *
     * @param testId unique identifier for the test
     * @return list of validated heals, or empty list if none
     */
    public List<ValidatedHeal> getValidatedHeals(String testId) {
        List<ValidatedHeal> heals = validatedHeals.get(testId);
        return heals != null ? new ArrayList<>(heals) : Collections.emptyList();
    }

    /**
     * Gets all validated heals across all tests.
     *
     * @return list of all validated heals
     */
    public List<ValidatedHeal> getAllValidatedHeals() {
        return new ArrayList<>(allValidatedHeals);
    }

    /**
     * Gets validated heals that meet the confidence threshold and can be auto-updated.
     *
     * @param minConfidence minimum confidence threshold
     * @return list of heals eligible for auto-update
     */
    public List<ValidatedHeal> getHealsForAutoUpdate(double minConfidence) {
        return allValidatedHeals.stream()
                .filter(h -> h.canAutoUpdate())
                .filter(h -> h.meetsConfidenceThreshold(minConfidence))
                .collect(Collectors.toList());
    }

    /**
     * Gets validated heals for a specific test that meet the confidence threshold.
     *
     * @param testId        unique identifier for the test
     * @param minConfidence minimum confidence threshold
     * @return list of heals eligible for auto-update
     */
    public List<ValidatedHeal> getHealsForAutoUpdate(String testId, double minConfidence) {
        List<ValidatedHeal> heals = validatedHeals.get(testId);
        if (heals == null) {
            return Collections.emptyList();
        }

        return heals.stream()
                .filter(h -> h.canAutoUpdate())
                .filter(h -> h.meetsConfidenceThreshold(minConfidence))
                .collect(Collectors.toList());
    }

    /**
     * Clears validated heals after they have been processed.
     *
     * @param testId unique identifier for the test
     */
    public void clearValidated(String testId) {
        validatedHeals.remove(testId);
    }

    /**
     * Clears all registry data. Use for testing or cleanup.
     */
    public void clearAll() {
        pendingHeals.clear();
        validatedHeals.clear();
        allValidatedHeals.clear();
    }

    /**
     * Returns the number of pending heals awaiting validation.
     */
    public int getPendingCount() {
        return pendingHeals.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Returns the number of validated heals.
     */
    public int getValidatedCount() {
        return allValidatedHeals.size();
    }

    /**
     * Internal class representing a heal awaiting validation.
     */
    private static class PendingHeal {
        final String healId;
        final SourceLocation sourceLocation;
        final LocatorInfo originalLocator;
        final String healedLocator;
        final double confidence;
        final String scenarioName;
        final String reasoning;
        final Instant recordedAt;

        PendingHeal(String healId, SourceLocation sourceLocation, LocatorInfo originalLocator,
                    String healedLocator, double confidence, String scenarioName,
                    String reasoning, Instant recordedAt) {
            this.healId = healId;
            this.sourceLocation = sourceLocation;
            this.originalLocator = originalLocator;
            this.healedLocator = healedLocator;
            this.confidence = confidence;
            this.scenarioName = scenarioName;
            this.reasoning = reasoning;
            this.recordedAt = recordedAt;
        }

        ValidatedHeal toValidatedHeal(String testName) {
            return ValidatedHeal.builder()
                    .healId(healId)
                    .sourceLocation(sourceLocation)
                    .originalLocator(originalLocator != null ? originalLocator.getValue() : null)
                    .healedLocator(healedLocator)
                    .locatorStrategy(originalLocator != null ? originalLocator.getStrategy() : null)
                    .confidence(confidence)
                    .testName(testName)
                    .scenarioName(scenarioName)
                    .reasoning(reasoning)
                    .validatedAt(Instant.now())
                    .build();
        }
    }
}
