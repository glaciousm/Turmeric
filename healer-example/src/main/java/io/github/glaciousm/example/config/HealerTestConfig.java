package io.github.glaciousm.example.config;

import io.github.glaciousm.core.config.ConfigLoader;
import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.engine.HealingEngine;
import io.github.glaciousm.core.model.IntentContract;
import io.github.glaciousm.llm.LlmOrchestrator;
import io.github.glaciousm.selenium.driver.HealingWebDriver;
import io.github.glaciousm.selenium.snapshot.SnapshotBuilder;
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
        // Run in visible mode for testing (not headless)
        // options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");

        WebDriver chromeDriver = new ChromeDriver(options);
        this.driver = new HealingWebDriver(chromeDriver, healingEngine, config);

        // Configure healing engine with snapshot capture and LLM evaluator
        SnapshotBuilder snapshotBuilder = new SnapshotBuilder(chromeDriver);
        healingEngine.setSnapshotCapture(failure -> snapshotBuilder.captureAll());
        healingEngine.setLlmEvaluator((failure, snapshot) -> {
            IntentContract intent = IntentContract.defaultContract(failure.getStepText());
            return llmOrchestrator.evaluateCandidates(failure, snapshot, intent, config.getLlm());
        });

        System.out.println("=".repeat(60));
        System.out.println("HealingWebDriver created successfully");
        System.out.println("Healer mode: " + config.getMode());
        System.out.println("LLM provider: " + config.getLlm().getProvider());
        System.out.println("Auto-update enabled: " +
                (config.getAutoUpdate() != null && config.getAutoUpdate().isEnabled()));
        System.out.println("=".repeat(60));

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
