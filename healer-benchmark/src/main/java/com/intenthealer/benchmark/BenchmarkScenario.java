/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package com.intenthealer.benchmark;

import com.intenthealer.benchmark.BenchmarkResult.ExpectedOutcome;
import com.intenthealer.benchmark.BenchmarkResult.ActualOutcome;
import com.intenthealer.core.config.GuardrailConfig;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.config.LlmConfig;
import com.intenthealer.core.engine.guardrails.GuardrailChecker;
import com.intenthealer.core.exception.LlmException;
import com.intenthealer.core.model.*;
import com.intenthealer.llm.LlmOrchestrator;
import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Base class for all benchmark scenarios.
 * Each scenario tests a specific healing situation with controlled HTML pages.
 * Calls the real LLM provider to measure actual healing accuracy.
 */
public abstract class BenchmarkScenario {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private static final LlmOrchestrator orchestrator = new LlmOrchestrator();
    private static final HtmlSnapshotParser htmlParser = new HtmlSnapshotParser();

    /**
     * Unique identifier for this scenario (e.g., "01", "02", etc.)
     */
    public abstract String getId();

    /**
     * Human-readable name for this scenario.
     */
    public abstract String getName();

    /**
     * Category this scenario belongs to (e.g., "Locator Changes", "Element Type Changes").
     */
    public abstract String getCategory();

    /**
     * Description of what this scenario tests.
     */
    public abstract String getDescription();

    /**
     * The expected outcome for this scenario.
     */
    public abstract ExpectedOutcome getExpectedOutcome();

    /**
     * The original (broken) locator that would fail on the "after" HTML.
     */
    public abstract By getOriginalLocator();

    /**
     * The correct locator that should be found after healing.
     * For REFUSE scenarios, this may return null.
     */
    public abstract By getExpectedHealedLocator();

    /**
     * Get the "before" HTML content (where the original locator works).
     */
    public abstract String getBeforeHtml();

    /**
     * Get the "after" HTML content (where the original locator is broken).
     */
    public abstract String getAfterHtml();

    /**
     * Get the URL pattern for guardrail checking (default: safe URL).
     */
    public String getPageUrl() {
        return "https://example.com/page";
    }

    /**
     * Get the action type for this scenario (default: CLICK).
     */
    public ActionType getActionType() {
        return ActionType.CLICK;
    }

    /**
     * Execute this benchmark scenario with real LLM calls.
     *
     * @param config The healer configuration
     * @return The benchmark result
     */
    public BenchmarkResult execute(HealerConfig config) {
        logger.info("Executing scenario {}: {}", getId(), getName());

        Instant startTime = Instant.now();
        String provider = config.getLlm() != null ? config.getLlm().getProvider() : "mock";
        String model = config.getLlm() != null ? config.getLlm().getModel() : "heuristic";

        BenchmarkResult.Builder resultBuilder = BenchmarkResult.builder()
            .scenarioId(getId())
            .scenarioName(getName())
            .category(getCategory())
            .expectedOutcome(getExpectedOutcome())
            .originalLocator(getOriginalLocator().toString())
            .llmProvider(provider)
            .llmModel(model);

        try {
            // Parse the "after" HTML into a UiSnapshot
            String afterHtml = getAfterHtml();
            UiSnapshot snapshot = htmlParser.parse(afterHtml, getPageUrl());

            // Create failure context
            FailureContext failure = createFailureContext();

            // Create default intent contract for the scenario
            IntentContract intent = createIntentContract();

            // Get guardrail config
            GuardrailConfig guardrailConfig = config.getGuardrails();
            if (guardrailConfig == null) {
                guardrailConfig = createDefaultGuardrailConfig();
            }
            GuardrailChecker guardrails = new GuardrailChecker(guardrailConfig);

            // PRE-LLM GUARDRAILS
            GuardrailResult urlCheck = guardrails.checkUrl(getPageUrl());
            if (urlCheck.isRefused()) {
                logger.info("Scenario {} REFUSED by URL guardrail: {}", getId(), urlCheck.getReason());
                return buildRefusedResult(resultBuilder, startTime, urlCheck.getReason());
            }

            GuardrailResult preCheck = guardrails.checkPreLlm(failure, intent);
            if (preCheck.isRefused()) {
                logger.info("Scenario {} REFUSED by pre-LLM guardrail: {}", getId(), preCheck.getReason());
                return buildRefusedResult(resultBuilder, startTime, preCheck.getReason());
            }

            // Call LLM to evaluate candidates
            LlmConfig llmConfig = config.getLlm();
            if (llmConfig == null) {
                llmConfig = createDefaultLlmConfig();
            }

            HealDecision decision = orchestrator.evaluateCandidates(
                failure, snapshot, intent, llmConfig);

            Duration latency = Duration.between(startTime, Instant.now());
            resultBuilder.latency(latency);

            // POST-LLM GUARDRAILS
            if (decision.canHeal() && decision.getSelectedElementIndex() != null) {
                ElementSnapshot chosenElement = snapshot.getElement(decision.getSelectedElementIndex()).orElse(null);
                if (chosenElement != null) {
                    GuardrailResult postCheck = guardrails.checkPostLlm(decision, chosenElement, snapshot);
                    if (postCheck.isRefused()) {
                        logger.info("Scenario {} REFUSED by post-LLM guardrail: {}", getId(), postCheck.getReason());
                        ActualOutcome outcome = ActualOutcome.REFUSED;
                        boolean passed = isOutcomeCorrect(outcome, getExpectedOutcome());
                        resultBuilder
                            .actualOutcome(outcome)
                            .passed(passed)
                            .confidence(decision.getConfidence())
                            .reasoning("Guardrail: " + postCheck.getReason());
                        return resultBuilder.build();
                    }
                }
            }

            // Evaluate the decision
            ActualOutcome actualOutcome = evaluateDecision(decision, snapshot);
            boolean passed = isOutcomeCorrect(actualOutcome, getExpectedOutcome());

            resultBuilder
                .actualOutcome(actualOutcome)
                .passed(passed)
                .confidence(decision.getConfidence())
                .reasoning(decision.getReasoning())
                .healedLocator(decision.getSelectedElementIndex() != null ?
                    formatHealedLocator(decision.getSelectedElementIndex()) : null);

            logger.info("Scenario {} {}: expected={}, actual={}, confidence={:.0f}%",
                getId(), passed ? "PASSED" : "FAILED", getExpectedOutcome(),
                actualOutcome, decision.getConfidence() * 100);

        } catch (LlmException e) {
            handleLlmError(e, resultBuilder, startTime);
        } catch (Exception e) {
            handleGenericError(e, resultBuilder, startTime);
        }

        return resultBuilder.build();
    }

