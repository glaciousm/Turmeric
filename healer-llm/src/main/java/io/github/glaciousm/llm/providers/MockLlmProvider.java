package io.github.glaciousm.llm.providers;

import io.github.glaciousm.core.config.LlmConfig;
import io.github.glaciousm.core.model.*;
import io.github.glaciousm.llm.LlmProvider;
import io.github.glaciousm.llm.LlmRequest;
import io.github.glaciousm.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Mock LLM provider for testing purposes.
 * Uses simple heuristics to select the best matching element without an actual LLM.
 *
 * This provider analyzes element attributes to find the most likely match:
 * - Looks for buttons, submit elements, login-related text
 * - Scores elements based on relevant attributes
 * - Returns the best match with simulated confidence
 */
public class MockLlmProvider implements LlmProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockLlmProvider.class);

    /**
     * Default constructor for registration in LlmOrchestrator.
     */
    public MockLlmProvider() {
        logger.info("MockLlmProvider initialized - using heuristic element matching (no LLM required)");
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        // Simple response for generic prompts
        return LlmResponse.builder()
                .success(true)
                .content("Mock response")
                .promptTokens(100)
                .completionTokens(50)
                .build();
    }

    @Override
    public HealDecision evaluateCandidates(
            FailureContext failure,
            UiSnapshot snapshot,
            IntentContract intent,
            LlmConfig config) {

        String stepText = failure.getStepText() != null ? failure.getStepText() : "";
        String originalLocator = failure.getOriginalLocator() != null ? failure.getOriginalLocator().getValue() : "";

        logger.info("MockLlmProvider evaluating {} candidates for: {}",
                snapshot.getInteractiveElements().size(), stepText);

        List<ElementSnapshot> elements = snapshot.getInteractiveElements();

        if (elements.isEmpty()) {
            return HealDecision.cannotHeal("No candidate elements found");
        }

        // Score each element based on heuristics
        int bestIndex = -1;
        double bestScore = 0;
        String bestReasoning = "";
        ElementSnapshot bestElement = null;

        for (ElementSnapshot element : elements) {
            double score = scoreElement(element, stepText, originalLocator);

            logger.debug("Element {}: {} - score: {}", element.getIndex(), element.getTagName(), score);

            if (score > bestScore) {
                bestScore = score;
                bestIndex = element.getIndex();
                bestReasoning = generateReasoning(element, stepText);
                bestElement = element;
            }
        }

        if (bestIndex >= 0 && bestScore > 0.3) {
            // Convert score to confidence (0.75 - 0.95 range)
            double confidence = 0.75 + (bestScore * 0.20);
            confidence = Math.min(0.95, confidence);

            logger.info("MockLlmProvider selected element {} with confidence {}: {}",
                    bestIndex, confidence, bestElement != null ? bestElement.getTagName() : "unknown");

            return HealDecision.canHeal(
                    bestIndex,
                    confidence,
                    bestReasoning
            );
        }

        return HealDecision.cannotHeal("No suitable element found matching the intent");
    }

    @Override
    public OutcomeResult validateOutcome(
            String expectedOutcome,
            UiSnapshot before,
            UiSnapshot after,
            LlmConfig config) {

        logger.info("MockLlmProvider validating outcome: {}", expectedOutcome);

        // Simple heuristic: check if page changed
        boolean urlChanged = !before.getUrl().equals(after.getUrl());
        boolean titleChanged = !before.getTitle().equals(after.getTitle());
        boolean elementsChanged = before.getInteractiveElements().size() != after.getInteractiveElements().size();

        String expectedLower = expectedOutcome.toLowerCase();

        // Check for navigation expectations
        if (expectedLower.contains("navigate") || expectedLower.contains("page") ||
            expectedLower.contains("redirect") || expectedLower.contains("url")) {
            if (urlChanged) {
                return OutcomeResult.passed("URL changed as expected: " + after.getUrl(), 0.85);
            }
        }

        // Check for login/success expectations
        if (expectedLower.contains("secure") || expectedLower.contains("success") ||
            expectedLower.contains("dashboard") || expectedLower.contains("logged in")) {
            if (after.getUrl().contains("secure") ||
                after.getTitle().toLowerCase().contains("secure")) {
                return OutcomeResult.passed("Secure area reached as expected", 0.90);
            }
        }

        // General change detection
        if (urlChanged || titleChanged || elementsChanged) {
            return OutcomeResult.passed("Page state changed as expected", 0.75);
        }

        return OutcomeResult.failed("Expected outcome not detected: " + expectedOutcome);
    }

    /**
     * Score an element based on how well it matches the expected action.
     * Enhanced to handle various difficulty levels of locator mismatches.
     */
    private double scoreElement(ElementSnapshot element, String stepText, String originalLocator) {
        double score = 0;
        String tagName = element.getTagName() != null ? element.getTagName().toLowerCase() : "";
        List<String> classes = element.getClasses();
        String classStr = classes != null ? String.join(" ", classes).toLowerCase() : "";

        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase();
        String id = Optional.ofNullable(element.getId()).orElse("").toLowerCase();
        String type = Optional.ofNullable(element.getType()).orElse("").toLowerCase();
        String name = Optional.ofNullable(element.getName()).orElse("").toLowerCase();

        String stepLower = stepText.toLowerCase();
        String locatorLower = originalLocator.toLowerCase();

        // ===== CHECKBOX DETECTION =====
        // Looking for checkbox-1 but actual is input[type=checkbox]
        boolean wantsCheckbox = locatorLower.contains("checkbox") || stepLower.contains("checkbox");
        if (wantsCheckbox && tagName.equals("input") && type.equals("checkbox")) {
            score += 0.8;
        }

        // ===== SELECT/DROPDOWN DETECTION =====
        // Looking for dropdown-menu but actual is select element
        boolean wantsDropdown = locatorLower.contains("dropdown") || locatorLower.contains("select") ||
                stepLower.contains("dropdown") || stepLower.contains("select");
        if (wantsDropdown && tagName.equals("select")) {
            score += 0.9;
        }

        // ===== NUMBER INPUT DETECTION =====
        boolean wantsNumberInput = locatorLower.contains("number") || stepLower.contains("number");
        if (wantsNumberInput && tagName.equals("input") && type.equals("number")) {
            score += 0.8;
        }

        // ===== BUTTON DETECTION (including when looking for link/submit) =====
        boolean wantsButton = stepLower.contains("click") || stepLower.contains("button") ||
                stepLower.contains("login") || stepLower.contains("submit") ||
                stepLower.contains("start") || stepLower.contains("add") ||
                locatorLower.contains("btn") || locatorLower.contains("button") ||
                locatorLower.contains("login") || locatorLower.contains("submit") ||
                locatorLower.contains("linktext");  // Looking for linkText but might be button

        boolean wantsInput = stepLower.contains("enter") || stepLower.contains("type") ||
                stepLower.contains("input") || stepLower.contains("username") ||
                stepLower.contains("password");

        // Tag matching for buttons
        if (wantsButton) {
            if (tagName.equals("button")) score += 0.5;
            if (tagName.equals("input") && type.equals("submit")) score += 0.4;
            if (tagName.equals("a") && classStr.contains("button")) score += 0.4;
            // Links that look like buttons
            if (tagName.equals("a") && (text.contains("login") || text.contains("sign") ||
                    text.contains("add") || text.contains("delete") || text.contains("edit"))) {
                score += 0.3;
            }
        }

        if (wantsInput) {
            if (tagName.equals("input") && (type.equals("text") || type.equals("password") || type.equals("email"))) {
                score += 0.4;
            }
        }

        // ===== TEXT CONTENT MATCHING =====
        // Very important for matching buttons/links by their visible text
        if (text.contains("add element") && locatorLower.contains("add")) score += 0.6;
        if (text.contains("delete") && locatorLower.contains("delete")) score += 0.6;
        if (text.contains("start") && (locatorLower.contains("start") || locatorLower.contains("submit"))) score += 0.6;
        if (text.contains("login") || text.contains("sign in") || text.contains("log in")) score += 0.3;
        if (text.contains("submit")) score += 0.2;

        // ===== ATTRIBUTE MATCHING =====
        if (id.contains("login") || id.contains("signin") || id.contains("submit")) score += 0.2;
        if (classStr.contains("login") || classStr.contains("submit") || classStr.contains("btn")) score += 0.15;
        if (type.equals("submit")) score += 0.2;

        // ===== SPECIFIC CLASS PATTERNS =====
        // Herokuapp specific patterns
        if (classStr.contains("radius")) score += 0.25;
        if (classStr.contains("button")) score += 0.2;
        if (classStr.contains("alert") || classStr.contains("success") || classStr.contains("error")) score += 0.15;
        if (classStr.contains("added-manually")) score += 0.3;  // Delete buttons on add/remove page

        // ===== DYNAMIC/CHALLENGING DOM PATTERNS =====
        // Links in tables that perform actions
        if (tagName.equals("a") && (text.contains("edit") || text.contains("delete"))) score += 0.3;

        // ===== PENALIZE HIDDEN/DISABLED =====
        if (!element.isVisible()) score -= 0.5;
        if (!element.isEnabled()) score -= 0.2;

        // Bonus for interactive elements
        if (element.isInteractable()) score += 0.1;

        return Math.max(0, Math.min(1, score));
    }

    /**
     * Generate human-readable reasoning for the selection.
     */
    private String generateReasoning(ElementSnapshot element, String stepText) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("Selected <").append(element.getTagName()).append("> element");

        if (element.getText() != null && !element.getText().isEmpty()) {
            reasoning.append(" with text '").append(element.getText()).append("'");
        }

        List<String> classes = element.getClasses();
        if (classes != null && !classes.isEmpty()) {
            reasoning.append(" (class: ").append(String.join(" ", classes)).append(")");
        }

        reasoning.append(" as the most likely match for action: ").append(stepText);

        return reasoning.toString();
    }
}
