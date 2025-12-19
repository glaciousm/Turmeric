package io.github.glaciousm.showcase.hooks;

import io.github.glaciousm.core.engine.HealingSummary;
import io.github.glaciousm.showcase.config.ShowcaseConfig;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber hooks for Intent Healer Showcase.
 */
public class ShowcaseHooks {

    private static final Logger logger = LoggerFactory.getLogger(ShowcaseHooks.class);
    private static int testCount = 0;
    private static int passedCount = 0;
    private static int failedCount = 0;

    @Before
    public void setUp(Scenario scenario) {
        testCount++;
        logger.info("");
        logger.info("╔══════════════════════════════════════════════════════════════╗");
        logger.info("║ TEST {}: {}",
                String.format("%2d", testCount),
                truncate(scenario.getName(), 45));
        logger.info("╚══════════════════════════════════════════════════════════════╝");

        // Ensure driver is initialized
        ShowcaseConfig.getInstance().getDriver();
    }

    @After
    public void tearDown(Scenario scenario) {
        String status = scenario.isFailed() ? "FAILED" : "PASSED";

        if (scenario.isFailed()) {
            failedCount++;
            logger.error("╔══════════════════════════════════════════════════════════════╗");
            logger.error("║ RESULT: {} - {}",
                    status,
                    truncate(scenario.getName(), 40));
            logger.error("╚══════════════════════════════════════════════════════════════╝");
        } else {
            passedCount++;
            logger.info("╔══════════════════════════════════════════════════════════════╗");
            logger.info("║ RESULT: {} - {}",
                    status,
                    truncate(scenario.getName(), 40));
            logger.info("║ Self-healing was applied successfully!");
            logger.info("╚══════════════════════════════════════════════════════════════╝");
        }

        // Quit driver after each scenario to ensure clean state
        ShowcaseConfig.getInstance().quitDriver();
        ShowcaseConfig.reset();

        logger.info("");
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Print final summary after all tests complete.
     */
    @AfterAll
    public static void printSummary() {
        logger.info("");
        logger.info("════════════════════════════════════════════════════════════════");
        logger.info("                    INTENT HEALER SHOWCASE RESULTS              ");
        logger.info("════════════════════════════════════════════════════════════════");
        logger.info("  Total Tests:  {}", testCount);
        logger.info("  Passed:       {} (healed successfully)", passedCount);
        logger.info("  Failed:       {}", failedCount);
        logger.info("  Success Rate: {}%", testCount > 0 ? (passedCount * 100 / testCount) : 0);
        logger.info("════════════════════════════════════════════════════════════════");

        if (failedCount == 0 && testCount > 0) {
            logger.info("  ALL TESTS PASSED! Self-healing worked for all {} scenarios.", testCount);
        }
        logger.info("");

        // Print detailed healing summary with actionable fixes
        HealingSummary.getInstance().printSummary();
    }
}
