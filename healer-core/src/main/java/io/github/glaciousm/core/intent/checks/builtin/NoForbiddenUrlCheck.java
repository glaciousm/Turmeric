package io.github.glaciousm.core.intent.checks.builtin;

import io.github.glaciousm.core.model.ExecutionContext;
import io.github.glaciousm.core.model.IntentContract.InvariantCheck;
import io.github.glaciousm.core.model.InvariantResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Invariant check that verifies the current URL is not in a forbidden list.
 */
public class NoForbiddenUrlCheck implements InvariantCheck {

    private final List<Pattern> forbiddenPatterns;
    private final List<String> patternStrings;

    public NoForbiddenUrlCheck(List<String> forbiddenPatterns) {
        this.patternStrings = new ArrayList<>(forbiddenPatterns);
        this.forbiddenPatterns = forbiddenPatterns.stream()
                .map(Pattern::compile)
                .toList();
    }

    /**
     * Create a check with forbidden URL patterns.
     */
    public static NoForbiddenUrlCheck of(String... patterns) {
        return new NoForbiddenUrlCheck(List.of(patterns));
    }

    /**
     * Create a check that forbids error pages.
     */
    public static NoForbiddenUrlCheck noErrorPages() {
        return new NoForbiddenUrlCheck(List.of(
                ".*/(error|500|404|403|401).*",
                ".*/access-denied.*",
                ".*/unauthorized.*",
                ".*/forbidden.*"
        ));
    }

    /**
     * Create a check that forbids logout pages (for authenticated flows).
     */
    public static NoForbiddenUrlCheck noLogoutPages() {
        return new NoForbiddenUrlCheck(List.of(
                ".*/logout.*",
                ".*/signout.*",
                ".*/sign-out.*",
                ".*/logged-out.*"
        ));
    }

    /**
     * Create a combined check for common forbidden URLs.
     */
    public static NoForbiddenUrlCheck common() {
        return new NoForbiddenUrlCheck(List.of(
                ".*/(error|500|404|403|401).*",
                ".*/access-denied.*",
                ".*/unauthorized.*",
                ".*/forbidden.*",
                ".*/logout.*",
                ".*/signout.*"
        ));
    }

    @Override
    public InvariantResult verify(ExecutionContext ctx) {
        String currentUrl = ctx.getCurrentUrl();

        if (currentUrl == null) {
            return InvariantResult.satisfied("No URL to check");
        }

        for (int i = 0; i < forbiddenPatterns.size(); i++) {
            Pattern pattern = forbiddenPatterns.get(i);
            if (pattern.matcher(currentUrl).matches()) {
                return InvariantResult.violated(
                        "Navigated to forbidden URL: %s (matched pattern: %s)",
                        currentUrl, patternStrings.get(i));
            }
        }

        return InvariantResult.satisfied("URL is not forbidden");
    }

    @Override
    public String getDescription() {
        return "Verify URL is not in forbidden list: " + patternStrings;
    }
}
