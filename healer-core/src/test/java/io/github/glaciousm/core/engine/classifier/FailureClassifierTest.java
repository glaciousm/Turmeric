package io.github.glaciousm.core.engine.classifier;

import io.github.glaciousm.core.model.FailureKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FailureClassifier")
class FailureClassifierTest {

    private FailureClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new FailureClassifier();
    }

    @Test
    @DisplayName("should return UNKNOWN for null exception")
    void classifyNull() {
        assertEquals(FailureKind.UNKNOWN, classifier.classify(null));
    }

    @Test
    @DisplayName("should classify AssertionError as ASSERTION_FAILURE")
    void classifyAssertionError() {
        AssertionError error = new AssertionError("Expected true but was false");
        assertEquals(FailureKind.ASSERTION_FAILURE, classifier.classify(error));
    }

    @Test
    @DisplayName("should classify NoSuchElementException by class name")
    void classifyByClassName() {
        // Simulate a Selenium NoSuchElementException using a runtime exception with matching name
        Exception e = new RuntimeException("no such element") {
            @Override
            public String getMessage() {
                return "no such element: Unable to locate element";
            }
        };
        // The message-based classification should catch this
        FailureKind kind = classifier.classify(e);
        assertEquals(FailureKind.ELEMENT_NOT_FOUND, kind);
    }

    @Test
    @DisplayName("should classify by message - element not found")
    void classifyByMessageElementNotFound() {
        RuntimeException e = new RuntimeException("Unable to locate element: {\"method\":\"css selector\",\"selector\":\"#submit\"}");
        assertEquals(FailureKind.ELEMENT_NOT_FOUND, classifier.classify(e));
    }

    @Test
    @DisplayName("should classify by message - stale element")
    void classifyByMessageStaleElement() {
        RuntimeException e = new RuntimeException("stale element reference: element is not attached to the page document");
        assertEquals(FailureKind.STALE_ELEMENT, classifier.classify(e));
    }

    @Test
    @DisplayName("should classify by message - click intercepted")
    void classifyByMessageClickIntercepted() {
        RuntimeException e = new RuntimeException("element click intercepted: Element is not clickable at point (100, 200). Other element would receive the click");
        assertEquals(FailureKind.CLICK_INTERCEPTED, classifier.classify(e));
    }

    @Test
    @DisplayName("should classify by message - not interactable")
    void classifyByMessageNotInteractable() {
        RuntimeException e = new RuntimeException("element not interactable: element is not displayed");
        assertEquals(FailureKind.NOT_INTERACTABLE, classifier.classify(e));
    }

    @Test
    @DisplayName("should classify by message - timeout")
    void classifyByMessageTimeout() {
        RuntimeException e = new RuntimeException("Timed out after 10 seconds waiting for element to be clickable");
        assertEquals(FailureKind.TIMEOUT, classifier.classify(e));
    }

    @Test
    @DisplayName("should classify wrapped exceptions")
    void classifyWrappedException() {
        RuntimeException cause = new RuntimeException("Unable to locate element: #missing");
        RuntimeException wrapper = new RuntimeException("Step failed", cause);
        assertEquals(FailureKind.ELEMENT_NOT_FOUND, classifier.classify(wrapper));
    }

    @Test
    @DisplayName("should return UNKNOWN for unrecognized exceptions")
    void classifyUnknown() {
        RuntimeException e = new RuntimeException("Something unexpected happened");
        assertEquals(FailureKind.UNKNOWN, classifier.classify(e));
    }

    @Test
    @DisplayName("should classify with step text context")
    void classifyWithStepText() {
        RuntimeException e = new RuntimeException("Unable to locate element");

        // Assertion step should return ASSERTION_FAILURE
        assertEquals(FailureKind.ASSERTION_FAILURE,
                classifier.classify(e, "Then I should see the welcome message"));

        // Non-assertion step should classify normally
        assertEquals(FailureKind.ELEMENT_NOT_FOUND,
                classifier.classify(e, "When I click the submit button"));
    }

    @Test
    @DisplayName("should detect assertion step text patterns")
    void detectAssertionStepText() {
        assertTrue(classifier.isAssertionStepText("Then I should see the welcome message"));
        assertTrue(classifier.isAssertionStepText("And the title must be Dashboard"));
        assertTrue(classifier.isAssertionStepText("Verify the user is logged in"));
        assertTrue(classifier.isAssertionStepText("Assert the count equals 5"));
        assertTrue(classifier.isAssertionStepText("Expect the button to be visible"));
        assertTrue(classifier.isAssertionStepText("Check the response status"));
        assertTrue(classifier.isAssertionStepText("Ensure the form is valid"));
        assertTrue(classifier.isAssertionStepText("Confirm the transaction completed"));
        assertTrue(classifier.isAssertionStepText("Validate the email format"));

        assertFalse(classifier.isAssertionStepText("When I click the button"));
        assertFalse(classifier.isAssertionStepText("Given I am on the login page"));
        assertFalse(classifier.isAssertionStepText("I enter my username"));
        assertFalse(classifier.isAssertionStepText(null));
        assertFalse(classifier.isAssertionStepText(""));
    }

    @Test
    @DisplayName("should check healability by kind")
    void isHealableByKind() {
        assertTrue(classifier.isHealable(FailureKind.ELEMENT_NOT_FOUND));
        assertTrue(classifier.isHealable(FailureKind.STALE_ELEMENT));
        assertTrue(classifier.isHealable(FailureKind.CLICK_INTERCEPTED));
        assertTrue(classifier.isHealable(FailureKind.NOT_INTERACTABLE));
        assertTrue(classifier.isHealable(FailureKind.TIMEOUT));

        assertFalse(classifier.isHealable(FailureKind.ASSERTION_FAILURE));
        assertFalse(classifier.isHealable(FailureKind.UNKNOWN));
        assertFalse(classifier.isHealable((FailureKind) null));
    }

    @Test
    @DisplayName("should check healability by exception")
    void isHealableByException() {
        assertTrue(classifier.isHealable(new RuntimeException("Unable to locate element")));
        assertFalse(classifier.isHealable(new AssertionError("Expected X but got Y")));
        assertFalse(classifier.isHealable((Throwable) null));
    }
}
