package io.github.glaciousm.showcase.config;

import io.github.glaciousm.core.config.ConfigLoader;
import io.github.glaciousm.core.config.HealerConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for Intent Healer Showcase.
 *
 * <p>This class demonstrates the ZERO-CODE Java Agent integration approach.
 * No HealingWebDriver wrapping is needed - the Java Agent automatically
 * intercepts all WebDriver instances and adds self-healing capability.</p>
 *
 * <h2>How it works:</h2>
 * <ol>
 *   <li>The Java Agent is loaded via JVM argument (-javaagent)</li>
 *   <li>When ChromeDriver is created, the agent automatically registers it</li>
 *   <li>When findElement() fails, the agent intercepts and heals</li>
 *   <li>No code changes required in your tests!</li>
 * </ol>
 *
 * <h2>Configuration:</h2>
 * <p>The agent reads healer-config.yml from src/test/resources/</p>
 */
public class ShowcaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(ShowcaseConfig.class);
    private static ShowcaseConfig instance;

    private final HealerConfig config;
    private WebDriver driver;

    private ShowcaseConfig() {
        // Load configuration from YAML file (for display purposes)
        this.config = new ConfigLoader().load();

        logger.info("Intent Healer Showcase initialized (Java Agent mode)");
        logger.info("Mode: {}", config.getMode());
        logger.info("LLM Provider: {}", config.getLlm().getProvider());
    }

    public static synchronized ShowcaseConfig getInstance() {
        if (instance == null) {
            instance = new ShowcaseConfig();
        }
        return instance;
    }

    /**
     * Creates a standard WebDriver.
     *
     * <p>The Java Agent automatically intercepts this driver and adds
     * self-healing capability. No HealingWebDriver wrapper needed!</p>
     *
     * @return a WebDriver instance with automatic self-healing
     */
    public WebDriver createDriver() {
        // Set up ChromeDriver using WebDriverManager
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-search-engine-choice-screen");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");

        // Just create a regular ChromeDriver - the agent handles the rest!
        this.driver = new ChromeDriver(options);

        printBanner();

        return driver;
    }

    private void printBanner() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  Intent Healer Showcase - ZERO-CODE Integration Demo");
        System.out.println("============================================================");
        System.out.println("  Integration:   Java Agent (no code changes needed!)");
        System.out.println("  Mode:          " + config.getMode());
        System.out.println("  LLM Provider:  " + config.getLlm().getProvider());
        System.out.println("  Auto-Healing:  ENABLED (via agent interception)");
        System.out.println("============================================================");
        System.out.println();
        System.out.println("  This demo uses regular WebDriver - NO HealingWebDriver!");
        System.out.println("  The Java Agent automatically adds self-healing.");
        System.out.println();
    }

    public WebDriver getDriver() {
        if (driver == null) {
            return createDriver();
        }
        return driver;
    }

    public void quitDriver() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    public HealerConfig getConfig() {
        return config;
    }

    public static void reset() {
        if (instance != null) {
            instance.quitDriver();
            instance = null;
        }
    }
}
