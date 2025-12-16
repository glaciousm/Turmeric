package com.intenthealer.example.config;

import com.intenthealer.core.config.ConfigLoader;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.llm.LlmOrchestrator;
import com.intenthealer.selenium.driver.HealingWebDriver;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Configuration for the Intent Healer example project.
 */
public class HealerTestConfig {

    private static HealerTestConfig instance;

    private final HealerConfig config;
    private final HealingEngine healingEngine;
    private final LlmOrchestrator llmOrchestrator;
    private HealingWebDriver driver;

    private HealerTestConfig() {
        // Load configuration from YAML file or use defaults
        this.config = new ConfigLoader().load();

        // Initialize LLM orchestrator
        this.llmOrchestrator = new LlmOrchestrator();

        // Initialize healing engine
        this.healingEngine = new HealingEngine(config);
    }

    public static synchronized HealerTestConfig getInstance() {
        if (instance == null) {
            instance = new HealerTestConfig();
        }
        return instance;
    }

    /**
     * Create and initialize a healing WebDriver.
     */
    public HealingWebDriver createDriver() {
        // Set up ChromeDriver
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        WebDriver chromeDriver = new ChromeDriver(options);
        this.driver = new HealingWebDriver(chromeDriver, healingEngine, config);
        return driver;
    }

    /**
     * Get the current healing WebDriver.
     */
    public HealingWebDriver getDriver() {
        return driver;
    }

    /**
     * Get the healing engine.
     */
    public HealingEngine getHealingEngine() {
        return healingEngine;
    }

    /**
     * Get the healer configuration.
     */
    public HealerConfig getConfig() {
        return config;
    }

    /**
     * Shut down all resources.
     */
    public void shutdown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
