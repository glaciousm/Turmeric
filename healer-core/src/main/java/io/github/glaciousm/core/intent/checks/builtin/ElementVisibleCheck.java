package io.github.glaciousm.core.intent.checks.builtin;

import io.github.glaciousm.core.model.ElementSnapshot;
import io.github.glaciousm.core.model.ExecutionContext;
import io.github.glaciousm.core.model.IntentContract.OutcomeCheck;
import io.github.glaciousm.core.model.OutcomeResult;
import io.github.glaciousm.core.model.UiSnapshot;

import java.util.Optional;

/**
 * Outcome check that verifies an element is visible on the page.
 */
public class ElementVisibleCheck implements OutcomeCheck {

    private final String elementId;
    private final String elementText;
    private final String description;

    private ElementVisibleCheck(String elementId, String elementText, String description) {
        this.elementId = elementId;
        this.elementText = elementText;
        this.description = description;
    }

    /**
     * Create a check that verifies an element with the given ID is visible.
     */
    public static ElementVisibleCheck byId(String id) {
        return new ElementVisibleCheck(id, null, "Element with id='" + id + "' is visible");
    }

    /**
     * Create a check that verifies an element with the given text is visible.
     */
    public static ElementVisibleCheck byText(String text) {
        return new ElementVisibleCheck(null, text, "Element with text='" + text + "' is visible");
    }

    /**
     * Create a check that verifies an element with the given ID and text is visible.
     */
    public static ElementVisibleCheck byIdAndText(String id, String text) {
        return new ElementVisibleCheck(id, text,
                "Element with id='" + id + "' and text='" + text + "' is visible");
    }

    @Override
    public OutcomeResult verify(ExecutionContext ctx) {
        UiSnapshot snapshot = ctx.getAfterSnapshot();

        if (snapshot == null) {
            return OutcomeResult.failed("No snapshot available");
        }

        Optional<ElementSnapshot> found = findElement(snapshot);

        if (found.isEmpty()) {
            return OutcomeResult.failed("Element not found: " + description);
        }

        ElementSnapshot element = found.get();

        if (!element.isVisible()) {
            return OutcomeResult.failed("Element exists but is not visible");
        }

        return OutcomeResult.passed("Element is visible: " + description);
    }

    private Optional<ElementSnapshot> findElement(UiSnapshot snapshot) {
        return snapshot.getInteractiveElements().stream()
                .filter(this::matchesElement)
                .findFirst();
    }

    private boolean matchesElement(ElementSnapshot element) {
        if (elementId != null && !elementId.equals(element.getId())) {
            return false;
        }
        if (elementText != null) {
            String text = element.getNormalizedText();
            if (text == null || !text.contains(elementText)) {
                return false;
            }
        }
        return elementId != null || elementText != null;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
