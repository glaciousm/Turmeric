package io.github.glaciousm.example.hooks;

import io.github.glaciousm.example.config.HealerTestConfig;
import io.github.glaciousm.selenium.driver.HealingWebDriver;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber hooks for test setup and teardown.
 * Manages the HealingWebDriver lifecycle and captures screenshots on failure.
 */
public class TestHooks {

    private static final Logger logger = LoggerFactory.getLogger(TestHooks.class);

    @BeforeAll
    public static void beforeAll() {
        logger.info("========================================");
        logger.info("Starting Herokuapp Login Test Suite");
        logger.info("Using Intent Healer with Self-Healing");
        logger.info("========================================");
    }

    @Before
    public void beforeScenario(Scenario scenario) {
        logger.info("Starting scenario: {}", scenario.getName());
        logger.info("Tags: {}", scenario.getSourceTagNames());

        // Initialize the driver (creates new HealingWebDriver)
        HealerTestConfig config = HealerTestConfig.getInstance();
        HealingWebDriver driver = config.createDriver();

        logger.info("HealingWebDriver initialized successfully");
        logger.info("Healer mode: {}", config.getConfig().getMode());
        logger.info("Auto-update enabled: {}",
                config.getConfig().getAutoUpdate() != null &&
                        config.getConfig().getAutoUpdate().isEnabled());
    }

    @After
    public void afterScenario(Scenario scenario) {
        logger.info("Finishing scenario: {} - Status: {}",
                scenario.getName(), scenario.getStatus());

        HealerTestConfig config = HealerTestConfig.getInstance();
        HealingWebDriver driver = config.getDriver();

        if (driver != null) {
            // Capture screenshot on failure
            if (scenario.isFailed()) {
                try {
                    byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                    scenario.attach(screenshot, "image/png", "Screenshot on failure");
                    logger.warn("Scenario failed - screenshot captured");
                } catch (Exception e) {
                    logger.error("Failed to capture screenshot: {}", e.getMessage());
                }
            }

            // Log healing statistics if available
            logHealingStats(driver);
        }

        // Shutdown the driver
        config.shutdown();
        logger.info("Driver shutdown complete");
    }

    @AfterAll
    public static void afterAll() {
        logger.info("========================================");
        logger.info("Herokuapp Login Test Suite Complete");
        logger.info("========================================");

        // Final cleanup
        HealerTestConfig.getInstance().shutdown();
    }

    /**
     * Log any healing statistics from the driver.
     */
    private void logHealingStats(HealingWebDriver driver) {
        // The HealingWebDriver tracks heals internally
        // Log any relevant information here
        logger.info("Scenario complete - check healer-reports/ for healing details");
    }
}