    /**
     * Create intent contract for this scenario.
     * Subclasses can override to provide specific intent metadata.
     */
    protected IntentContract createIntentContract() {
        return IntentContract.builder()
            .action(getActionType().name())
            .description(getDescription())
            .policy(HealPolicy.AUTO_SAFE)
            .destructive(false)
            .build();
    }

    /**
     * Create failure context for this scenario.
     */
    protected FailureContext createFailureContext() {
        By locator = getOriginalLocator();
        LocatorInfo locatorInfo = byToLocatorInfo(locator);

        return FailureContext.builder()
            .stepText("Find element: " + locator.toString())
            .stepKeyword("When")
            .originalLocator(locatorInfo)
            .actionType(getActionType())
            .exceptionType("NoSuchElementException")
            .exceptionMessage("Unable to locate element: " + locator)
            .featureName("Benchmark")
            .scenarioName(getName())
            .build();
    }

    /**
     * Convert Selenium By to LocatorInfo.
     */
    protected LocatorInfo byToLocatorInfo(By by) {
        String byString = by.toString();

        if (byString.startsWith("By.id:")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.ID,
                byString.substring(7).trim());
        } else if (byString.startsWith("By.name:")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.NAME,
                byString.substring(9).trim());
        } else if (byString.startsWith("By.className:")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.CLASS_NAME,
                byString.substring(14).trim());
        } else if (byString.startsWith("By.cssSelector:")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS,
                byString.substring(16).trim());
        } else if (byString.startsWith("By.xpath:")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.XPATH,
                byString.substring(10).trim());
        } else if (byString.startsWith("By.linkText:")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.LINK_TEXT,
                byString.substring(13).trim());
        } else if (byString.startsWith("By.partialLinkText:")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT,
                byString.substring(20).trim());
        } else if (byString.startsWith("By.tagName:")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.TAG_NAME,
                byString.substring(12).trim());
        }

        // Default to CSS
        return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, byString);
    }

    /**
     * Evaluate the LLM decision to determine actual outcome.
     */
    protected ActualOutcome evaluateDecision(HealDecision decision, UiSnapshot snapshot) {
        if (decision == null || !decision.canHeal()) {
            return ActualOutcome.REFUSED;
        }

        double confidence = decision.getConfidence();
        double minConfidence = 0.75; // Default threshold

        if (confidence < minConfidence) {
            return ActualOutcome.LOW_CONFIDENCE;
        }

        // Check if the healed element is correct
        Integer chosenIndex = decision.getSelectedElementIndex();
        if (chosenIndex == null) {
            return ActualOutcome.REFUSED;
        }

        // For HEAL expected outcomes, verify the element was found
        if (getExpectedOutcome() == ExpectedOutcome.HEAL) {
            ElementSnapshot element = snapshot.getElement(chosenIndex).orElse(null);
            if (element != null) {
                // Element found - check if it matches expected
                if (matchesExpectedElement(element)) {
                    return ActualOutcome.HEALED_CORRECT;
                } else {
                    return ActualOutcome.HEALED_WRONG;
                }
            }
            return ActualOutcome.REFUSED;
        }

        // For DETECT_FALSE_HEAL, validate if LLM picked the wrong element
        if (getExpectedOutcome() == ExpectedOutcome.DETECT_FALSE_HEAL) {
            ElementSnapshot element = snapshot.getElement(chosenIndex).orElse(null);
            if (element != null) {
                // Check if the healed element matches what would be correct
                if (!matchesExpectedElement(element)) {
                    // LLM picked wrong element - we detected the false heal
                    return ActualOutcome.HEALED_WRONG;
                }
                // LLM somehow picked a correct element - unexpected success
                return ActualOutcome.HEALED_CORRECT;
            }
            return ActualOutcome.REFUSED;
        }

        // For REFUSE expected outcomes, validate that any heal is actually wrong
        if (getExpectedOutcome() == ExpectedOutcome.REFUSE) {
            ElementSnapshot element = snapshot.getElement(chosenIndex).orElse(null);
            if (element != null) {
                // Check if the healed element would be a valid replacement
                if (!matchesExpectedElement(element)) {
                    // LLM picked an invalid element - this is a wrong heal
                    return ActualOutcome.HEALED_WRONG;
                }
                // LLM found a valid element when we expected refusal
                return ActualOutcome.HEALED_CORRECT;
            }
            return ActualOutcome.REFUSED;
        }

        return ActualOutcome.HEALED_CORRECT;
    }

    /**
     * Check if the healed element matches expectations.
     * Subclasses can override for specific validation.
     */
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        By expected = getExpectedHealedLocator();
        if (expected == null) {
            return true; // No expected locator means any element is valid
        }

        String byString = expected.toString();

        // Match by ID
        if (byString.startsWith("By.id:")) {
            String expectedId = byString.substring(7).trim();
            return expectedId.equals(element.getId());
        }

        // Match by name attribute
        if (byString.startsWith("By.name:")) {
            String expectedName = byString.substring(9).trim();
            return expectedName.equals(element.getName());
        }

        // Match by class name
        if (byString.startsWith("By.className:")) {
            String expectedClass = byString.substring(14).trim();
            return element.getClasses() != null && element.getClasses().contains(expectedClass);
        }

        // Match by CSS selector (basic support)
        if (byString.startsWith("By.cssSelector:")) {
            return matchesCssSelector(byString.substring(16).trim(), element);
        }

        // Match by XPath with text() (basic support)
        if (byString.startsWith("By.xpath:") && byString.contains("text()")) {
            String expectedText = extractXPathText(byString);
            if (expectedText != null) {
                String elementText = element.getNormalizedText();
                return expectedText.equals(elementText);
            }
        }

        // Default: trust LLM
        return true;
    }

    /**
     * Match element against a CSS selector (basic support).
     */
    private boolean matchesCssSelector(String selector, ElementSnapshot element) {
        // Check tag name (if selector starts with a tag)
        if (selector.startsWith("input") && !"input".equalsIgnoreCase(element.getTagName())) {
            return false;
        }
        if (selector.startsWith("button") && !"button".equalsIgnoreCase(element.getTagName())) {
            return false;
        }
        if (selector.startsWith("a") && !"a".equalsIgnoreCase(element.getTagName())) {
            return false;
        }

        // Check type attribute
        if (selector.contains("[type='checkbox']") && !"checkbox".equals(element.getType())) {
            return false;
        }
        if (selector.contains("[type='submit']") && !"submit".equals(element.getType())) {
            return false;
        }

        // Check enabled state
        if (selector.contains(":not([disabled])") && !element.isEnabled()) {
            return false;
        }

        // Check ID in selector (#id)
        if (selector.contains("#")) {
            int idStart = selector.indexOf('#') + 1;
            int idEnd = idStart;
            while (idEnd < selector.length() &&
                   (Character.isLetterOrDigit(selector.charAt(idEnd)) ||
                    selector.charAt(idEnd) == '-' || selector.charAt(idEnd) == '_')) {
                idEnd++;
            }
            String expectedId = selector.substring(idStart, idEnd);
            if (!expectedId.equals(element.getId())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extract text from XPath text() expressions.
     */
    private String extractXPathText(String xpath) {
        // Match patterns like: text()='Submit' or text()="Submit"
        int textStart = xpath.indexOf("text()=");
        if (textStart >= 0) {
            textStart += 7; // Skip "text()="
            char quote = xpath.charAt(textStart);
            if (quote == '\'' || quote == '"') {
                int textEnd = xpath.indexOf(quote, textStart + 1);
                if (textEnd > textStart) {
                    return xpath.substring(textStart + 1, textEnd);
                }
            }
        }
        // Match contains(text(), 'something')
        int containsStart = xpath.indexOf("contains(text(),");
        if (containsStart >= 0) {
            containsStart += 16; // Skip "contains(text(),"
            while (containsStart < xpath.length() && xpath.charAt(containsStart) == ' ') {
                containsStart++;
            }
            char quote = xpath.charAt(containsStart);
            if (quote == '\'' || quote == '"') {
                int textEnd = xpath.indexOf(quote, containsStart + 1);
                if (textEnd > containsStart) {
                    return xpath.substring(containsStart + 1, textEnd);
                }
            }
        }
        return null;
    }

    /**
     * Check if the actual outcome matches the expected outcome.
     */
    protected boolean isOutcomeCorrect(ActualOutcome actual, ExpectedOutcome expected) {
        return switch (expected) {
            case HEAL -> actual == ActualOutcome.HEALED_CORRECT;
            case REFUSE -> actual == ActualOutcome.REFUSED;
            case LOW_CONFIDENCE -> actual == ActualOutcome.LOW_CONFIDENCE ||
                                   actual == ActualOutcome.REFUSED;
            // DETECT_FALSE_HEAL passes if we either refused, had low confidence,
            // or detected the wrong element (HEALED_WRONG means we caught the false heal)
            case DETECT_FALSE_HEAL -> actual == ActualOutcome.REFUSED ||
                                      actual == ActualOutcome.LOW_CONFIDENCE ||
                                      actual == ActualOutcome.HEALED_WRONG;
        };
    }

    /**
     * Format a healed locator from element index.
     */
    protected String formatHealedLocator(Integer elementIndex) {
        return "element[" + elementIndex + "]";
    }

    /**
     * Create default LLM config if none provided.
     */
    private LlmConfig createDefaultLlmConfig() {
        LlmConfig config = new LlmConfig();
        config.setProvider("mock");
        config.setModel("heuristic");
        config.setTimeoutSeconds(60);
        config.setMaxRetries(2);
        config.setConfidenceThreshold(0.75);
        return config;
    }

    /**
     * Create default guardrail config if none provided.
     */
    private GuardrailConfig createDefaultGuardrailConfig() {
        GuardrailConfig config = new GuardrailConfig();
        config.setMinConfidence(0.75);
        config.setMaxHealsPerScenario(10);
        return config;
    }

    /**
     * Build a REFUSED result for guardrail rejections.
     */
    private BenchmarkResult buildRefusedResult(BenchmarkResult.Builder builder,
                                                Instant startTime, String reason) {
        ActualOutcome outcome = ActualOutcome.REFUSED;
        boolean passed = isOutcomeCorrect(outcome, getExpectedOutcome());

        return builder
            .actualOutcome(outcome)
            .passed(passed)
            .confidence(0.0)
            .reasoning("Guardrail: " + reason)
            .latency(Duration.between(startTime, Instant.now()))
            .build();
    }

    /**
     * Handle LLM-specific errors.
     */
    private void handleLlmError(LlmException e, BenchmarkResult.Builder builder, Instant startTime) {
        logger.warn("Scenario {} LLM error: {}", getId(), e.getMessage());

        // LLM errors might indicate REFUSED behavior for some scenarios
        ActualOutcome outcome;
        if (e.getMessage() != null && e.getMessage().contains("refused")) {
            outcome = ActualOutcome.REFUSED;
        } else if (e.isRateLimited()) {
            outcome = ActualOutcome.ERROR;
        } else {
            outcome = ActualOutcome.REFUSED;
        }

        boolean passed = isOutcomeCorrect(outcome, getExpectedOutcome());

        builder
            .actualOutcome(outcome)
            .passed(passed)
            .confidence(0.0)
            .errorMessage(e.getMessage())
            .latency(Duration.between(startTime, Instant.now()));
    }

    /**
     * Handle generic errors.
     */
    private void handleGenericError(Exception e, BenchmarkResult.Builder builder, Instant startTime) {
        logger.error("Scenario {} ERROR: {}", getId(), e.getMessage(), e);
        builder
            .actualOutcome(ActualOutcome.ERROR)
            .errorMessage(e.getMessage())
            .passed(false)
            .latency(Duration.between(startTime, Instant.now()));
    }

    @Override
    public String toString() {
        return String.format("Scenario[%s]: %s (%s) - Expected: %s",
            getId(), getName(), getCategory(), getExpectedOutcome());
    }
}
