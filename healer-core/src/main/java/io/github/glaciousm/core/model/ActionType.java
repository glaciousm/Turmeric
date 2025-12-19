package io.github.glaciousm.core.model;

/**
 * Types of user actions that can be healed.
 */
public enum ActionType {
    /**
     * Click on an element (button, link, etc.)
     */
    CLICK,

    /**
     * Type text into an input field.
     */
    TYPE,

    /**
     * Select an option from a dropdown.
     */
    SELECT,

    /**
     * Clear the content of an input field.
     */
    CLEAR,

    /**
     * Hover over an element.
     */
    HOVER,

    /**
     * Double-click on an element.
     */
    DOUBLE_CLICK,

    /**
     * Right-click on an element.
     */
    RIGHT_CLICK,

    /**
     * Submit a form.
     */
    SUBMIT,

    /**
     * Unknown or unclassified action.
     */
    UNKNOWN
}
