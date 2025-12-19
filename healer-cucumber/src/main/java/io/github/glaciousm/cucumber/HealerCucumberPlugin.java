package io.github.glaciousm.cucumber;

import io.github.glaciousm.core.config.AutoUpdateConfig;
import io.github.glaciousm.core.config.ConfigLoader;
import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.engine.HealingEngine;
import io.github.glaciousm.core.engine.patch.SourceCodeUpdater;
import io.github.glaciousm.core.engine.patch.ValidatedHealRegistry;
import io.github.glaciousm.core.model.*;
import io.github.glaciousm.cucumber.annotations.Intent;
import io.github.glaciousm.cucumber.annotations.Outcome;
import io.github.glaciousm.llm.LlmOrchestrator;
import io.github.glaciousm.selenium.actions.ActionExecutor;
import io.github.glaciousm.selenium.snapshot.SnapshotBuilder;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cucumber plugin that enables self-healing for test steps.
 * Register this plugin in your Cucumber runner configuration.
 *
 * Example:
 * @CucumberOptions(plugin = {"io.github.glaciousm.cucumber.HealerCucumberPlugin"})
 */
public class HealerCucumberPlugin implements ConcurrentEventListener {

    private static final Logger logger = LoggerFactory.getLogger(HealerCucumberPlugin.class);

    private final HealerConfig config;
    private final HealingEngine healingEngine;
    private final LlmOrchestrator llmOrchestrator;
    private final Map<String, ScenarioContext> scenarioContexts = new ConcurrentHashMap<>();
    private final ValidatedHealRegistry healRegistry;
    private final SourceCodeUpdater sourceCodeUpdater;

    // These need to be set by the test framework
    private static Supplier<WebDriver> webDriverSupplier;
    private static final ThreadLocal<WebDriver> currentDriver = new ThreadLocal<>();

    public HealerCucumberPlugin() {
        this.config = new ConfigLoader().load();
        this.healingEngine = new HealingEngine(config);
        this.llmOrchestrator = new LlmOrchestrator();
        this.healRegistry = new ValidatedHealRegistry();

        // Initialize source code updater if auto-update is enabled
        AutoUpdateConfig autoUpdateConfig = config.getAutoUpdate();
        if (autoUpdateConfig != null && autoUpdateConfig.isEnabled()) {
            this.sourceCodeUpdater = new SourceCodeUpdater(autoUpdateConfig);
            logger.info("Auto-update enabled with min confidence: {}", autoUpdateConfig.getMinConfidence());
        } else {
            this.sourceCodeUpdater = null;
        }

        logger.info("Intent Healer initialized with mode: {}", config.getMode());
    }

    /**
     * Set the WebDriver supplier for the healer to use.
     * Call this from your test setup.
     */
    public static void setWebDriverSupplier(Supplier<WebDriver> supplier) {
        webDriverSupplier = supplier;
    }

    /**
     * Set the current WebDriver instance for this thread.
     */
    public static void setCurrentDriver(WebDriver driver) {
        currentDriver.set(driver);
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        publisher.registerHandlerFor(TestStepStarted.class, this::onTestStepStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::onTestStepFinished);
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        String scenarioId = event.getTestCase().getId().toString();
        ScenarioContext context = new ScenarioContext();
        context.setFeatureName(event.getTestCase().getUri().toString());
        context.setScenarioName(event.getTestCase().getName());
        context.setTags(event.getTestCase().getTags());
        scenarioContexts.put(scenarioId, context);

        logger.debug("Starting scenario: {}", context.getScenarioName());
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        String scenarioId = event.getTestCase().getId().toString();
        ScenarioContext context = scenarioContexts.remove(scenarioId);

        if (context != null && context.getHealCount() > 0) {
            logger.info("Scenario '{}' completed with {} heals",
                    context.getScenarioName(), context.getHealCount());
        }

        // Handle auto-update based on test result
        if (event.getResult().getStatus() == Status.PASSED) {
            triggerAutoUpdate(scenarioId, context != null ? context.getScenarioName() : "unknown");
        } else {
            // Discard pending heals for failed scenario
            healRegistry.discardPending(scenarioId);
        }
    }

    /**
     * Triggers auto-update for heals validated by this passing scenario.
     */
    private void triggerAutoUpdate(String scenarioId, String scenarioName) {
        if (sourceCodeUpdater == null) {
            return;
        }

        AutoUpdateConfig autoUpdateConfig = config.getAutoUpdate();
        if (autoUpdateConfig == null || !autoUpdateConfig.isEnabled()) {
            return;
        }

        // Mark pending heals as validated
        healRegistry.markAsValidated(scenarioId, scenarioName);

        // Get heals for auto-update
        List<ValidatedHeal> healsToUpdate = healRegistry.getHealsForAutoUpdate(scenarioId, autoUpdateConfig.getMinConfidence());
        if (healsToUpdate.isEmpty()) {
            return;
        }

        logger.info("Applying {} validated heals for scenario: {}", healsToUpdate.size(), scenarioName);

        // Apply updates
        List<SourceCodeUpdater.UpdateResult> results = sourceCodeUpdater.applyAllValidated(healsToUpdate);
        for (SourceCodeUpdater.UpdateResult updateResult : results) {
            if (updateResult.isSuccess()) {
                logger.info("Auto-updated: {}", updateResult);
            } else {
                logger.warn("Auto-update failed: {}", updateResult);
            }
        }

        // Clear processed heals
        healRegistry.clearValidated(scenarioId);
    }

