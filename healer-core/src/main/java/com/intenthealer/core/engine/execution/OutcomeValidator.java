package com.intenthealer.core.engine.execution;

import com.intenthealer.core.model.*;
import com.intenthealer.core.model.IntentContract.InvariantCheck;
import com.intenthealer.core.model.IntentContract.OutcomeCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates outcomes after heal execution.
 * Runs outcome checks and invariant checks to verify the heal was successful.
 */
public class OutcomeValidator {

    private static final Logger logger = LoggerFactory.getLogger(OutcomeValidator.class);

    /**
     * Validate the outcome of a healed action.
     */
    public ValidationResult validate(ExecutionContext context, IntentContract intent) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Run outcome check if defined
        if (intent.getOutcomeCheck() != null) {
            OutcomeResult outcomeResult = runOutcomeCheck(context, intent.getOutcomeCheck());
            if (!outcomeResult.isPassed()) {
                failures.add("Outcome check failed: " + outcomeResult.getMessage());
                logger.warn("Outcome check failed: {}", outcomeResult.getMessage());
            } else {
                logger.debug("Outcome check passed: {}", outcomeResult.getMessage());
            }
        }

        // Run invariant checks
        for (Class<? extends InvariantCheck> invariantClass : intent.getInvariants()) {
            InvariantResult invariantResult = runInvariantCheck(context, invariantClass);
            if (invariantResult.isViolated()) {
                failures.add("Invariant violated: " + invariantResult.getMessage());
                logger.warn("Invariant violated: {}", invariantResult.getMessage());
            } else {
                logger.debug("Invariant satisfied: {}", invariantResult.getMessage());
            }
        }

        // Check for error indicators in the page
        checkForErrorIndicators(context, failures, warnings);

        boolean success = failures.isEmpty();
        return new ValidationResult(success, failures, warnings);
    }

    /**
     * Run a quick validation without full invariant checks.
     */
    public ValidationResult quickValidate(ExecutionContext context) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check basic indicators
        checkForErrorIndicators(context, failures, warnings);

        boolean success = failures.isEmpty();
        return new ValidationResult(success, failures, warnings);
    }

    /**
     * Run the outcome check.
     */
    private OutcomeResult runOutcomeCheck(ExecutionContext context, Class<? extends OutcomeCheck> checkClass) {
        try {
            OutcomeCheck check = checkClass.getDeclaredConstructor().newInstance();
            return check.verify(context);
        } catch (Exception e) {
            logger.error("Failed to run outcome check: {}", checkClass.getName(), e);
            return OutcomeResult.failed("Error running check: " + e.getMessage());
        }
    }

    /**
     * Run an invariant check.
     */
    private InvariantResult runInvariantCheck(ExecutionContext context, Class<? extends InvariantCheck> checkClass) {
        try {
            InvariantCheck check = checkClass.getDeclaredConstructor().newInstance();
            return check.verify(context);
        } catch (Exception e) {
            logger.error("Failed to run invariant check: {}", checkClass.getName(), e);
            return InvariantResult.violated("Error running check: " + e.getMessage());
        }
    }

    /**
     * Check for common error indicators in the page.
     */
    private void checkForErrorIndicators(ExecutionContext context, List<String> failures, List<String> warnings) {
        UiSnapshot snapshot = context.getAfterSnapshot();
        if (snapshot == null) {
            return;
        }

        String currentUrl = context.getCurrentUrl();

        // Check for error page URLs
        if (currentUrl != null) {
            String lowerUrl = currentUrl.toLowerCase();
            if (lowerUrl.contains("/error") || lowerUrl.contains("/500") ||
                lowerUrl.contains("/404") || lowerUrl.contains("/forbidden")) {
                failures.add("Redirected to error page: " + currentUrl);
            }
        }

        // Check page title for error indicators
        String title = context.getPageTitle();
        if (title != null) {
            String lowerTitle = title.toLowerCase();
            if (lowerTitle.contains("error") || lowerTitle.contains("not found") ||
                lowerTitle.contains("forbidden") || lowerTitle.contains("500") ||
                lowerTitle.contains("internal server")) {
                failures.add("Error page detected from title: " + title);
            }
        }

        // Check for visible error elements
        for (ElementSnapshot element : snapshot.getInteractiveElements()) {
            if (!element.isVisible()) {
                continue;
            }

            // Check for error classes
            if (element.getClasses() != null) {
                for (String cls : element.getClasses()) {
                    String lowerCls = cls.toLowerCase();
                    if (lowerCls.contains("error") || lowerCls.equals("alert-danger")) {
                        String text = element.getNormalizedText();
                        if (text != null && !text.isEmpty()) {
                            warnings.add("Possible error element: " + text);
                        }
                        break;
                    }
                }
            }

            // Check for alert roles
            if ("alert".equals(element.getAriaRole())) {
                String text = element.getNormalizedText();
                if (text != null && text.toLowerCase().contains("error")) {
                    failures.add("Error alert detected: " + text);
                }
            }
        }
    }

    /**
     * Result of validation.
     */
    public record ValidationResult(
            boolean success,
            List<String> failures,
            List<String> warnings
    ) {
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }

        public String getSummary() {
            if (success) {
                return hasWarnings()
                        ? "Validation passed with " + warnings.size() + " warnings"
                        : "Validation passed";
            } else {
                return "Validation failed: " + String.join("; ", failures);
            }
        }
    }
}
