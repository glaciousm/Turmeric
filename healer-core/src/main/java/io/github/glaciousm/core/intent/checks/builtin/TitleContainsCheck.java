package io.github.glaciousm.core.intent.checks.builtin;

import io.github.glaciousm.core.model.ExecutionContext;
import io.github.glaciousm.core.model.IntentContract.OutcomeCheck;
import io.github.glaciousm.core.model.OutcomeResult;

/**
 * Outcome check that verifies the page title contains expected text.
 */
public class TitleContainsCheck implements OutcomeCheck {

    private final String expectedText;
    private final boolean caseSensitive;

    public TitleContainsCheck(String expectedText) {
        this(expectedText, false);
    }

    public TitleContainsCheck(String expectedText, boolean caseSensitive) {
        this.expectedText = expectedText;
        this.caseSensitive = caseSensitive;
    }

    /**
     * Create a case-insensitive title check.
     */
    public static TitleContainsCheck of(String text) {
        return new TitleContainsCheck(text, false);
    }

    /**
     * Create a case-sensitive title check.
     */
    public static TitleContainsCheck caseSensitive(String text) {
        return new TitleContainsCheck(text, true);
    }

    @Override
    public OutcomeResult verify(ExecutionContext ctx) {
        String currentTitle = ctx.getCurrentTitle();

        if (currentTitle == null) {
            return OutcomeResult.failed("Page title is null");
        }

        boolean contains;
        if (caseSensitive) {
            contains = currentTitle.contains(expectedText);
        } else {
            contains = currentTitle.toLowerCase().contains(expectedText.toLowerCase());
        }

        if (contains) {
            return OutcomeResult.passed("Page title contains: " + expectedText);
        } else {
            return OutcomeResult.failed(
                    "Page title '%s' does not contain '%s'",
                    currentTitle, expectedText);
        }
    }

    @Override
    public String getDescription() {
        return "Verify page title contains: " + expectedText;
    }
}