    private void onTestStepStarted(TestStepStarted event) {
        if (!(event.getTestStep() instanceof PickleStepTestStep)) {
            return;
        }

        PickleStepTestStep step = (PickleStepTestStep) event.getTestStep();
        String scenarioId = event.getTestCase().getId().toString();
        ScenarioContext context = scenarioContexts.get(scenarioId);

        if (context != null) {
            context.setCurrentStepText(step.getStep().getText());
            context.setCurrentStepKeyword(step.getStep().getKeyword().trim());
        }
    }

    private void onTestStepFinished(TestStepFinished event) {
        if (!(event.getTestStep() instanceof PickleStepTestStep)) {
            return;
        }

        // Only attempt healing on failures
        if (event.getResult().getStatus() != Status.FAILED) {
            return;
        }

        if (!config.isEnabled()) {
            return;
        }

        PickleStepTestStep step = (PickleStepTestStep) event.getTestStep();
        String scenarioId = event.getTestCase().getId().toString();
        ScenarioContext context = scenarioContexts.get(scenarioId);

        if (context == null) {
            return;
        }

        // Check if we've exceeded max heals for this scenario
        if (context.getHealCount() >= config.getGuardrails().getMaxHealsPerScenario()) {
            logger.warn("Maximum heals per scenario reached for: {}", context.getScenarioName());
            return;
        }

        // Build failure context
        FailureContext failure = buildFailureContext(event, step, context);

        // Get intent contract from step definition method
        IntentContract intent = getIntentContract(step);

        // Attempt healing
        attemptHealing(failure, intent, context);
    }

    private FailureContext buildFailureContext(
            TestStepFinished event,
            PickleStepTestStep step,
            ScenarioContext context) {

        Throwable error = event.getResult().getError();

        return FailureContext.builder()
                .featureName(context.getFeatureName())
                .scenarioName(context.getScenarioName())
                .stepText(step.getStep().getText())
                .stepKeyword(step.getStep().getKeyword().trim())
                .tags(context.getTags())
                .exceptionType(error != null ? error.getClass().getSimpleName() : "Unknown")
                .exceptionMessage(error != null ? error.getMessage() : "Unknown error")
                .failureKind(classifyFailure(error))
                .build();
    }

    private FailureKind classifyFailure(Throwable error) {
        if (error == null) {
            return FailureKind.UNKNOWN;
        }

        String className = error.getClass().getSimpleName();

        return switch (className) {
            case "NoSuchElementException" -> FailureKind.ELEMENT_NOT_FOUND;
            case "StaleElementReferenceException" -> FailureKind.STALE_ELEMENT;
            case "ElementClickInterceptedException" -> FailureKind.CLICK_INTERCEPTED;
            case "ElementNotInteractableException" -> FailureKind.NOT_INTERACTABLE;
            case "TimeoutException" -> FailureKind.TIMEOUT;
            case "AssertionError", "ComparisonFailure" -> FailureKind.ASSERTION_FAILURE;
            default -> FailureKind.UNKNOWN;
        };
    }

    private IntentContract getIntentContract(PickleStepTestStep step) {
        // Try to get the step definition method and its annotations
        // This is a simplified version - real implementation would use Cucumber internals

        // For now, return default contract
        return IntentContract.defaultContract(step.getStep().getText());
    }

    private void attemptHealing(FailureContext failure, IntentContract intent, ScenarioContext context) {
        WebDriver driver = getWebDriver();
        if (driver == null) {
            logger.warn("No WebDriver available for healing");
            return;
        }

        // Configure the healing engine with Selenium components
        healingEngine.setSnapshotCapture(f ->
                new SnapshotBuilder(driver, config.getSnapshot()).capture(f));

        healingEngine.setLlmEvaluator((f, s) ->
                llmOrchestrator.evaluateCandidates(f, s, intent, config.getLlm()));

        healingEngine.setActionExecutor((action, element, data) -> {
            new ActionExecutor(driver, config.getGuardrails()).execute(action, element, data);
            return null;
        });

        // Attempt the heal
        HealResult result = healingEngine.attemptHeal(failure, intent);

        if (result.isSuccess()) {
            context.incrementHealCount();
            logger.info("Successfully healed step: {} (confidence: {:.2f})",
                    failure.getStepText(), result.getConfidence());
        } else if (result.isRefused()) {
            logger.info("Healing refused for step: {} - {}",
                    failure.getStepText(), result.getFailureReason().orElse("unknown"));
        } else {
            logger.warn("Healing failed for step: {} - {}",
                    failure.getStepText(), result.getFailureReason().orElse("unknown"));
        }
    }

    private WebDriver getWebDriver() {
        WebDriver driver = currentDriver.get();
        if (driver != null) {
            return driver;
        }
        if (webDriverSupplier != null) {
            return webDriverSupplier.get();
        }
        return null;
    }

    /**
     * Internal class to track scenario-level state.
     */
    private static class ScenarioContext {
        private String featureName;
        private String scenarioName;
        private java.util.List<String> tags;
        private String currentStepText;
        private String currentStepKeyword;
        private int healCount = 0;

        public String getFeatureName() { return featureName; }
        public void setFeatureName(String featureName) { this.featureName = featureName; }

        public String getScenarioName() { return scenarioName; }
        public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }

        public java.util.List<String> getTags() { return tags; }
        public void setTags(java.util.List<String> tags) { this.tags = tags; }

        public String getCurrentStepText() { return currentStepText; }
        public void setCurrentStepText(String currentStepText) { this.currentStepText = currentStepText; }

        public String getCurrentStepKeyword() { return currentStepKeyword; }
        public void setCurrentStepKeyword(String currentStepKeyword) { this.currentStepKeyword = currentStepKeyword; }

        public int getHealCount() { return healCount; }
        public void incrementHealCount() { this.healCount++; }
    }
}
