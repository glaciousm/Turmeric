package com.intenthealer.core.integration;

import com.intenthealer.core.config.GuardrailConfig;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.core.engine.cache.HealCache;
import com.intenthealer.core.engine.circuit.CircuitBreaker;
import com.intenthealer.core.config.CircuitBreakerConfig;
import com.intenthealer.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full healing pipeline.
 * Tests the complete flow: failure -> snapshot -> LLM -> heal -> success
 */
@DisplayName("Healing Pipeline Integration")
class HealingPipelineIntegrationTest {

    private HealingEngine engine;
    private HealerConfig config;
    private List<ElementSnapshot> testElements;

    @BeforeEach
    void setUp() {
        config = new HealerConfig();
        config.setEnabled(true);
        config.applyDefaults();
        engine = new HealingEngine(config);

        // Create test elements
        testElements = createTestElements();
    }

    @Nested
    @DisplayName("Successful Healing Scenarios")
    class SuccessfulHealingTests {

        @Test
        @DisplayName("should heal when element is found with high confidence")
        void healWithHighConfidence() {
            // Setup snapshot capture
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));

            // Setup LLM evaluator that returns high confidence decision
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.95, "Found matching login button by aria-label"));

            // Setup action executor
            AtomicBoolean actionExecuted = new AtomicBoolean(false);
            engine.setActionExecutor((actionType, element, data) -> {
                actionExecuted.set(true);
                return null;
            });

            // Execute heal
            FailureContext failure = createFailureContext("I click the login button");
            IntentContract intent = IntentContract.defaultContract("I click the login button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(0.95);
            assertThat(result.getHealedLocator()).isPresent();
            assertThat(actionExecuted.get()).isTrue();
        }

        @Test
        @DisplayName("should generate CSS locator for element with stable ID")
        void generateIdLocator() {
            // Setup with element that has ID
            List<ElementSnapshot> elementsWithId = List.of(
                ElementSnapshot.builder()
                    .index(0)
                    .tagName("button")
                    .id("login-btn")
                    .text("Login")
                    .visible(true)
                    .enabled(true)
                    .build()
            );

            engine.setSnapshotCapture(failure -> createSnapshot(elementsWithId));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.9, "Found button by ID"));

            FailureContext failure = createFailureContext("Click login");
            IntentContract intent = IntentContract.defaultContract("Click login");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getHealedLocator()).hasValue("id=login-btn");
        }

        @Test
        @DisplayName("should generate CSS locator for element with name")
        void generateNameLocator() {
            List<ElementSnapshot> elementsWithName = List.of(
                ElementSnapshot.builder()
                    .index(0)
                    .tagName("input")
                    .name("username")
                    .visible(true)
                    .enabled(true)
                    .build()
            );

            engine.setSnapshotCapture(failure -> createSnapshot(elementsWithName));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.9, "Found input by name"));

            FailureContext failure = createFailureContext("Enter username");
            IntentContract intent = IntentContract.defaultContract("Enter username");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getHealedLocator()).hasValue("name=username");
        }

        @Test
        @DisplayName("should generate XPath locator for element with text only")
        void generateXpathLocator() {
            List<ElementSnapshot> elementsWithText = List.of(
                ElementSnapshot.builder()
                    .index(0)
                    .tagName("button")
                    .text("Submit Form")
                    .visible(true)
                    .enabled(true)
                    .build()
            );

            engine.setSnapshotCapture(failure -> createSnapshot(elementsWithText));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.9, "Found button by text"));

            FailureContext failure = createFailureContext("Submit");
            IntentContract intent = IntentContract.defaultContract("Submit");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getHealedLocator().orElse(""))
                .contains("xpath=//button[contains(text(),'Submit Form')]");
        }
    }

    @Nested
    @DisplayName("Healing Refusal Scenarios")
    class RefusalTests {

        @Test
        @DisplayName("should refuse when healing is disabled")
        void refuseWhenDisabled() {
            config.setEnabled(false);
            engine = new HealingEngine(config);

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.defaultContract("Click button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isRefused()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).containsIgnoringCase("disabled");
        }

        @Test
        @DisplayName("should refuse when LLM returns low confidence")
        void refuseOnLowConfidence() {
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.lowConfidence(0.3, "Multiple similar buttons found"));

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.defaultContract("Click button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isRefused()).isTrue();
            // Decision is stored in the result
            assertThat(result.getDecision()).isPresent();
            assertThat(result.getDecision().get().getConfidence()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("should refuse when LLM cannot heal")
        void refuseWhenCannotHeal() {
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.cannotHeal("No matching element found"));

            FailureContext failure = createFailureContext("Click non-existent button");
            IntentContract intent = IntentContract.defaultContract("Click non-existent button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isRefused()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).contains("No matching element");
        }

        @Test
        @DisplayName("should fail when snapshot is empty")
        void refuseOnEmptySnapshot() {
            engine.setSnapshotCapture(failure -> createSnapshot(List.of()));

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.defaultContract("Click button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).contains("No interactive elements");
        }

        @Test
        @DisplayName("should refuse for SUGGEST policy without executing")
        void suggestWithoutExecuting() {
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.9, "Found button"));

            AtomicBoolean actionExecuted = new AtomicBoolean(false);
            engine.setActionExecutor((actionType, element, data) -> {
                actionExecuted.set(true);
                return null;
            });

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.builder()
                .action("click")
                .description("Click button")
                .policy(HealPolicy.SUGGEST)
                .build();

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.getOutcome()).isEqualTo(HealOutcome.SUGGESTED);
            assertThat(actionExecuted.get()).isFalse();
        }
    }

    @Nested
    @DisplayName("Failure Scenarios")
    class FailureTests {

        @Test
        @DisplayName("should fail when snapshot capture not configured")
        void failWithoutSnapshotCapture() {
            // Don't set snapshot capture

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.defaultContract("Click button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).contains("Snapshot capture not configured");
        }

        @Test
        @DisplayName("should fail when LLM evaluator not configured")
        void failWithoutLlmEvaluator() {
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            // Don't set LLM evaluator

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.defaultContract("Click button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).contains("LLM evaluator not configured");
        }

        @Test
        @DisplayName("should fail when action execution throws")
        void failOnActionError() {
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.9, "Found element"));
            engine.setActionExecutor((actionType, element, data) -> {
                throw new RuntimeException("Element not interactable");
            });

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.defaultContract("Click button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).contains("Action execution failed");
        }

        @Test
        @DisplayName("should fail when selected element index not found")
        void failOnInvalidElementIndex() {
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            // Return index that doesn't exist
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(999, 0.9, "Found element at invalid index"));

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.defaultContract("Click button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).contains("Selected element index not found");
        }

        @Test
        @DisplayName("should fail when outcome validation fails")
        void failOnOutcomeValidation() {
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.9, "Found element"));
            engine.setActionExecutor((actionType, element, data) -> null);
            engine.setOutcomeValidator(ctx ->
                OutcomeResult.failed("Expected page navigation but stayed on same page"));

            FailureContext failure = createFailureContext("Click login");
            IntentContract intent = IntentContract.defaultContract("Click login");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.getOutcome()).isEqualTo(HealOutcome.OUTCOME_FAILED);
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).contains("Expected page navigation");
        }
    }

    @Nested
    @DisplayName("Guardrail Integration")
    class GuardrailTests {

        @Test
        @DisplayName("should refuse healing for forbidden URL patterns")
        void refuseForForbiddenUrl() {
            // Configure guardrails with forbidden URL
            GuardrailConfig guardrails = config.getGuardrails();
            guardrails.setForbiddenUrlPatterns(List.of(".*admin.*", ".*delete.*"));
            config.setGuardrails(guardrails);
            engine = new HealingEngine(config);

            // Snapshot returns admin URL
            engine.setSnapshotCapture(failure -> UiSnapshot.builder()
                .url("https://example.com/admin/users")
                .title("Admin Panel")
                .interactiveElements(testElements)
                .build());

            FailureContext failure = createFailureContext("Click delete user");
            IntentContract intent = IntentContract.defaultContract("Click delete user");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isRefused()).isTrue();
            assertThat(result.getFailureReason().orElse("")).containsIgnoringCase("forbidden");
        }

        @Test
        @DisplayName("should refuse when element text contains forbidden keyword")
        void refuseForbiddenKeywordInElement() {
            // Create elements where one has forbidden text "Delete"
            List<ElementSnapshot> elementsWithDelete = List.of(
                ElementSnapshot.builder()
                    .index(0)
                    .tagName("button")
                    .text("Delete Account")
                    .visible(true)
                    .enabled(true)
                    .build()
            );

            engine.setSnapshotCapture(failure -> createSnapshot(elementsWithDelete));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.9, "Found delete button"));

            FailureContext failure = createFailureContext("Click delete");
            IntentContract intent = IntentContract.defaultContract("Click delete");

            HealResult result = engine.attemptHeal(failure, intent);

            // Should be refused because element text contains forbidden keyword "delete"
            assertThat(result.isRefused()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
        }

        @Test
        @DisplayName("should refuse when confidence below minimum threshold")
        void refuseLowConfidence() {
            // Configure low min confidence threshold
            config.getGuardrails().setMinConfidence(0.9);
            engine = new HealingEngine(config);

            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            // Return confidence below threshold
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.7, "Found possible match"));

            FailureContext failure = createFailureContext("Click button");
            IntentContract intent = IntentContract.defaultContract("Click button");

            HealResult result = engine.attemptHeal(failure, intent);

            assertThat(result.isRefused()).isTrue();
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).containsIgnoringCase("confidence");
        }
    }

    @Nested
    @DisplayName("Multi-step Healing")
    class MultiStepTests {

        @Test
        @DisplayName("should track consecutive heals in a session")
        void trackConsecutiveHeals() {
            engine.setSnapshotCapture(failure -> createSnapshot(testElements));
            engine.setLlmEvaluator((failure, snapshot) ->
                HealDecision.canHeal(0, 0.9, "Found element"));

            AtomicInteger healCount = new AtomicInteger(0);
            engine.setActionExecutor((actionType, element, data) -> {
                healCount.incrementAndGet();
                return null;
            });

            IntentContract intent = IntentContract.defaultContract("Click button");

            // Execute multiple heals
            for (int i = 0; i < 5; i++) {
                FailureContext failure = createFailureContext("Step " + i);
                HealResult result = engine.attemptHeal(failure, intent);
                assertThat(result.isSuccess()).isTrue();
            }

            assertThat(healCount.get()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Integration")
    class CircuitBreakerIntegrationTests {

        @Test
        @DisplayName("should integrate with circuit breaker for failures")
        void circuitBreakerIntegration() {
            CircuitBreakerConfig cbConfig = new CircuitBreakerConfig();
            cbConfig.setEnabled(true);
            cbConfig.setFailureThreshold(3);
            CircuitBreaker breaker = new CircuitBreaker(cbConfig);

            AtomicInteger failureCount = new AtomicInteger(0);

            // Simulate failures
            for (int i = 0; i < 5; i++) {
                if (breaker.isHealingAllowed()) {
                    // Simulate a failure
                    breaker.recordFailure();
                    failureCount.incrementAndGet();
                }
            }

            // After 3 failures, circuit should be open
            assertThat(breaker.isHealingAllowed()).isFalse();
            // Only 3 attempts should have been made before circuit opened
            assertThat(failureCount.get()).isEqualTo(3);
        }
    }

    // Helper methods

    private List<ElementSnapshot> createTestElements() {
        List<ElementSnapshot> elements = new ArrayList<>();
        elements.add(ElementSnapshot.builder()
            .index(0)
            .tagName("button")
            .text("Login")
            .ariaLabel("Login to your account")
            .classes(List.of("btn", "btn-primary"))
            .visible(true)
            .enabled(true)
            .build());
        elements.add(ElementSnapshot.builder()
            .index(1)
            .tagName("input")
            .type("text")
            .name("username")
            .placeholder("Enter username")
            .visible(true)
            .enabled(true)
            .build());
        elements.add(ElementSnapshot.builder()
            .index(2)
            .tagName("input")
            .type("password")
            .name("password")
            .placeholder("Enter password")
            .visible(true)
            .enabled(true)
            .build());
        return elements;
    }

    private UiSnapshot createSnapshot(List<ElementSnapshot> elements) {
        return UiSnapshot.builder()
            .url("https://example.com/login")
            .title("Login Page")
            .interactiveElements(elements)
            .build();
    }

    private FailureContext createFailureContext(String stepText) {
        return FailureContext.builder()
            .stepText(stepText)
            .featureName("Login Feature")
            .scenarioName("User Login")
            .stepKeyword("When")
            .actionType(ActionType.CLICK)
            .exceptionType("NoSuchElementException")
            .exceptionMessage("Unable to locate element")
            .originalLocator(new LocatorInfo("id", "login-btn"))
            .build();
    }
}
