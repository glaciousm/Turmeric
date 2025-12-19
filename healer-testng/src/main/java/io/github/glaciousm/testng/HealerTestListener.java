package io.github.glaciousm.testng;

import io.github.glaciousm.core.config.AutoUpdateConfig;
import io.github.glaciousm.core.config.ConfigLoader;
import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.engine.HealingEngine;
import io.github.glaciousm.core.engine.patch.SourceCodeUpdater;
import io.github.glaciousm.core.engine.patch.ValidatedHealRegistry;
import io.github.glaciousm.core.model.ValidatedHeal;
import io.github.glaciousm.report.ReportGenerator;
import io.github.glaciousm.report.model.HealReport;
import io.github.glaciousm.selenium.driver.HealingWebDriver;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TestNG listener that automatically wraps WebDriver instances with healing capability
 * and manages heal reports per test method.
 */
public class HealerTestListener implements ITestListener, ISuiteListener, IInvokedMethodListener {

    private static final Logger logger = LoggerFactory.getLogger(HealerTestListener.class);

    private static final String HEALER_ENGINE_KEY = "healerEngine";
    private static final String HEAL_REPORT_KEY = "healReport";

    private final Map<String, HealReport> testReports = new ConcurrentHashMap<>();
    private HealerConfig config;
    private HealingEngine healingEngine;
    private ReportGenerator reportGenerator;
    private ValidatedHealRegistry healRegistry;
    private SourceCodeUpdater sourceCodeUpdater;
    private boolean enabled = true;

    @Override
    public void onStart(ISuite suite) {
        logger.info("Initializing Intent Healer for test suite: {}", suite.getName());

        try {
            config = new ConfigLoader().load();
            enabled = config.isEnabled();

            if (enabled) {
                healingEngine = createHealingEngine(config);
                reportGenerator = new ReportGenerator();
                healRegistry = new ValidatedHealRegistry();

                // Initialize source code updater if auto-update is enabled
                AutoUpdateConfig autoUpdateConfig = config.getAutoUpdate();
                if (autoUpdateConfig != null && autoUpdateConfig.isEnabled()) {
                    sourceCodeUpdater = new SourceCodeUpdater(autoUpdateConfig);
                    logger.info("Auto-update enabled with min confidence: {}", autoUpdateConfig.getMinConfidence());
                }

                logger.info("Intent Healer initialized with mode: {}", config.getMode());
            } else {
                logger.info("Intent Healer is disabled by configuration");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Intent Healer", e);
            enabled = false;
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        if (!enabled) return;

        logger.info("Generating final heal report for suite: {}", suite.getName());

        try {
            // Generate consolidated report
            if (reportGenerator != null && !testReports.isEmpty()) {
                HealReport suiteReport = consolidateReports(suite.getName());
                String reportPath = config.getReport().getOutputDir() + "/suite-" + suite.getName() + "-report";

                // Write reports using the ReportGenerator's current report mechanism
                reportGenerator.startReport();
                for (var event : suiteReport.getEvents()) {
                    reportGenerator.addEvent(event);
                }
                reportGenerator.finishReport();

                logger.info("Suite report generated: {}", reportPath);
            }
        } catch (Exception e) {
            logger.error("Failed to generate suite report", e);
        }
        testReports.clear();
    }

    @Override
    public void onTestStart(ITestResult result) {
        if (!enabled) return;

        String testId = getTestId(result);
        logger.debug("Starting test with healing: {}", testId);

        // Create a new report for this test
        HealReport report = new HealReport();
        report.setTestName(result.getMethod().getMethodName());
        report.setStartTime(Instant.now());
        testReports.put(testId, report);

        // Try to wrap any WebDriver field in the test class
        wrapWebDriverFields(result.getInstance());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        if (!enabled) return;
        finalizeTestReport(result, "PASSED");

        // Trigger auto-update for validated heals
        triggerAutoUpdate(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (!enabled) return;
        finalizeTestReport(result, "FAILED");

        // Discard pending heals for failed test
        if (healRegistry != null) {
            healRegistry.discardPending(getTestId(result));
        }
    }

    /**
     * Triggers auto-update for heals validated by this passing test.
     */
    private void triggerAutoUpdate(ITestResult result) {
        if (healRegistry == null || sourceCodeUpdater == null) {
            return;
        }

        AutoUpdateConfig autoUpdateConfig = config.getAutoUpdate();
        if (autoUpdateConfig == null || !autoUpdateConfig.isEnabled()) {
            return;
        }

        String testId = getTestId(result);
        String testName = result.getMethod().getMethodName();

        // Mark pending heals as validated
        healRegistry.markAsValidated(testId, testName);

        // Get heals for auto-update
        List<ValidatedHeal> healsToUpdate = healRegistry.getHealsForAutoUpdate(testId, autoUpdateConfig.getMinConfidence());
        if (healsToUpdate.isEmpty()) {
            return;
        }

        logger.info("Applying {} validated heals for test: {}", healsToUpdate.size(), testName);

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
        healRegistry.clearValidated(testId);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (!enabled) return;
        finalizeTestReport(result, "SKIPPED");
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        // Can be used for more fine-grained method-level healing
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        // Post-method cleanup if needed
    }

    /**
     * Wrap WebDriver fields in the test class instance with HealingWebDriver.
     */
    private void wrapWebDriverFields(Object testInstance) {
        if (testInstance == null) return;

        Class<?> clazz = testInstance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (WebDriver.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        WebDriver originalDriver = (WebDriver) field.get(testInstance);

                        if (originalDriver != null && !(originalDriver instanceof HealingWebDriver)) {
                            HealingWebDriver healingDriver = createHealingDriver(originalDriver);
                            field.set(testInstance, healingDriver);
                            logger.debug("Wrapped WebDriver field '{}' with HealingWebDriver", field.getName());
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to wrap WebDriver field '{}': {}", field.getName(), e.getMessage());
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Create a HealingWebDriver wrapper.
     */
    private HealingWebDriver createHealingDriver(WebDriver driver) {
        return new HealingWebDriver(driver, healingEngine, config);
    }

    /**
     * Create the healing engine from configuration.
     */
    private HealingEngine createHealingEngine(HealerConfig config) {
        return new HealingEngine(config);
    }

    /**
     * Finalize the test report.
     */
    private void finalizeTestReport(ITestResult result, String status) {
        String testId = getTestId(result);
        HealReport report = testReports.get(testId);

        if (report != null) {
            report.setEndTime(Instant.now());
            report.setTestStatus(status);

            // Generate individual test report if configured
            if (config.getReport().isEnabled()) {
                try {
                    String reportPath = config.getReport().getOutputDir() + "/test-" + testId;
                    reportGenerator.startReport();
                    for (var event : report.getEvents()) {
                        reportGenerator.addEvent(event);
                    }
                    reportGenerator.finishReport();
                    logger.debug("Test report generated: {}", reportPath);
                } catch (Exception e) {
                    logger.warn("Failed to generate test report: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Generate a unique test ID.
     */
    private String getTestId(ITestResult result) {
        return result.getTestClass().getName() + "." + result.getMethod().getMethodName();
    }

    /**
     * Consolidate all test reports into a single suite report.
     */
    private HealReport consolidateReports(String suiteName) {
        HealReport consolidated = new HealReport();
        consolidated.setTestName("Suite: " + suiteName);
        consolidated.setStartTime(Instant.now());

        for (HealReport report : testReports.values()) {
            consolidated.getEvents().addAll(report.getEvents());
        }

        consolidated.setEndTime(Instant.now());
        return consolidated;
    }
}
