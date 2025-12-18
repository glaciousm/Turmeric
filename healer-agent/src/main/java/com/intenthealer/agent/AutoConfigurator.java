package com.intenthealer.agent;

import com.intenthealer.core.config.ConfigLoader;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.core.engine.HealingSummary;
import com.intenthealer.core.model.*;
import com.intenthealer.core.util.StackTraceAnalyzer;
import com.intenthealer.llm.LlmOrchestrator;
import com.intenthealer.selenium.snapshot.SnapshotBuilder;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Auto-configures the Intent Healer components for agent-based operation.
 *
 * <p>This class manages the healing engine, LLM orchestrator, and driver registration.
 * It provides the bridge between the ByteBuddy instrumentation and the Intent Healer
 * healing pipeline.</p>
 */
public class AutoConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(AutoConfigurator.class);

    private static volatile HealerConfig config;
    private static volatile HealingEngine engine;
    private static volatile LlmOrchestrator llmOrchestrator;
    private static volatile boolean initialized = false;

    // Track registered drivers (WeakHashMap allows garbage collection)
    private static final Map<WebDriver, SnapshotBuilder> driverSnapshots =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final StackTraceAnalyzer stackTraceAnalyzer = new StackTraceAnalyzer();

    /**
     * Initialize the healing configuration and engine.
     * This is called once when the agent starts.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            // Load configuration
            config = new ConfigLoader().load();

            if (config.isEnabled()) {
                // Create healing engine
                engine = new HealingEngine(config);

                // Create LLM orchestrator
                llmOrchestrator = new LlmOrchestrator();

                // Wire the engine with snapshot and LLM callbacks
                wireEngine();

                logger.info("Auto-configured healing engine with provider: {}",
                        config.getLlm() != null ? config.getLlm().getProvider() : "mock");
            } else {
                logger.info("Healing is disabled in configuration");
            }

        } catch (Exception e) {
            logger.error("Failed to initialize healing configuration", e);
            config = null;
            engine = null;
        }
    }

    /**
     * Check if healing is enabled.
     */
    public static boolean isEnabled() {
        return config != null && config.isEnabled() && engine != null;
    }

    /**
     * Get the current configuration.
     */
    public static HealerConfig getConfig() {
        return config;
    }

    /**
     * Get the healing engine.
     */
    public static HealingEngine getEngine() {
        return engine;
    }

    /**
     * Register a WebDriver instance for healing.
     * Called by the constructor advice when a new WebDriver is created.
     */
    public static void registerDriver(WebDriver driver) {
        if (!isEnabled()) {
            return;
        }

        synchronized (driverSnapshots) {
            if (!driverSnapshots.containsKey(driver)) {
                SnapshotBuilder snapshotBuilder = new SnapshotBuilder(driver);
                driverSnapshots.put(driver, snapshotBuilder);
                logger.debug("Registered driver for healing: {}", driver.getClass().getName());
            }
        }
    }

    /**
     * Attempt to heal a failed findElement call.
     *
     * @param driver the WebDriver instance
     * @param by the original locator that failed
     * @param originalException the exception that was thrown
     * @return the healed WebElement, or null if healing failed
     */
    public static WebElement heal(WebDriver driver, By by, Throwable originalException) {
        if (!isEnabled()) {
            return null;
        }

        SnapshotBuilder snapshotBuilder = driverSnapshots.get(driver);
        if (snapshotBuilder == null) {
            // Driver wasn't registered, create a snapshot builder on-the-fly
            snapshotBuilder = new SnapshotBuilder(driver);
            driverSnapshots.put(driver, snapshotBuilder);
        }

        // Capture screenshot BEFORE healing attempt (for visual evidence)
        String beforeScreenshotBase64 = captureScreenshotBase64(driver);

        try {
            // Convert By to LocatorInfo
            LocatorInfo originalLocator = byToLocatorInfo(by);

            // Capture UI snapshot
            UiSnapshot snapshot = snapshotBuilder.captureAll();

            // Extract source location from stack trace
            SourceLocation sourceLocation = stackTraceAnalyzer
                    .extractSourceLocationWithContext((Exception) originalException)
                    .orElse(null);

            if (sourceLocation != null) {
                logger.debug("Captured source location: {}", sourceLocation.toShortString());
            }

            // Build failure context
            String effectiveStepText = "find element: " + by.toString();

            FailureContext failureContext = FailureContext.builder()
                    .exceptionType(originalException.getClass().getSimpleName())
                    .exceptionMessage(originalException.getMessage())
                    .originalLocator(originalLocator)
                    .stepText(effectiveStepText)
                    .sourceLocation(sourceLocation)
                    .build();

            IntentContract intent = IntentContract.defaultContract("find element");

            // Attempt healing
            HealResult result = engine.attemptHeal(failureContext, intent);

            if (result != null && result.isSuccess() && result.getHealedLocator().isPresent()) {
                String healedLocatorStr = result.getHealedLocator().get();
                LocatorInfo healedLocator = parseLocatorString(healedLocatorStr);
                By healedBy = locatorInfoToBy(healedLocator);

                logger.info("Healed locator: {} -> {}", by, healedBy);

                // Capture screenshot AFTER successful healing
                String afterScreenshotBase64 = captureScreenshotBase64(driver);

                // Record heal for summary report with visual evidence
                HealingSummary.getInstance().recordHealWithScreenshots(
                        effectiveStepText,
                        by.toString(),
                        healedBy.toString(),
                        result.getConfidence(),
                        sourceLocation != null ? sourceLocation.getFilePath() : null,
                        sourceLocation != null ? sourceLocation.getLineNumber() : 0,
                        beforeScreenshotBase64,
                        afterScreenshotBase64
                );

                // Find element with healed locator
                return driver.findElement(healedBy);
            }

        } catch (Exception healException) {
            logger.warn("Healing attempt failed: {}", healException.getMessage());
        }

        return null;
    }

    /**
     * Wire the healing engine with snapshot capture and LLM evaluation functions.
     */
    private static void wireEngine() {
        // Set snapshot capture function
        engine.setSnapshotCapture(failure -> {
            // Since we don't have direct access to driver from failure context,
            // the heal() method handles snapshot capture directly
            return null;
        });

        // Set LLM evaluator function
        engine.setLlmEvaluator((failure, snapshot) -> {
            IntentContract intent = IntentContract.defaultContract(failure.getStepText());
            return llmOrchestrator.evaluateCandidates(failure, snapshot, intent, config.getLlm());
        });
    }

    /**
     * Capture a screenshot and return it as a Base64-encoded string.
     */
    private static String captureScreenshotBase64(WebDriver driver) {
        if (!(driver instanceof TakesScreenshot)) {
            return null;
        }

        try {
            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            return Base64.getEncoder().encodeToString(screenshotBytes);
        } catch (Exception e) {
            logger.debug("Failed to capture screenshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convert Selenium By to LocatorInfo.
     */
    private static LocatorInfo byToLocatorInfo(By by) {
        String byString = by.toString();
        LocatorInfo.LocatorStrategy strategy = LocatorInfo.LocatorStrategy.CSS;
        String value = byString;

        if (byString.startsWith("By.id:")) {
            strategy = LocatorInfo.LocatorStrategy.ID;
            value = byString.substring("By.id:".length()).trim();
        } else if (byString.startsWith("By.name:")) {
            strategy = LocatorInfo.LocatorStrategy.NAME;
            value = byString.substring("By.name:".length()).trim();
        } else if (byString.startsWith("By.className:")) {
            strategy = LocatorInfo.LocatorStrategy.CLASS_NAME;
            value = byString.substring("By.className:".length()).trim();
        } else if (byString.startsWith("By.cssSelector:")) {
            strategy = LocatorInfo.LocatorStrategy.CSS;
            value = byString.substring("By.cssSelector:".length()).trim();
        } else if (byString.startsWith("By.xpath:")) {
            strategy = LocatorInfo.LocatorStrategy.XPATH;
            value = byString.substring("By.xpath:".length()).trim();
        } else if (byString.startsWith("By.linkText:")) {
            strategy = LocatorInfo.LocatorStrategy.LINK_TEXT;
            value = byString.substring("By.linkText:".length()).trim();
        } else if (byString.startsWith("By.partialLinkText:")) {
            strategy = LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT;
            value = byString.substring("By.partialLinkText:".length()).trim();
        } else if (byString.startsWith("By.tagName:")) {
            strategy = LocatorInfo.LocatorStrategy.TAG_NAME;
            value = byString.substring("By.tagName:".length()).trim();
        }

        return new LocatorInfo(strategy, value);
    }

    /**
     * Parse a locator string into LocatorInfo.
     */
    private static LocatorInfo parseLocatorString(String locatorStr) {
        if (locatorStr == null || locatorStr.isEmpty()) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, "");
        }

        // Handle CSS selector format (starts with tag, #, or .)
        if (locatorStr.startsWith("#")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, locatorStr);
        }
        if (locatorStr.startsWith(".")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, locatorStr);
        }
        if (locatorStr.startsWith("//") || locatorStr.startsWith("(//")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.XPATH, locatorStr);
        }

        // Handle explicit strategy format: "strategy:value"
        if (locatorStr.contains(":")) {
            int colonIndex = locatorStr.indexOf(':');
            String strategyPart = locatorStr.substring(0, colonIndex).toLowerCase().trim();
            String valuePart = locatorStr.substring(colonIndex + 1).trim();

            LocatorInfo.LocatorStrategy strategy = switch (strategyPart) {
                case "id" -> LocatorInfo.LocatorStrategy.ID;
                case "name" -> LocatorInfo.LocatorStrategy.NAME;
                case "class", "classname" -> LocatorInfo.LocatorStrategy.CLASS_NAME;
                case "css", "cssselector" -> LocatorInfo.LocatorStrategy.CSS;
                case "xpath" -> LocatorInfo.LocatorStrategy.XPATH;
                case "linktext" -> LocatorInfo.LocatorStrategy.LINK_TEXT;
                case "partiallinktext" -> LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT;
                case "tagname" -> LocatorInfo.LocatorStrategy.TAG_NAME;
                default -> LocatorInfo.LocatorStrategy.CSS;
            };

            return new LocatorInfo(strategy, valuePart);
        }

        // Default to CSS selector
        return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, locatorStr);
    }

    /**
     * Convert LocatorInfo to Selenium By.
     */
    private static By locatorInfoToBy(LocatorInfo locator) {
        return switch (locator.getStrategy()) {
            case ID -> By.id(locator.getValue());
            case NAME -> By.name(locator.getValue());
            case CLASS_NAME -> By.className(locator.getValue());
            case CSS -> By.cssSelector(locator.getValue());
            case XPATH -> By.xpath(locator.getValue());
            case LINK_TEXT -> By.linkText(locator.getValue());
            case PARTIAL_LINK_TEXT -> By.partialLinkText(locator.getValue());
            case TAG_NAME -> By.tagName(locator.getValue());
        };
    }
}
