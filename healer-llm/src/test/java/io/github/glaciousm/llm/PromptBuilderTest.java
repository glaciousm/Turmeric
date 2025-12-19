package io.github.glaciousm.llm;

import io.github.glaciousm.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    @Test
    void buildSystemPrompt_returnsNonEmptyString() {
        String result = promptBuilder.buildSystemPrompt();

        assertThat(result).isNotBlank();
        assertThat(result).contains("test automation");
        assertThat(result).contains("engineer");
    }

    @Test
    void buildHealingPrompt_withFullContext_includesAllInformation() {
        FailureContext failure = createSampleFailureContext();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("Login Feature");
        assertThat(prompt).contains("User logs in");
        assertThat(prompt).contains("Given");
        assertThat(prompt).contains("click the login button");
        assertThat(prompt).contains("login_action");
        assertThat(prompt).contains("NoSuchElementException");
        assertThat(prompt).contains("#login-btn");
        assertThat(prompt).contains("CSS");
        assertThat(prompt).contains("https://example.com/login");
        assertThat(prompt).contains("Login Page");
        assertThat(prompt).contains("English");
        assertThat(prompt).contains("JSON");
        assertThat(prompt).contains("can_heal");
        assertThat(prompt).contains("confidence");
    }

    @Test
    void buildHealingPrompt_withNullValues_handlesGracefully() {
        FailureContext failure = FailureContext.builder()
                .stepText("click something")
                .build();

        UiSnapshot snapshot = UiSnapshot.builder()
                .interactiveElements(List.of())
                .build();

        IntentContract intent = IntentContract.builder()
                .action("click")
                .build();

        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("unknown");
        assertThat(prompt).contains("No interactive elements found");
    }

    @Test
    void buildHealingPrompt_includesElementDetails() {
        FailureContext failure = createSampleFailureContext();
        UiSnapshot snapshot = createSnapshotWithMultipleElements();
        IntentContract intent = createSampleIntent();

        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        assertThat(prompt).contains("**[0]** `<button>`");
        assertThat(prompt).contains("id: submit-btn");
        assertThat(prompt).contains("text: \"Submit\"");
        assertThat(prompt).contains("**[1]** `<input>`");
        assertThat(prompt).contains("type: text");
    }

    @Test
    void buildHealingPrompt_limitsElementCount() {
        FailureContext failure = createSampleFailureContext();
        IntentContract intent = createSampleIntent();

        // Create snapshot with 60 elements (more than MAX_ELEMENTS_IN_PROMPT = 50)
        UiSnapshot.Builder snapshotBuilder = UiSnapshot.builder()
                .url("https://example.com")
                .title("Test Page");

        List<ElementSnapshot> elements = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            elements.add(ElementSnapshot.builder()
                    .index(i)
                    .tagName("button")
                    .text("Button " + i)
                    .build());
        }
        snapshotBuilder.interactiveElements(elements);
        UiSnapshot snapshot = snapshotBuilder.build();

        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        assertThat(prompt).contains("... and 10 more elements");
    }

    @Test
    void buildHealingPrompt_truncatesLongText() {
        FailureContext failure = createSampleFailureContext();
        IntentContract intent = createSampleIntent();

        String longText = "a".repeat(200);
        ElementSnapshot element = ElementSnapshot.builder()
                .index(0)
                .tagName("div")
                .text(longText)
                .build();

        UiSnapshot snapshot = UiSnapshot.builder()
                .url("https://example.com")
                .title("Test")
                .interactiveElements(List.of(element))
                .build();

        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        assertThat(prompt).contains("...");
        assertThat(prompt).doesNotContain(longText);
    }

    @Test
    void buildHealingPrompt_includesAllElementAttributes() {
        FailureContext failure = createSampleFailureContext();
        IntentContract intent = createSampleIntent();

        ElementSnapshot element = ElementSnapshot.builder()
                .index(0)
                .tagName("input")
                .id("email-input")
                .name("email")
                .type("email")
                .classes(List.of("form-control", "required"))
                .text("Enter email")
                .ariaLabel("Email address")
                .ariaRole("textbox")
                .placeholder("your@email.com")
                .title("Email field")
                .container("form#login-form")
                .nearbyLabels(List.of("Email:", "Required field"))
                .visible(true)
                .enabled(true)
                .build();

        UiSnapshot snapshot = UiSnapshot.builder()
                .url("https://example.com")
                .title("Test")
                .interactiveElements(List.of(element))
                .build();

        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        assertThat(prompt).contains("id: email-input");
        assertThat(prompt).contains("name: email");
        assertThat(prompt).contains("type: email");
        assertThat(prompt).contains("classes: form-control, required");
        assertThat(prompt).contains("aria-label: \"Email address\"");
        assertThat(prompt).contains("role: textbox");
        assertThat(prompt).contains("placeholder: \"your@email.com\"");
        assertThat(prompt).contains("title: \"Email field\"");
        assertThat(prompt).contains("container: form#login-form");
        assertThat(prompt).contains("nearby labels: Email:, Required field");
        assertThat(prompt).contains("visible: true, enabled: true");
    }

    @Test
    void buildOutcomeValidationPrompt_includesBeforeAndAfter() {
        UiSnapshot before = createSampleSnapshot();
        UiSnapshot after = UiSnapshot.builder()
                .url("https://example.com/dashboard")
                .title("Dashboard")
                .interactiveElements(List.of(
                        ElementSnapshot.builder()
                                .index(0)
                                .tagName("button")
                                .text("Logout")
                                .build()
                ))
                .build();

        String prompt = promptBuilder.buildOutcomeValidationPrompt(
                "User should be logged in and see dashboard",
                before,
                after
        );

        assertThat(prompt).contains("Expected Outcome");
        assertThat(prompt).contains("User should be logged in and see dashboard");
        assertThat(prompt).contains("Before Action");
        assertThat(prompt).contains("https://example.com/login");
        assertThat(prompt).contains("Login Page");
        assertThat(prompt).contains("After Action");
        assertThat(prompt).contains("https://example.com/dashboard");
        assertThat(prompt).contains("Dashboard");
        assertThat(prompt).contains("outcome_achieved");
        assertThat(prompt).contains("reasoning");
    }

    @Test
    void buildOutcomeValidationPrompt_withNullSnapshots_handlesGracefully() {
        UiSnapshot before = UiSnapshot.builder().build();
        UiSnapshot after = UiSnapshot.builder().build();

        String prompt = promptBuilder.buildOutcomeValidationPrompt(
                "Something should happen",
                before,
                after
        );

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("unknown");
    }

    @Test
    void buildEvaluationPrompt_aliasForBuildHealingPrompt() {
        FailureContext failure = createSampleFailureContext();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        String healingPrompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);
        String evaluationPrompt = promptBuilder.buildEvaluationPrompt(failure, snapshot, intent);

        assertThat(evaluationPrompt).isEqualTo(healingPrompt);
    }

    @Test
    void buildHealingPrompt_withEmptyElementLists_showsNoElements() {
        FailureContext failure = createSampleFailureContext();
        UiSnapshot snapshot = UiSnapshot.builder()
                .url("https://example.com")
                .title("Empty Page")
                .interactiveElements(List.of())
                .build();
        IntentContract intent = createSampleIntent();

        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        assertThat(prompt).contains("No interactive elements found on the page");
    }

    @Test
    void buildHealingPrompt_withVisibilityStates_includesStates() {
        FailureContext failure = createSampleFailureContext();
        IntentContract intent = createSampleIntent();

        ElementSnapshot visibleEnabled = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .text("Visible Enabled")
                .visible(true)
                .enabled(true)
                .build();

        ElementSnapshot visibleDisabled = ElementSnapshot.builder()
                .index(1)
                .tagName("button")
                .text("Visible Disabled")
                .visible(true)
                .enabled(false)
                .build();

        ElementSnapshot invisibleEnabled = ElementSnapshot.builder()
                .index(2)
                .tagName("button")
                .text("Invisible Enabled")
                .visible(false)
                .enabled(true)
                .build();

        UiSnapshot snapshot = UiSnapshot.builder()
                .url("https://example.com")
                .title("Test")
                .interactiveElements(List.of(visibleEnabled, visibleDisabled, invisibleEnabled))
                .build();

        String prompt = promptBuilder.buildHealingPrompt(failure, snapshot, intent);

        assertThat(prompt).contains("visible: true, enabled: true");
        assertThat(prompt).contains("visible: true, enabled: false");
        assertThat(prompt).contains("visible: false, enabled: true");
    }

    // Helper methods

    private FailureContext createSampleFailureContext() {
        return FailureContext.builder()
                .featureName("Login Feature")
                .scenarioName("User logs in")
                .stepKeyword("Given")
                .stepText("click the login button")
                .exceptionType("NoSuchElementException")
                .exceptionMessage("Unable to locate element")
                .originalLocator(new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, "#login-btn"))
                .actionType(ActionType.CLICK)
                .build();
    }

    private UiSnapshot createSampleSnapshot() {
        ElementSnapshot element = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .id("submit-btn")
                .text("Submit")
                .visible(true)
                .enabled(true)
                .build();

        return UiSnapshot.builder()
                .url("https://example.com/login")
                .title("Login Page")
                .detectedLanguage("English")
                .interactiveElements(List.of(element))
                .build();
    }

    private UiSnapshot createSnapshotWithMultipleElements() {
        ElementSnapshot button = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .id("submit-btn")
                .text("Submit")
                .visible(true)
                .enabled(true)
                .build();

        ElementSnapshot input = ElementSnapshot.builder()
                .index(1)
                .tagName("input")
                .type("text")
                .id("username")
                .placeholder("Enter username")
                .visible(true)
                .enabled(true)
                .build();

        return UiSnapshot.builder()
                .url("https://example.com/login")
                .title("Login Page")
                .interactiveElements(List.of(button, input))
                .build();
    }

    private IntentContract createSampleIntent() {
        return IntentContract.builder()
                .action("login_action")
                .description("Click the login button to submit credentials")
                .policy(HealPolicy.AUTO_SAFE)
                .destructive(false)
                .build();
    }
}
