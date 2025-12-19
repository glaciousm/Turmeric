package io.github.glaciousm.core.engine.validation;

import io.github.glaciousm.core.config.LlmConfig;
import io.github.glaciousm.core.model.OutcomeResult;
import io.github.glaciousm.core.model.UiSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * LLM-based outcome validator that uses semantic understanding
 * to determine if expected test outcomes were achieved after healing.
 */
public class LlmOutcomeValidator {

    private static final Logger logger = LoggerFactory.getLogger(LlmOutcomeValidator.class);

    private final Function<ValidationRequest, OutcomeResult> llmValidator;
    private final List<OutcomeCheck> customChecks = new ArrayList<>();

    /**
     * Create validator with LLM validation function.
     */
    public LlmOutcomeValidator(Function<ValidationRequest, OutcomeResult> llmValidator) {
        this.llmValidator = llmValidator;
    }

    /**
     * Validate outcome using LLM reasoning.
     */
    public ValidationResult validate(
            String expectedOutcome,
            UiSnapshot beforeHeal,
            UiSnapshot afterHeal,
            Map<String, Object> context) {

        long startTime = System.currentTimeMillis();
        List<CheckResult> checkResults = new ArrayList<>();

        // Run custom checks first
        for (OutcomeCheck check : customChecks) {
            try {
                CheckResult result = check.evaluate(beforeHeal, afterHeal, context);
                checkResults.add(result);

                if (!result.passed() && check.isBlocking()) {
                    // Blocking check failed - no need to continue
                    return ValidationResult.failed(
                            "Blocking check failed: " + check.getName(),
                            checkResults,
                            System.currentTimeMillis() - startTime
                    );
                }
            } catch (Exception e) {
                logger.warn("Custom check '{}' threw exception: {}", check.getName(), e.getMessage());
                checkResults.add(new CheckResult(check.getName(), false, "Exception: " + e.getMessage()));
            }
        }

        // Use LLM for semantic validation
        if (llmValidator != null && expectedOutcome != null && !expectedOutcome.isEmpty()) {
            try {
                ValidationRequest request = new ValidationRequest(
                        expectedOutcome,
                        beforeHeal,
                        afterHeal,
                        context
                );

                OutcomeResult llmResult = llmValidator.apply(request);

                checkResults.add(new CheckResult(
                        "LLM Semantic Validation",
                        llmResult.isValid(),
                        llmResult.getReasoning()
                ));

                if (!llmResult.isValid()) {
                    return ValidationResult.builder()
                            .passed(false)
                            .reason("LLM validation failed: " + llmResult.getReasoning())
                            .checkResults(checkResults)
                            .confidence(llmResult.getConfidence())
                            .llmReasoning(llmResult.getReasoning())
                            .latencyMs(System.currentTimeMillis() - startTime)
                            .build();
                }

                return ValidationResult.builder()
                        .passed(true)
                        .reason("All validations passed")
                        .checkResults(checkResults)
                        .confidence(llmResult.getConfidence())
                        .llmReasoning(llmResult.getReasoning())
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();

            } catch (Exception e) {
                logger.error("LLM validation failed with exception", e);
                checkResults.add(new CheckResult("LLM Semantic Validation", false, "Error: " + e.getMessage()));

                // Fall back to custom checks only
                boolean allPassed = checkResults.stream()
                        .filter(r -> !"LLM Semantic Validation".equals(r.checkName()))
                        .allMatch(CheckResult::passed);

                return ValidationResult.builder()
                        .passed(allPassed)
                        .reason(allPassed ? "Custom checks passed (LLM unavailable)" : "Validation failed")
                        .checkResults(checkResults)
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }
        }

        // No LLM validator - use custom checks only
        boolean allPassed = checkResults.isEmpty() || checkResults.stream().allMatch(CheckResult::passed);
        return ValidationResult.builder()
                .passed(allPassed)
                .reason(allPassed ? "All custom checks passed" : "One or more checks failed")
                .checkResults(checkResults)
                .latencyMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * Validate with simple expected outcome string.
     */
    public ValidationResult validate(String expectedOutcome, UiSnapshot before, UiSnapshot after) {
        return validate(expectedOutcome, before, after, Map.of());
    }

    /**
     * Register a custom outcome check.
     */
    public void addCheck(OutcomeCheck check) {
        customChecks.add(check);
    }

    /**
     * Remove a custom check by name.
     */
    public void removeCheck(String name) {
        customChecks.removeIf(c -> c.getName().equals(name));
    }

    /**
     * Clear all custom checks.
     */
    public void clearChecks() {
        customChecks.clear();
    }

    /**
     * Create common outcome checks.
     */
    public static class CommonChecks {

        /**
         * Check that no error messages appeared.
         */
        public static OutcomeCheck noErrorMessages() {
            return new OutcomeCheck() {
                @Override
                public String getName() { return "No Error Messages"; }

                @Override
                public CheckResult evaluate(UiSnapshot before, UiSnapshot after, Map<String, Object> ctx) {
                    if (after == null || after.getHtml() == null) {
                        return new CheckResult(getName(), true, "No snapshot to check");
                    }

                    String html = after.getHtml().toLowerCase();
                    List<String> errorPatterns = List.of(
                            "error", "exception", "failed", "invalid",
                            "class=\"error\"", "class=\"alert-danger\"",
                            "aria-invalid=\"true\""
                    );

                    for (String pattern : errorPatterns) {
                        if (html.contains(pattern)) {
                            // Check if it was already present before
                            if (before != null && before.getHtml() != null &&
                                    before.getHtml().toLowerCase().contains(pattern)) {
                                continue; // Was already there
                            }
                            return new CheckResult(getName(), false,
                                    "New error indicator found: " + pattern);
                        }
                    }

                    return new CheckResult(getName(), true, "No new error indicators");
                }

                @Override
                public boolean isBlocking() { return true; }
            };
        }

        /**
         * Check that page didn't navigate unexpectedly.
         */
        public static OutcomeCheck samePageUrl() {
            return new OutcomeCheck() {
                @Override
                public String getName() { return "Same Page URL"; }

                @Override
                public CheckResult evaluate(UiSnapshot before, UiSnapshot after, Map<String, Object> ctx) {
                    if (before == null || after == null) {
                        return new CheckResult(getName(), true, "Cannot compare URLs");
                    }

                    String beforeUrl = before.getUrl();
                    String afterUrl = after.getUrl();

                    if (beforeUrl == null || afterUrl == null) {
                        return new CheckResult(getName(), true, "URLs not available");
                    }

                    // Allow same page or expected navigation
                    if (beforeUrl.equals(afterUrl)) {
                        return new CheckResult(getName(), true, "URL unchanged");
                    }

                    // Check for expected navigation in context
                    Object expectedNav = ctx.get("expectedNavigation");
                    if (expectedNav != null && afterUrl.contains(expectedNav.toString())) {
                        return new CheckResult(getName(), true, "Expected navigation occurred");
                    }

                    return new CheckResult(getName(), false,
                            "Unexpected navigation: " + beforeUrl + " -> " + afterUrl);
                }

                @Override
                public boolean isBlocking() { return false; }
            };
        }

        /**
         * Check that target element exists after heal.
         */
        public static OutcomeCheck elementExists(String locatorDescription) {
            return new OutcomeCheck() {
                @Override
                public String getName() { return "Element Exists: " + locatorDescription; }

                @Override
                public CheckResult evaluate(UiSnapshot before, UiSnapshot after, Map<String, Object> ctx) {
                    Object healedLocator = ctx.get("healedLocator");
                    if (healedLocator == null) {
                        return new CheckResult(getName(), true, "No healed locator to verify");
                    }

                    if (after == null || after.getHtml() == null) {
                        return new CheckResult(getName(), false, "No snapshot available");
                    }

                    // Simple check - look for locator pattern in HTML
                    String locator = healedLocator.toString();
                    String html = after.getHtml();

                    if (locator.startsWith("#")) {
                        String id = locator.substring(1);
                        if (html.contains("id=\"" + id + "\"") || html.contains("id='" + id + "'")) {
                            return new CheckResult(getName(), true, "Element with ID found");
                        }
                    }

                    return new CheckResult(getName(), true, "Element verification skipped");
                }

                @Override
                public boolean isBlocking() { return false; }
            };
        }

        /**
         * Check that specific text appears on page.
         */
        public static OutcomeCheck textPresent(String expectedText) {
            return new OutcomeCheck() {
                @Override
                public String getName() { return "Text Present: " + expectedText; }

                @Override
                public CheckResult evaluate(UiSnapshot before, UiSnapshot after, Map<String, Object> ctx) {
                    if (after == null || after.getHtml() == null) {
                        return new CheckResult(getName(), false, "No snapshot available");
                    }

                    if (after.getHtml().contains(expectedText)) {
                        return new CheckResult(getName(), true, "Expected text found");
                    }

                    return new CheckResult(getName(), false, "Expected text not found: " + expectedText);
                }

                @Override
                public boolean isBlocking() { return true; }
            };
        }

        /**
         * Check that specific text is NOT present.
         */
        public static OutcomeCheck textAbsent(String unexpectedText) {
            return new OutcomeCheck() {
                @Override
                public String getName() { return "Text Absent: " + unexpectedText; }

                @Override
                public CheckResult evaluate(UiSnapshot before, UiSnapshot after, Map<String, Object> ctx) {
                    if (after == null || after.getHtml() == null) {
                        return new CheckResult(getName(), true, "No snapshot available");
                    }

                    if (!after.getHtml().contains(unexpectedText)) {
                        return new CheckResult(getName(), true, "Unexpected text not found (good)");
                    }

                    return new CheckResult(getName(), false, "Unexpected text found: " + unexpectedText);
                }

                @Override
                public boolean isBlocking() { return true; }
            };
        }

        /**
         * Check page title matches expected.
         */
        public static OutcomeCheck pageTitle(String expectedTitle) {
            return new OutcomeCheck() {
                @Override
                public String getName() { return "Page Title: " + expectedTitle; }

                @Override
                public CheckResult evaluate(UiSnapshot before, UiSnapshot after, Map<String, Object> ctx) {
                    if (after == null) {
                        return new CheckResult(getName(), false, "No snapshot available");
                    }

                    String title = after.getTitle();
                    if (title != null && title.contains(expectedTitle)) {
                        return new CheckResult(getName(), true, "Title matches");
                    }

                    return new CheckResult(getName(), false,
                            "Title mismatch: expected '" + expectedTitle + "', got '" + title + "'");
                }

                @Override
                public boolean isBlocking() { return false; }
            };
        }
    }

    // Types

    /**
     * Request for LLM validation.
     */
    public record ValidationRequest(
            String expectedOutcome,
            UiSnapshot beforeSnapshot,
            UiSnapshot afterSnapshot,
            Map<String, Object> context
    ) {}

    /**
     * Result of a single check.
     */
    public record CheckResult(
            String checkName,
            boolean passed,
            String details
    ) {}

    /**
     * Overall validation result.
     */
    public static class ValidationResult {
        private final boolean passed;
        private final String reason;
        private final List<CheckResult> checkResults;
        private final double confidence;
        private final String llmReasoning;
        private final long latencyMs;

        private ValidationResult(Builder builder) {
            this.passed = builder.passed;
            this.reason = builder.reason;
            this.checkResults = builder.checkResults;
            this.confidence = builder.confidence;
            this.llmReasoning = builder.llmReasoning;
            this.latencyMs = builder.latencyMs;
        }

        public static ValidationResult failed(String reason, List<CheckResult> results, long latencyMs) {
            return builder()
                    .passed(false)
                    .reason(reason)
                    .checkResults(results)
                    .latencyMs(latencyMs)
                    .build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean isPassed() { return passed; }
        public String getReason() { return reason; }
        public List<CheckResult> getCheckResults() { return checkResults; }
        public double getConfidence() { return confidence; }
        public String getLlmReasoning() { return llmReasoning; }
        public long getLatencyMs() { return latencyMs; }

        public static class Builder {
            private boolean passed;
            private String reason;
            private List<CheckResult> checkResults = new ArrayList<>();
            private double confidence;
            private String llmReasoning;
            private long latencyMs;

            public Builder passed(boolean passed) { this.passed = passed; return this; }
            public Builder reason(String reason) { this.reason = reason; return this; }
            public Builder checkResults(List<CheckResult> results) { this.checkResults = results; return this; }
            public Builder confidence(double confidence) { this.confidence = confidence; return this; }
            public Builder llmReasoning(String reasoning) { this.llmReasoning = reasoning; return this; }
            public Builder latencyMs(long latencyMs) { this.latencyMs = latencyMs; return this; }

            public ValidationResult build() {
                return new ValidationResult(this);
            }
        }
    }

    /**
     * Interface for custom outcome checks.
     */
    public interface OutcomeCheck {
        String getName();
        CheckResult evaluate(UiSnapshot before, UiSnapshot after, Map<String, Object> context);
        default boolean isBlocking() { return false; }
    }
}
