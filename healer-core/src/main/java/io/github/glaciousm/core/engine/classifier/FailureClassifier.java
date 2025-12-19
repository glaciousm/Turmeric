package io.github.glaciousm.core.engine.classifier;

import io.github.glaciousm.core.model.FailureKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classifies test failures to determine if they are healable.
 */
public class FailureClassifier {

    private static final Logger logger = LoggerFactory.getLogger(FailureClassifier.class);

    /**
     * Selenium exception class names mapped to failure kinds.
     */
    private static final Map<String, FailureKind> EXCEPTION_MAPPING = Map.ofEntries(
            // Element not found
            Map.entry("NoSuchElementException", FailureKind.ELEMENT_NOT_FOUND),
            Map.entry("org.openqa.selenium.NoSuchElementException", FailureKind.ELEMENT_NOT_FOUND),

            // Stale element
            Map.entry("StaleElementReferenceException", FailureKind.STALE_ELEMENT),
            Map.entry("org.openqa.selenium.StaleElementReferenceException", FailureKind.STALE_ELEMENT),

            // Click intercepted
            Map.entry("ElementClickInterceptedException", FailureKind.CLICK_INTERCEPTED),
            Map.entry("org.openqa.selenium.ElementClickInterceptedException", FailureKind.CLICK_INTERCEPTED),

            // Not interactable
            Map.entry("ElementNotInteractableException", FailureKind.NOT_INTERACTABLE),
            Map.entry("org.openqa.selenium.ElementNotInteractableException", FailureKind.NOT_INTERACTABLE),
            Map.entry("ElementNotVisibleException", FailureKind.NOT_INTERACTABLE),

            // Timeout
            Map.entry("TimeoutException", FailureKind.TIMEOUT),
            Map.entry("org.openqa.selenium.TimeoutException", FailureKind.TIMEOUT),

            // Assertion failures - never heal
            Map.entry("AssertionError", FailureKind.ASSERTION_FAILURE),
            Map.entry("java.lang.AssertionError", FailureKind.ASSERTION_FAILURE),
            Map.entry("ComparisonFailure", FailureKind.ASSERTION_FAILURE),
            Map.entry("org.junit.ComparisonFailure", FailureKind.ASSERTION_FAILURE),
            Map.entry("AssertionFailedError", FailureKind.ASSERTION_FAILURE),
            Map.entry("org.opentest4j.AssertionFailedError", FailureKind.ASSERTION_FAILURE)
    );

    /**
     * Patterns to detect assertion failures from step text.
     */
    private static final Pattern ASSERTION_STEP_PATTERN = Pattern.compile(
            "(?i)(should|must|verify|assert|expect|check|ensure|confirm|validate)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Classify an exception into a failure kind.
     */
    public FailureKind classify(Throwable exception) {
        if (exception == null) {
            return FailureKind.UNKNOWN;
        }

        // Check the exception class name
        String className = exception.getClass().getName();
        String simpleName = exception.getClass().getSimpleName();

        // Try exact match first
        if (EXCEPTION_MAPPING.containsKey(className)) {
            return EXCEPTION_MAPPING.get(className);
        }
        if (EXCEPTION_MAPPING.containsKey(simpleName)) {
            return EXCEPTION_MAPPING.get(simpleName);
        }

        // Check for assertion errors by inheritance
        if (exception instanceof AssertionError) {
            return FailureKind.ASSERTION_FAILURE;
        }

        // Check message for clues
        String message = exception.getMessage();
        if (message != null) {
            FailureKind kindFromMessage = classifyByMessage(message);
            if (kindFromMessage != FailureKind.UNKNOWN) {
                return kindFromMessage;
            }
        }

        // Check wrapped exception
        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            FailureKind kindFromCause = classify(cause);
            if (kindFromCause != FailureKind.UNKNOWN) {
                return kindFromCause;
            }
        }

        logger.debug("Unknown exception type: {}", className);
        return FailureKind.UNKNOWN;
    }

    /**
     * Classify an exception into a failure kind with step text context.
     */
    public FailureKind classify(Throwable exception, String stepText) {
        // First check if step text suggests an assertion
        if (stepText != null && isAssertionStepText(stepText)) {
            return FailureKind.ASSERTION_FAILURE;
        }

        return classify(exception);
    }

    /**
     * Check if a failure kind is potentially healable.
     */
    public boolean isHealable(FailureKind kind) {
        return kind != null && kind.isHealable();
    }

    /**
     * Check if an exception is potentially healable.
     */
    public boolean isHealable(Throwable exception) {
        return isHealable(classify(exception));
    }

    /**
     * Check if step text suggests an assertion (verification) step.
     */
    public boolean isAssertionStepText(String stepText) {
        if (stepText == null || stepText.isEmpty()) {
            return false;
        }
        return ASSERTION_STEP_PATTERN.matcher(stepText).find();
    }

    /**
     * Check if a step keyword indicates an assertion step.
     */
    public boolean isAssertionKeyword(String keyword) {
        if (keyword == null) {
            return false;
        }
        String normalized = keyword.trim().toLowerCase();
        return normalized.equals("then") || normalized.equals("and") &&
               // "And" after "Then" is also assertion - this needs context
               false; // Conservative: only "Then" is definitely assertion
    }

    private FailureKind classifyByMessage(String message) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("no such element") ||
            lowerMessage.contains("unable to locate")) {
            return FailureKind.ELEMENT_NOT_FOUND;
        }

        if (lowerMessage.contains("stale element")) {
            return FailureKind.STALE_ELEMENT;
        }

        if (lowerMessage.contains("element click intercepted") ||
            lowerMessage.contains("other element would receive the click")) {
            return FailureKind.CLICK_INTERCEPTED;
        }

        if (lowerMessage.contains("element not interactable") ||
            lowerMessage.contains("element is not clickable")) {
            return FailureKind.NOT_INTERACTABLE;
        }

        if (lowerMessage.contains("timed out") ||
            lowerMessage.contains("timeout")) {
            return FailureKind.TIMEOUT;
        }

        return FailureKind.UNKNOWN;
    }
}
