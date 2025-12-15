package com.intenthealer.testng;

import com.intenthealer.core.config.ConfigLoader;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.core.model.HealMode;
import com.intenthealer.report.ReportGenerator;
import com.intenthealer.report.model.HealReport;
import com.intenthealer.selenium.driver.HealingWebDriver;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;

import java.lang.reflect.Field;
import java.time.Instant;
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
    private boolean enabled = true;

    @Override
    public void onStart(ISuite suite) {
        logger.info("Initializing Intent Healer for test suite: {}", suite.getName());

        try {
            config = ConfigLoader.load();
            enabled = config.isEnabled();

            if (enabled) {
                healingEngine = createHealingEngine(config);
                reportGenerator = new ReportGenerator();
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
                String reportPath = config.getReports().getOutputDir() + "/suite-" + suite.getName() + "-report";

                reportGenerator.generateHtmlReport(suiteReport, reportPath + ".html");
                reportGenerator.generateJsonReport(suiteReport, reportPath + ".json");

                logger.info("Suite report generated: {}", reportPath);
            }
        } catch (Exception e) {
            logger.error("Failed to generate suite report", e);
        }

        // Cleanup
        if (healingEngine != null) {
            healingEngine.shutdown();
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
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (!enabled) return;
        finalizeTestReport(result, "FAILED");
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
        HealMode mode = HealMode.valueOf(config.getMode().toUpperCase());
        return new HealingWebDriver(driver, healingEngine, mode);
    }

    /**
     * Create the healing engine from configuration.
     */
    private HealingEngine createHealingEngine(HealerConfig config) {
        return HealingEngine.builder()
                .config(config)
                .build();
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
            if (config.getReports().isEnabled()) {
                try {
                    String reportPath = config.getReports().getOutputDir() + "/test-" + testId;
                    reportGenerator.generateJsonReport(report, reportPath + ".json");
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
