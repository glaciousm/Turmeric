package io.github.glaciousm.core.model;

/**
 * Classification of test failure types.
 * Determines whether a failure is potentially healable.
 */
public enum FailureKind {
    /**
     * Element could not be found on the page.
     * Healable: the element may have changed ID, class, or position.
     */
    ELEMENT_NOT_FOUND(true),

    /**
     * Element was found but is no longer attached to the DOM.
     * Healable: typically caused by SPA re-rendering.
     */
    STALE_ELEMENT(true),

    /**
     * Click was intercepted by another element (overlay, modal, etc.)
     * Healable: may need to wait for overlay to disappear or find alternative element.
     */
    CLICK_INTERCEPTED(true),

    /**
     * Element exists but cannot be interacted with.
     * Healable: may need to scroll, wait, or find alternative element.
     */
    NOT_INTERACTABLE(true),

    /**
     * Timeout waiting for element or condition.
     * Potentially healable: element may have moved.
     */
    TIMEOUT(true),

    /**
     * Test assertion failed.
     * Never healable: assertions represent actual test verification.
     */
    ASSERTION_FAILURE(false),

    /**
     * Unknown or unclassified failure.
     * Not healable: cannot determine appropriate action.
     */
    UNKNOWN(false);

    private final boolean healable;

    FailureKind(boolean healable) {
        this.healable = healable;
    }

    /**
     * Returns whether this type of failure can potentially be healed.
     */
    public boolean isHealable() {
        return healable;
    }
}
