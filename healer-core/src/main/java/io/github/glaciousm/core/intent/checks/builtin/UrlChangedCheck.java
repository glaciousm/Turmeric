package io.github.glaciousm.core.intent.checks.builtin;

import io.github.glaciousm.core.model.ExecutionContext;
import io.github.glaciousm.core.model.IntentContract.OutcomeCheck;
import io.github.glaciousm.core.model.OutcomeResult;

import java.util.regex.Pattern;

/**
 * Outcome check that verifies the URL changed to match an expected pattern.
 */
public class UrlChangedCheck implements OutcomeCheck {

    private final Pattern expectedPattern;
    private final String patternString;

    /**
     * Create a check that verifies URL matches the given regex pattern.
     */
    public UrlChangedCheck(String urlPattern) {
        this.patternString = urlPattern;
        this.expectedPattern = Pattern.compile(urlPattern);
    }

    /**
     * Create a check that verifies URL contains the given substring.
     */
    public static UrlChangedCheck contains(String substring) {
        return new UrlChangedCheck(".*" + Pattern.quote(substring) + ".*");
    }

    /**
     * Create a check that verifies URL starts with the given prefix.
     */
    public static UrlChangedCheck startsWith(String prefix) {
        return new UrlChangedCheck("^" + Pattern.quote(prefix) + ".*");
    }

    /**
     * Create a check that verifies URL ends with the given suffix.
     */
    public static UrlChangedCheck endsWith(String suffix) {
        return new UrlChangedCheck(".*" + Pattern.quote(suffix) + "$");
    }

    @Override
    public OutcomeResult verify(ExecutionContext ctx) {
        String currentUrl = ctx.getCurrentUrl();

        if (currentUrl == null) {
            return OutcomeResult.failed("Current URL is null");
        }

        boolean matches = expectedPattern.matcher(currentUrl).matches();

        if (matches) {
            return OutcomeResult.passed("URL matches expected pattern: " + patternString);
        } else {
            return OutcomeResult.failed(
                    "URL '%s' does not match pattern '%s'",
                    currentUrl, patternString);
        }
    }

    @Override
    public String getDescription() {
        return "Verify URL matches pattern: " + patternString;
    }
}
