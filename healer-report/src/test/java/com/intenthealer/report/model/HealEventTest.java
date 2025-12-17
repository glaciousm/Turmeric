package com.intenthealer.report.model;

import com.intenthealer.report.model.HealEvent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the HealEvent class and its inner classes.
 */
class HealEventTest {

    private HealEvent event;

    @BeforeEach
    void setUp() {
        event = new HealEvent();
    }

    @Test
    void testDefaultEventIdGenerated() {
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventId()).isNotEmpty();
    }

    @Test
    void testDefaultTimestampSet() {
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void testSetAndGetEventId() {
        event.setEventId("custom-event-id");
        assertThat(event.getEventId()).isEqualTo("custom-event-id");
    }

    @Test
    void testSetAndGetTimestamp() {
        Instant customTime = Instant.parse("2024-01-15T10:30:00Z");
        event.setTimestamp(customTime);
        assertThat(event.getTimestamp()).isEqualTo(customTime);
    }

    @Test
    void testSetAndGetFeature() {
        event.setFeature("Login Feature");
        assertThat(event.getFeature()).isEqualTo("Login Feature");
    }

    @Test
    void testSetAndGetScenario() {
        event.setScenario("Successful Login");
        assertThat(event.getScenario()).isEqualTo("Successful Login");
    }

    @Test
    void testSetAndGetStep() {
        event.setStep("I click the login button");
        assertThat(event.getStep()).isEqualTo("I click the login button");
    }

    @Test
    void testSetAndGetAutoUpdated() {
        event.setAutoUpdated(true);
        assertThat(event.isAutoUpdated()).isTrue();

        event.setAutoUpdated(false);
        assertThat(event.isAutoUpdated()).isFalse();
    }

    @Test
    void testSetAndGetBackupPath() {
        event.setBackupPath("/backup/file.bak");
        assertThat(event.getBackupPath()).isEqualTo("/backup/file.bak");
    }

    // Convenience method tests

    @Test
    void testGetOutcomeFromResult() {
        ResultInfo result = new ResultInfo();
        result.setStatus("SUCCESS");
        event.setResult(result);

        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void testGetOutcomeWithNullResult() {
        assertThat(event.getOutcome()).isNull();
    }

    @Test
    void testGetLlmCostUsdFromCost() {
        CostInfo cost = new CostInfo();
        cost.setCostUsd(0.0025);
        event.setCost(cost);

        assertThat(event.getLlmCostUsd()).isEqualTo(0.0025);
    }

    @Test
    void testGetLlmCostUsdWithNullCost() {
        assertThat(event.getLlmCostUsd()).isNull();
    }

    @Test
    void testGetOriginalLocatorFromFailure() {
        FailureInfo failure = new FailureInfo();
        failure.setOriginalLocator("By.id: login-btn");
        event.setFailure(failure);

        assertThat(event.getOriginalLocator()).isEqualTo("By.id: login-btn");
    }

    @Test
    void testGetOriginalLocatorWithNullFailure() {
        assertThat(event.getOriginalLocator()).isNull();
    }

    @Test
    void testGetStepTextReturnsStep() {
        event.setStep("I enter my username");
        assertThat(event.getStepText()).isEqualTo("I enter my username");
    }

    @Test
    void testGetHealedLocatorFromResult() {
        ResultInfo result = new ResultInfo();
        result.setHealedLocator("By.css: .login-button");
        event.setResult(result);

        assertThat(event.getHealedLocator()).isEqualTo("By.css: .login-button");
    }

    @Test
    void testGetHealedLocatorWithNullResult() {
        assertThat(event.getHealedLocator()).isNull();
    }

    @Test
    void testGetLlmLatencyMsReturnsNull() {
        // Currently not implemented
        assertThat(event.getLlmLatencyMs()).isNull();
    }

    // FailureInfo tests

    @Test
    void testFailureInfoSettersAndGetters() {
        FailureInfo failure = new FailureInfo();
        failure.setExceptionType("NoSuchElementException");
        failure.setMessage("Unable to locate element");
        failure.setOriginalLocator("By.id: missing");

        event.setFailure(failure);

        assertThat(event.getFailure().getExceptionType()).isEqualTo("NoSuchElementException");
        assertThat(event.getFailure().getMessage()).isEqualTo("Unable to locate element");
        assertThat(event.getFailure().getOriginalLocator()).isEqualTo("By.id: missing");
    }

    // DecisionInfo tests

    @Test
    void testDecisionInfoSettersAndGetters() {
        DecisionInfo decision = new DecisionInfo();
        decision.setCanHeal(true);
        decision.setConfidence(0.92);
        decision.setSelectedElementIndex(3);
        decision.setReasoning("Found matching element by class");
        decision.setRefusalReason(null);

        event.setDecision(decision);

        assertThat(event.getDecision().isCanHeal()).isTrue();
        assertThat(event.getDecision().getConfidence()).isEqualTo(0.92);
        assertThat(event.getDecision().getSelectedElementIndex()).isEqualTo(3);
        assertThat(event.getDecision().getReasoning()).isEqualTo("Found matching element by class");
        assertThat(event.getDecision().getRefusalReason()).isNull();
    }

    @Test
    void testDecisionInfoRefusal() {
        DecisionInfo decision = new DecisionInfo();
        decision.setCanHeal(false);
        decision.setConfidence(0.3);
        decision.setRefusalReason("Confidence too low");

        event.setDecision(decision);

        assertThat(event.getDecision().isCanHeal()).isFalse();
        assertThat(event.getDecision().getRefusalReason()).isEqualTo("Confidence too low");
    }

    // ResultInfo tests

    @Test
    void testResultInfoSettersAndGetters() {
        ResultInfo result = new ResultInfo();
        result.setStatus("SUCCESS");
        result.setHealedLocator("By.cssSelector: button.submit");
        result.setOutcomeCheckPassed(true);
        result.setInvariantsSatisfied(true);

        event.setResult(result);

        assertThat(event.getResult().getStatus()).isEqualTo("SUCCESS");
        assertThat(event.getResult().getHealedLocator()).isEqualTo("By.cssSelector: button.submit");
        assertThat(event.getResult().getOutcomeCheckPassed()).isTrue();
        assertThat(event.getResult().getInvariantsSatisfied()).isTrue();
    }

    @Test
    void testResultInfoFailedStatus() {
        ResultInfo result = new ResultInfo();
        result.setStatus("FAILED");
        result.setHealedLocator(null);
        result.setOutcomeCheckPassed(false);

        event.setResult(result);

        assertThat(event.getResult().getStatus()).isEqualTo("FAILED");
        assertThat(event.getResult().getHealedLocator()).isNull();
        assertThat(event.getResult().getOutcomeCheckPassed()).isFalse();
    }

    // ArtifactInfo tests

    @Test
    void testArtifactInfoSettersAndGetters() {
        ArtifactInfo artifacts = new ArtifactInfo();
        artifacts.setScreenshot("screenshots/failure.png");
        artifacts.setDomSnapshot("snapshots/dom.html");

        event.setArtifacts(artifacts);

        assertThat(event.getArtifacts().getScreenshot()).isEqualTo("screenshots/failure.png");
        assertThat(event.getArtifacts().getDomSnapshot()).isEqualTo("snapshots/dom.html");
    }

    // CostInfo tests

    @Test
    void testCostInfoSettersAndGetters() {
        CostInfo cost = new CostInfo();
        cost.setInputTokens(1500);
        cost.setOutputTokens(200);
        cost.setCostUsd(0.0035);

        event.setCost(cost);

        assertThat(event.getCost().getInputTokens()).isEqualTo(1500);
        assertThat(event.getCost().getOutputTokens()).isEqualTo(200);
        assertThat(event.getCost().getCostUsd()).isEqualTo(0.0035);
    }

    // SourceLocationInfo tests

    @Test
    void testSourceLocationInfoSettersAndGetters() {
        SourceLocationInfo location = new SourceLocationInfo();
        location.setFilePath("src/test/java/LoginSteps.java");
        location.setClassName("LoginSteps");
        location.setMethodName("clickLoginButton");
        location.setLineNumber(42);

        event.setSourceLocation(location);

        assertThat(event.getSourceLocation().getFilePath()).isEqualTo("src/test/java/LoginSteps.java");
        assertThat(event.getSourceLocation().getClassName()).isEqualTo("LoginSteps");
        assertThat(event.getSourceLocation().getMethodName()).isEqualTo("clickLoginButton");
        assertThat(event.getSourceLocation().getLineNumber()).isEqualTo(42);
    }

    @Test
    void testSourceLocationInfoToShortStringWithSlash() {
        SourceLocationInfo location = new SourceLocationInfo();
        location.setFilePath("src/test/java/LoginSteps.java");
        location.setLineNumber(42);

        assertThat(location.toShortString()).isEqualTo("LoginSteps.java:42");
    }

    @Test
    void testSourceLocationInfoToShortStringWithBackslash() {
        SourceLocationInfo location = new SourceLocationInfo();
        location.setFilePath("src\\test\\java\\LoginSteps.java");
        location.setLineNumber(100);

        assertThat(location.toShortString()).isEqualTo("LoginSteps.java:100");
    }

    @Test
    void testSourceLocationInfoToShortStringWithNullFilePath() {
        SourceLocationInfo location = new SourceLocationInfo();
        location.setFilePath(null);
        location.setLineNumber(10);

        assertThat(location.toShortString()).isEqualTo("unknown:10");
    }

    @Test
    void testSourceLocationInfoToShortStringSimpleFilename() {
        SourceLocationInfo location = new SourceLocationInfo();
        location.setFilePath("LoginSteps.java");
        location.setLineNumber(25);

        assertThat(location.toShortString()).isEqualTo("LoginSteps.java:25");
    }

    // Integration test - complete event

    @Test
    void testCompleteEventSetup() {
        event.setFeature("User Authentication");
        event.setScenario("Login with valid credentials");
        event.setStep("I click the login button");

        FailureInfo failure = new FailureInfo();
        failure.setExceptionType("NoSuchElementException");
        failure.setMessage("Element not found");
        failure.setOriginalLocator("By.id: login-btn");
        event.setFailure(failure);

        DecisionInfo decision = new DecisionInfo();
        decision.setCanHeal(true);
        decision.setConfidence(0.95);
        decision.setSelectedElementIndex(2);
        decision.setReasoning("Found button with class 'login'");
        event.setDecision(decision);

        ResultInfo result = new ResultInfo();
        result.setStatus("SUCCESS");
        result.setHealedLocator("By.cssSelector: button.login");
        result.setOutcomeCheckPassed(true);
        event.setResult(result);

        CostInfo cost = new CostInfo();
        cost.setInputTokens(1200);
        cost.setOutputTokens(150);
        cost.setCostUsd(0.0028);
        event.setCost(cost);

        // Verify convenience methods
        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
        assertThat(event.getOriginalLocator()).isEqualTo("By.id: login-btn");
        assertThat(event.getHealedLocator()).isEqualTo("By.cssSelector: button.login");
        assertThat(event.getLlmCostUsd()).isEqualTo(0.0028);
        assertThat(event.getStepText()).isEqualTo("I click the login button");
    }
}
