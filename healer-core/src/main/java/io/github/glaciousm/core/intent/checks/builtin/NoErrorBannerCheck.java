package io.github.glaciousm.core.intent.checks.builtin;

import io.github.glaciousm.core.model.ElementSnapshot;
import io.github.glaciousm.core.model.ExecutionContext;
import io.github.glaciousm.core.model.IntentContract.InvariantCheck;
import io.github.glaciousm.core.model.InvariantResult;
import io.github.glaciousm.core.model.UiSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Invariant check that verifies no error banners are visible on the page.
 */
public class NoErrorBannerCheck implements InvariantCheck {

    private static final List<String> DEFAULT_ERROR_CLASSES = List.of(
            "error", "error-banner", "error-message",
            "alert-danger", "alert-error",
            "notification-error", "toast-error"
    );

    private static final List<String> DEFAULT_ERROR_ROLES = List.of(
            "alert", "alertdialog"
    );

    private static final Pattern ERROR_TEXT_PATTERN = Pattern.compile(
            "(?i)(error|failed|failure|invalid|incorrect|wrong|denied|rejected|unauthorized)",
            Pattern.CASE_INSENSITIVE
    );

    private final List<String> errorClasses;
    private final List<String> errorRoles;
    private final List<String> errorKeywords;

    public NoErrorBannerCheck() {
        this(DEFAULT_ERROR_CLASSES, DEFAULT_ERROR_ROLES, List.of());
    }

    public NoErrorBannerCheck(List<String> errorClasses, List<String> errorRoles, List<String> errorKeywords) {
        this.errorClasses = new ArrayList<>(errorClasses);
        this.errorRoles = new ArrayList<>(errorRoles);
        this.errorKeywords = new ArrayList<>(errorKeywords);
    }

    /**
     * Create a check with custom error class selectors.
     */
    public static NoErrorBannerCheck withClasses(String... classes) {
        return new NoErrorBannerCheck(List.of(classes), DEFAULT_ERROR_ROLES, List.of());
    }

    /**
     * Create a check with custom error keywords.
     */
    public static NoErrorBannerCheck withKeywords(String... keywords) {
        return new NoErrorBannerCheck(DEFAULT_ERROR_CLASSES, DEFAULT_ERROR_ROLES, List.of(keywords));
    }

    @Override
    public InvariantResult verify(ExecutionContext ctx) {
        UiSnapshot snapshot = ctx.getAfterSnapshot();

        if (snapshot == null) {
            return InvariantResult.satisfied("No snapshot available to check");
        }

        Optional<ErrorInfo> errorFound = findErrorElement(snapshot);

        if (errorFound.isPresent()) {
            ErrorInfo error = errorFound.get();
            return InvariantResult.violated(
                    "Error banner detected: %s",
                    error.text != null ? error.text : error.reason);
        }

        return InvariantResult.satisfied("No error banners detected");
    }

    private Optional<ErrorInfo> findErrorElement(UiSnapshot snapshot) {
        for (ElementSnapshot element : snapshot.getInteractiveElements()) {
            // Check by class
            if (hasErrorClass(element)) {
                if (element.isVisible()) {
                    return Optional.of(new ErrorInfo("error class", element.getNormalizedText()));
                }
            }

            // Check by role
            String role = element.getAriaRole();
            if (role != null && errorRoles.contains(role.toLowerCase())) {
                if (element.isVisible()) {
                    return Optional.of(new ErrorInfo("alert role", element.getNormalizedText()));
                }
            }

            // Check by text content
            String text = element.getNormalizedText();
            if (text != null && !text.isEmpty()) {
                // Check custom keywords
                for (String keyword : errorKeywords) {
                    if (text.toLowerCase().contains(keyword.toLowerCase())) {
                        return Optional.of(new ErrorInfo("keyword: " + keyword, text));
                    }
                }

                // Check default error pattern
                if (ERROR_TEXT_PATTERN.matcher(text).find() && hasErrorClass(element)) {
                    return Optional.of(new ErrorInfo("error text pattern", text));
                }
            }
        }

        return Optional.empty();
    }

    private boolean hasErrorClass(ElementSnapshot element) {
        if (element.getClasses() == null) {
            return false;
        }
        return element.getClasses().stream()
                .anyMatch(cls -> errorClasses.stream()
                        .anyMatch(ec -> cls.toLowerCase().contains(ec.toLowerCase())));
    }

    @Override
    public String getDescription() {
        return "Verify no error banners are visible";
    }

    private record ErrorInfo(String reason, String text) {}
}
