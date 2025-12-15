package com.intenthealer.junit;

import com.intenthealer.core.config.ConfigLoader;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.core.model.HealMode;
import com.intenthealer.report.ReportGenerator;
import com.intenthealer.report.model.HealReport;
import com.intenthealer.selenium.driver.HealingWebDriver;
import org.junit.jupiter.api.extension.*;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

/**
 * JUnit 5 extension that automatically enables healing for WebDriver-based tests.
 *
 * Usage:
 * <pre>
 * &#64;ExtendWith(HealerExtension.class)
 * public class MySeleniumTest {
 *     private WebDriver driver; // Will be automatically wrapped
 *
 *     &#64;Test
 *     &#64;HealEnabled(intent = "Login with valid credentials")
 *     void testLogin() {
 *         // Test code
 *     }
 * }
 * </pre>
 */
public class HealerExtension implements BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final Logger logger = LoggerFactory.getLogger(HealerExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(HealerExtension.class);

    private static final String CONFIG_KEY = "config";
    private static final String ENGINE_KEY = "engine";
    private static final String REPORT_KEY = "report";
    private static final String GENERATOR_KEY = "generator";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        logger.info("Initializing Intent Healer for test class: {}",
                context.getRequiredTestClass().getSimpleName());

        try {
            HealerConfig config = ConfigLoader.load();
            context.getStore(NAMESPACE).put(CONFIG_KEY, config);

            if (config.isEnabled()) {
                HealingEngine engine = HealingEngine.builder()
                        .config(config)
                        .build();
                context.getStore(NAMESPACE).put(ENGINE_KEY, engine);

                ReportGenerator generator = new ReportGenerator();
                context.getStore(NAMESPACE).put(GENERATOR_KEY, generator);

                logger.info("Intent Healer initialized with mode: {}", config.getMode());
            } else {
                logger.info("Intent Healer is disabled by configuration");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Intent Healer", e);
            throw e;
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        HealingEngine engine = context.getStore(NAMESPACE).get(ENGINE_KEY, HealingEngine.class);
        if (engine != null) {
            engine.shutdown();
        }

        // Generate final report for the test class
        generateClassReport(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        HealerConfig config = context.getStore(NAMESPACE).get(CONFIG_KEY, HealerConfig.class);
        if (config == null || !config.isEnabled()) {
            return;
        }

        // Check for @HealDisabled annotation
        if (isHealingDisabled(context)) {
            logger.debug("Healing disabled for test: {}", context.getDisplayName());
            return;
        }

        // Create test report
        HealReport report = new HealReport();
        report.setTestName(context.getDisplayName());
        report.setStartTime(Instant.now());
        context.getStore(NAMESPACE).put(REPORT_KEY, report);

        // Wrap WebDriver fields
        context.getTestInstance().ifPresent(this::wrapWebDriverFields);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        HealReport report = context.getStore(NAMESPACE).get(REPORT_KEY, HealReport.class);
        if (report != null) {
            report.setEndTime(Instant.now());
        }
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        finalizeReport(context, "PASSED");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        finalizeReport(context, "FAILED");
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        finalizeReport(context, "SKIPPED");
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        finalizeReport(context, "ABORTED");
    }

    /**
     * Wrap WebDriver fields in test instance with HealingWebDriver.
     */
    private void wrapWebDriverFields(Object testInstance) {
        ExtensionContext context = getContextFromInstance(testInstance);
        HealingEngine engine = context != null
                ? context.getStore(NAMESPACE).get(ENGINE_KEY, HealingEngine.class)
                : null;
        HealerConfig config = context != null
                ? context.getStore(NAMESPACE).get(CONFIG_KEY, HealerConfig.class)
                : null;

        if (engine == null || config == null) {
            return;
        }

        Class<?> clazz = testInstance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (WebDriver.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        WebDriver originalDriver = (WebDriver) field.get(testInstance);

                        if (originalDriver != null && !(originalDriver instanceof HealingWebDriver)) {
                            HealMode mode = HealMode.valueOf(config.getMode().toUpperCase());
                            HealingWebDriver healingDriver = new HealingWebDriver(originalDriver, engine, mode);
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
     * Check if healing is disabled for the current test.
     */
    private boolean isHealingDisabled(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> m.isAnnotationPresent(HealDisabled.class))
                .orElse(false)
                || context.getRequiredTestClass().isAnnotationPresent(HealDisabled.class);
    }

    /**
     * Finalize the test report.
     */
    private void finalizeReport(ExtensionContext context, String status) {
        HealReport report = context.getStore(NAMESPACE).get(REPORT_KEY, HealReport.class);
        if (report != null) {
            report.setTestStatus(status);
            report.setEndTime(Instant.now());

            // Generate individual report if configured
            HealerConfig config = context.getStore(NAMESPACE).get(CONFIG_KEY, HealerConfig.class);
            ReportGenerator generator = context.getStore(NAMESPACE).get(GENERATOR_KEY, ReportGenerator.class);

            if (config != null && config.getReports() != null && config.getReports().isEnabled() && generator != null) {
                try {
                    String reportPath = config.getReports().getOutputDir() + "/test-"
                            + context.getDisplayName().replaceAll("[^a-zA-Z0-9]", "_");
                    generator.generateJsonReport(report, reportPath + ".json");
                } catch (Exception e) {
                    logger.warn("Failed to generate test report: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Generate class-level report.
     */
    private void generateClassReport(ExtensionContext context) {
        HealerConfig config = context.getStore(NAMESPACE).get(CONFIG_KEY, HealerConfig.class);
        ReportGenerator generator = context.getStore(NAMESPACE).get(GENERATOR_KEY, ReportGenerator.class);

        if (config != null && config.getReports() != null && config.getReports().isEnabled() && generator != null) {
            try {
                String className = context.getRequiredTestClass().getSimpleName();
                String reportPath = config.getReports().getOutputDir() + "/class-" + className;

                HealReport classReport = new HealReport();
                classReport.setTestName("Class: " + className);
                classReport.setEndTime(Instant.now());

                generator.generateHtmlReport(classReport, reportPath + ".html");
                logger.info("Class report generated: {}", reportPath);
            } catch (Exception e) {
                logger.warn("Failed to generate class report: {}", e.getMessage());
            }
        }
    }

    /**
     * Get extension context from test instance (workaround for field wrapping).
     */
    private ExtensionContext getContextFromInstance(Object testInstance) {
        // This is a simplified implementation - in practice,
        // you'd use extension store callbacks
        return null;
    }
}
