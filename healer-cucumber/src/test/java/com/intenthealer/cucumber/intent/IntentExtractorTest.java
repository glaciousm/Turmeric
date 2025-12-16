package com.intenthealer.cucumber.intent;

import com.intenthealer.core.model.HealPolicy;
import com.intenthealer.core.model.IntentContract;
import com.intenthealer.cucumber.annotations.Intent;
import com.intenthealer.cucumber.annotations.Invariant;
import com.intenthealer.cucumber.annotations.Invariants;
import com.intenthealer.cucumber.annotations.Outcome;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class IntentExtractorTest {

    private IntentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new IntentExtractor();
    }

    // ===== Test extracting intent from @Intent annotation =====

    @Test
    void extractIntent_fromMethodWithIntentAnnotation() throws Exception {
        Method method = TestSteps.class.getMethod("clickLoginButton");

        Optional<IntentContract> result = extractor.extractIntent(method);

        assertThat(result).isPresent();
        IntentContract intent = result.get();
        assertThat(intent.getAction()).isEqualTo("click");
        assertThat(intent.getDescription()).isEqualTo("Click the login button");
        assertThat(intent.getPolicy()).isEqualTo(HealPolicy.AUTO_SAFE);
        assertThat(intent.isDestructive()).isFalse();
    }

    @Test
    void extractIntent_withCustomPolicy() throws Exception {
        Method method = TestSteps.class.getMethod("deleteAccount");

        Optional<IntentContract> result = extractor.extractIntent(method);

        assertThat(result).isPresent();
        IntentContract intent = result.get();
        assertThat(intent.getPolicy()).isEqualTo(HealPolicy.MANUAL);
        assertThat(intent.isDestructive()).isTrue();
    }

    @Test
    void extractIntent_cachesResults() throws Exception {
        Method method = TestSteps.class.getMethod("clickLoginButton");

        // Extract twice
        extractor.extractIntent(method);
        extractor.extractIntent(method);

        // Verify cache is used
        assertThat(extractor.getCacheSize()).isGreaterThan(0);
    }

    // ===== Test extracting outcome from @Outcome annotation =====

    @Test
    void extractIntent_extractsOutcomeAnnotation() throws Exception {
        Method method = TestSteps.class.getMethod("verifyWelcomeMessage");

        Optional<IntentContract> result = extractor.extractIntent(method);

        assertThat(result).isPresent();
        IntentContract intent = result.get();
        assertThat(intent.getOutcomeDescription()).isEqualTo("User sees welcome message");
    }

    @Test
    void extractIntent_extractsOutcomeChecks() throws Exception {
        Method method = TestSteps.class.getMethod("verifyLoginSuccess");

        Optional<IntentContract> result = extractor.extractIntent(method);

        assertThat(result).isPresent();
        IntentContract intent = result.get();
        assertThat(intent.getOutcomeCheck()).isEqualTo("User is logged in");
        assertThat(intent.getOutcomeDescription()).isEqualTo("Login is successful");
    }

    @Test
    void extractIntent_prefersOutcomeValueOverDescription() throws Exception {
        Method method = TestSteps.class.getMethod("verifyWithBothValueAndDescription");

        Optional<IntentContract> result = extractor.extractIntent(method);

        assertThat(result).isPresent();
        IntentContract intent = result.get();
        // Should use value() instead of description()
        assertThat(intent.getOutcomeDescription()).isEqualTo("Value wins");
    }

    // ===== Test handling methods without annotations =====

    @Test
    void extractIntent_returnsEmpty_whenNoIntentAnnotation() throws Exception {
        Method method = TestSteps.class.getMethod("methodWithoutIntent");

        Optional<IntentContract> result = extractor.extractIntent(method);

        assertThat(result).isEmpty();
    }

    @Test
    void extractIntent_handlesMethodWithOnlyOutcome() throws Exception {
        Method method = TestSteps.class.getMethod("methodWithOnlyOutcome");

        Optional<IntentContract> result = extractor.extractIntent(method);

        // Should return empty since @Intent is required
        assertThat(result).isEmpty();
    }

    // ===== Test extracting invariants =====

    @Test
    void extractIntent_extractsSingleInvariant() throws Exception {
        Method method = TestSteps.class.getMethod("methodWithSingleInvariant");

        Optional<IntentContract> result = extractor.extractIntent(method);

        assertThat(result).isPresent();
        IntentContract intent = result.get();
        assertThat(intent.getInvariants()).hasSize(1);
        assertThat(intent.getInvariants().get(0)).isEqualTo(TestInvariant.class);
    }

    @Test
    void extractIntent_extractsMultipleInvariants() throws Exception {
        Method method = TestSteps.class.getMethod("methodWithMultipleInvariants");

        Optional<IntentContract> result = extractor.extractIntent(method);

        assertThat(result).isPresent();
        IntentContract intent = result.get();
        assertThat(intent.getInvariants()).hasSize(2);
        assertThat(intent.getInvariants()).containsExactly(
                TestInvariant.class,
                AnotherInvariant.class
        );
    }

    // ===== Test step definition class registration =====

    @Test
    void registerStepDefinitionClass_allowsLookup() {
        extractor.registerStepDefinitionClass(TestSteps.class);

        Optional<IntentContract> result = extractor.extractIntentByStepText("I click the login button");

        assertThat(result).isPresent();
        assertThat(result.get().getAction()).isEqualTo("click");
    }

    @Test
    void registerStepDefinitionClasses_allowsMultipleClasses() {
        extractor.registerStepDefinitionClasses(TestSteps.class, MoreTestSteps.class);

        Optional<IntentContract> result1 = extractor.extractIntentByStepText("I click the login button");
        Optional<IntentContract> result2 = extractor.extractIntentByStepText("I fill the form");

        assertThat(result1).isPresent();
        assertThat(result2).isPresent();
    }

    @Test
    void registerStepDefinitionClass_doesNotDuplicate() {
        extractor.registerStepDefinitionClass(TestSteps.class);
        extractor.registerStepDefinitionClass(TestSteps.class);

        // Should only be registered once (implementation detail, but good to verify)
        Optional<IntentContract> result = extractor.extractIntentByStepText("I click the login button");
        assertThat(result).isPresent();
    }

    // ===== Test extracting intent by step text =====

    @Test
    void extractIntentByStepText_matchesExactText() {
        extractor.registerStepDefinitionClass(TestSteps.class);

        Optional<IntentContract> result = extractor.extractIntentByStepText("I click the login button");

        assertThat(result).isPresent();
        assertThat(result.get().getAction()).isEqualTo("click");
    }

    @Test
    void extractIntentByStepText_matchesParameterizedStep() {
        extractor.registerStepDefinitionClass(TestSteps.class);

        Optional<IntentContract> result = extractor.extractIntentByStepText("I enter \"john@example.com\" in the email field");

        assertThat(result).isPresent();
        assertThat(result.get().getAction()).isEqualTo("type");
    }

    @Test
    void extractIntentByStepText_returnsEmpty_whenNoMatch() {
        extractor.registerStepDefinitionClass(TestSteps.class);

        Optional<IntentContract> result = extractor.extractIntentByStepText("This step does not exist");

        assertThat(result).isEmpty();
    }

    @Test
    void extractIntentByStepText_returnsEmpty_whenNoClassesRegistered() {
        Optional<IntentContract> result = extractor.extractIntentByStepText("I click button");

        assertThat(result).isEmpty();
    }

    // ===== Test Cucumber pattern matching =====

    @Test
    void extractIntentByStepText_matchesWithStringParameter() {
        extractor.registerStepDefinitionClass(TestSteps.class);

        Optional<IntentContract> result = extractor.extractIntentByStepText("I enter \"test@test.com\" in the email field");

        assertThat(result).isPresent();
    }

    @Test
    void extractIntentByStepText_matchesWithIntParameter() {
        extractor.registerStepDefinitionClass(TestSteps.class);

        Optional<IntentContract> result = extractor.extractIntentByStepText("I wait for 5 seconds");

        assertThat(result).isPresent();
    }

    // ===== Test creating default intent =====

    @Test
    void createDefaultIntent_forNonAssertionStep() {
        IntentContract intent = extractor.createDefaultIntent("I click the button", "When");

        assertThat(intent.getAction()).isEqualTo("action");
        assertThat(intent.getDescription()).isEqualTo("I click the button");
        assertThat(intent.getPolicy()).isEqualTo(HealPolicy.AUTO_SAFE);
        assertThat(intent.isDestructive()).isFalse();
    }

    @Test
    void createDefaultIntent_forThenStep() {
        IntentContract intent = extractor.createDefaultIntent("I should see welcome message", "Then");

        assertThat(intent.getAction()).isEqualTo("assertion");
        assertThat(intent.getPolicy()).isEqualTo(HealPolicy.OFF);
    }

    @Test
    void createDefaultIntent_detectsAssertionFromKeywords() {
        IntentContract intent1 = extractor.createDefaultIntent("I should see message", "When");
        IntentContract intent2 = extractor.createDefaultIntent("I must verify the result", "Given");
        IntentContract intent3 = extractor.createDefaultIntent("I expect to see error", "When");

        assertThat(intent1.getAction()).isEqualTo("assertion");
        assertThat(intent2.getAction()).isEqualTo("assertion");
        assertThat(intent3.getAction()).isEqualTo("assertion");
    }

    @Test
    void createDefaultIntent_handlesNullStepKeyword() {
        IntentContract intent = extractor.createDefaultIntent("I click button", null);

        assertThat(intent.getAction()).isEqualTo("action");
        assertThat(intent.getPolicy()).isEqualTo(HealPolicy.AUTO_SAFE);
    }

    @Test
    void createDefaultIntent_handlesNullStepText() {
        IntentContract intent = extractor.createDefaultIntent(null, "When");

        assertThat(intent.getAction()).isEqualTo("action");
    }

    // ===== Test cache management =====

    @Test
    void clearCache_removesAllCachedIntents() throws Exception {
        Method method = TestSteps.class.getMethod("clickLoginButton");

        extractor.extractIntent(method);
        assertThat(extractor.getCacheSize()).isGreaterThan(0);

        extractor.clearCache();
        assertThat(extractor.getCacheSize()).isEqualTo(0);
    }

    @Test
    void getCacheSize_returnsZero_initially() {
        assertThat(extractor.getCacheSize()).isEqualTo(0);
    }

    @Test
    void getCacheSize_increasesWithExtractions() throws Exception {
        Method method1 = TestSteps.class.getMethod("clickLoginButton");
        Method method2 = TestSteps.class.getMethod("verifyWelcomeMessage");

        extractor.extractIntent(method1);
        int size1 = extractor.getCacheSize();

        extractor.extractIntent(method2);
        int size2 = extractor.getCacheSize();

        assertThat(size2).isGreaterThan(size1);
    }

    // ===== Test assertion detection patterns =====

    @Test
    void isAssertionStep_detectsVariousKeywords() {
        assertThat(extractor.createDefaultIntent("I should see", "When").getAction()).isEqualTo("assertion");
        assertThat(extractor.createDefaultIntent("I must check", "When").getAction()).isEqualTo("assertion");
        assertThat(extractor.createDefaultIntent("I verify that", "When").getAction()).isEqualTo("assertion");
        assertThat(extractor.createDefaultIntent("I assert equals", "When").getAction()).isEqualTo("assertion");
        assertThat(extractor.createDefaultIntent("I expect value", "When").getAction()).isEqualTo("assertion");
        assertThat(extractor.createDefaultIntent("I check if", "When").getAction()).isEqualTo("assertion");
        assertThat(extractor.createDefaultIntent("I ensure that", "When").getAction()).isEqualTo("assertion");
        assertThat(extractor.createDefaultIntent("I confirm that", "When").getAction()).isEqualTo("assertion");
        assertThat(extractor.createDefaultIntent("I validate that", "When").getAction()).isEqualTo("assertion");
    }

    // ===== Test step definition classes =====

    public static class TestSteps {

        @Given("I click the login button")
        @Intent(action = "click", description = "Click the login button")
        public void clickLoginButton() {
        }

        @When("I delete my account")
        @Intent(action = "delete", description = "Delete user account", policy = HealPolicy.MANUAL, destructive = true)
        public void deleteAccount() {
        }

        @Then("I should see the welcome message")
        @Intent(action = "verify", description = "Verify welcome message")
        @Outcome(value = "User sees welcome message")
        public void verifyWelcomeMessage() {
        }

        @Then("the login is successful")
        @Intent(action = "verify", description = "Verify login")
        @Outcome(checks = "User is logged in", description = "Login is successful")
        public void verifyLoginSuccess() {
        }

        @Then("verify with both")
        @Intent(action = "verify", description = "Test")
        @Outcome(value = "Value wins", description = "Description loses")
        public void verifyWithBothValueAndDescription() {
        }

        @When("I do something")
        public void methodWithoutIntent() {
        }

        @Then("I see result")
        @Outcome(value = "Result is shown")
        public void methodWithOnlyOutcome() {
        }

        @When("I perform action with invariant")
        @Intent(action = "action", description = "Action with invariant")
        @Invariant(TestInvariant.class)
        public void methodWithSingleInvariant() {
        }

        @When("I perform action with multiple invariants")
        @Intent(action = "action", description = "Action with multiple invariants")
        @Invariants({
                @Invariant(TestInvariant.class),
                @Invariant(AnotherInvariant.class)
        })
        public void methodWithMultipleInvariants() {
        }

        @When("I enter {string} in the email field")
        @Intent(action = "type", description = "Enter email")
        public void enterEmail(String email) {
        }

        @When("I wait for {int} seconds")
        @Intent(action = "wait", description = "Wait for duration")
        public void waitForSeconds(int seconds) {
        }
    }

    public static class MoreTestSteps {

        @When("I fill the form")
        @Intent(action = "fill", description = "Fill form")
        public void fillForm() {
        }
    }

    // Test invariant classes
    public static class TestInvariant implements IntentContract.InvariantCheck {
        @Override
        public boolean check(Object context) {
            return true;
        }

        @Override
        public String getDescription() {
            return "Test invariant";
        }
    }

    public static class AnotherInvariant implements IntentContract.InvariantCheck {
        @Override
        public boolean check(Object context) {
            return true;
        }

        @Override
        public String getDescription() {
            return "Another invariant";
        }
    }
}
