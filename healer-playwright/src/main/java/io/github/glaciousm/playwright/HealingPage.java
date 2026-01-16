package io.github.glaciousm.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.engine.HealingEngine;
import io.github.glaciousm.core.engine.HealingSummary;
import io.github.glaciousm.core.model.*;
import io.github.glaciousm.core.util.StackTraceAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Playwright Page wrapper that provides automatic healing capabilities.
 * Intercepts locator failures and attempts to heal using LLM.
 *
 * <h2>Usage</h2>
 * <pre>
 * Page page = browser.newPage();
 * HealingPage healingPage = new HealingPage(page, healingEngine, config);
 *
 * // Use like a regular Page - healing happens automatically
 * healingPage.locator("#login-btn").click();
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class uses ThreadLocal for intent context, making it safe for
 * parallel test execution when each thread uses its own HealingPage instance.</p>
 */
public class HealingPage {

    private static final Logger logger = LoggerFactory.getLogger(HealingPage.class);

    private final Page delegate;
    private final HealingEngine healingEngine;
    private final HealerConfig config;
    private final ThreadLocal<PlaywrightSnapshotBuilder> snapshotBuilderHolder;
    private final StackTraceAnalyzer stackTraceAnalyzer;

    private final ThreadLocal<IntentContract> currentIntent = new ThreadLocal<>();
    private final ThreadLocal<String> currentStepText = new ThreadLocal<>();

    public HealingPage(Page delegate, HealingEngine healingEngine, HealerConfig config) {
        this.delegate = delegate;
        this.healingEngine = healingEngine;
        this.config = config;
        this.snapshotBuilderHolder = ThreadLocal.withInitial(() -> new PlaywrightSnapshotBuilder(delegate));
        this.stackTraceAnalyzer = new StackTraceAnalyzer();
    }

    /**
     * Set the current intent context for healing.
     */
    public void setCurrentIntent(IntentContract intent, String stepText) {
        this.currentIntent.set(intent);
        this.currentStepText.set(stepText);
    }

    /**
     * Clear the current intent context.
     */
    public void clearCurrentIntent() {
        this.currentIntent.remove();
        this.currentStepText.remove();
    }

    /**
     * Clean up thread-local resources.
     */
    public void cleanupThreadResources() {
        this.currentIntent.remove();
        this.currentStepText.remove();
        this.snapshotBuilderHolder.remove();
    }

    /**
     * Get the underlying Page.
     */
    public Page getDelegate() {
        return delegate;
    }

    private PlaywrightSnapshotBuilder getSnapshotBuilder() {
        return snapshotBuilderHolder.get();
    }

    // ==================== Locator Methods ====================

    /**
     * Creates a healing-enabled locator.
     */
    public HealingLocator locator(String selector) {
        return new HealingLocator(delegate.locator(selector), selector, this);
    }

    /**
     * Creates a healing-enabled locator with options.
     */
    public HealingLocator locator(String selector, Page.LocatorOptions options) {
        return new HealingLocator(delegate.locator(selector, options), selector, this);
    }

    /**
     * Locate by role.
     */
    public HealingLocator getByRole(AriaRole role) {
        String selector = "role=" + role.name().toLowerCase();
        return new HealingLocator(delegate.getByRole(role), selector, this);
    }

    /**
     * Locate by role with options.
     */
    public HealingLocator getByRole(AriaRole role, Page.GetByRoleOptions options) {
        String selector = "role=" + role.name().toLowerCase();
        return new HealingLocator(delegate.getByRole(role, options), selector, this);
    }

    /**
     * Locate by text.
     */
    public HealingLocator getByText(String text) {
        String selector = "text=" + text;
        return new HealingLocator(delegate.getByText(text), selector, this);
    }

    /**
     * Locate by text with options.
     */
    public HealingLocator getByText(String text, Page.GetByTextOptions options) {
        String selector = "text=" + text;
        return new HealingLocator(delegate.getByText(text, options), selector, this);
    }

    /**
     * Locate by text pattern.
     */
    public HealingLocator getByText(Pattern text) {
        String selector = "text=/" + text.pattern() + "/";
        return new HealingLocator(delegate.getByText(text), selector, this);
    }

    /**
     * Locate by label.
     */
    public HealingLocator getByLabel(String text) {
        String selector = "label=" + text;
        return new HealingLocator(delegate.getByLabel(text), selector, this);
    }

    /**
     * Locate by placeholder.
     */
    public HealingLocator getByPlaceholder(String text) {
        String selector = "placeholder=" + text;
        return new HealingLocator(delegate.getByPlaceholder(text), selector, this);
    }

    /**
     * Locate by test ID.
     */
    public HealingLocator getByTestId(String testId) {
        String selector = "data-testid=" + testId;
        return new HealingLocator(delegate.getByTestId(testId), selector, this);
    }

    /**
     * Locate by alt text.
     */
    public HealingLocator getByAltText(String text) {
        String selector = "alt=" + text;
        return new HealingLocator(delegate.getByAltText(text), selector, this);
    }

    /**
     * Locate by title.
     */
    public HealingLocator getByTitle(String text) {
        String selector = "title=" + text;
        return new HealingLocator(delegate.getByTitle(text), selector, this);
    }

    // ==================== Page Navigation ====================

    public Response navigate(String url) {
        return delegate.navigate(url);
    }

    public Response navigate(String url, Page.NavigateOptions options) {
        return delegate.navigate(url, options);
    }

    public Response reload() {
        return delegate.reload();
    }

    public Response goBack() {
        return delegate.goBack();
    }

    public Response goForward() {
        return delegate.goForward();
    }

    // ==================== Page Properties ====================

    public String url() {
        return delegate.url();
    }

    public String title() {
        return delegate.title();
    }

    public String content() {
        return delegate.content();
    }

    // ==================== Screenshots ====================

    public byte[] screenshot() {
        return delegate.screenshot();
    }

    public byte[] screenshot(Page.ScreenshotOptions options) {
        return delegate.screenshot(options);
    }

    // ==================== Waiting ====================

    public void waitForTimeout(double timeout) {
        delegate.waitForTimeout(timeout);
    }

    public void waitForLoadState() {
        delegate.waitForLoadState();
    }

    public void waitForLoadState(LoadState state) {
        delegate.waitForLoadState(state);
    }

    public void waitForURL(String url) {
        delegate.waitForURL(url);
    }

    public void waitForURL(Pattern url) {
        delegate.waitForURL(url);
    }

    public JSHandle waitForFunction(String expression) {
        return delegate.waitForFunction(expression);
    }

    public ElementHandle waitForSelector(String selector) {
        return delegate.waitForSelector(selector);
    }

    // ==================== JavaScript ====================

    public Object evaluate(String expression) {
        return delegate.evaluate(expression);
    }

    public Object evaluate(String expression, Object arg) {
        return delegate.evaluate(expression, arg);
    }

    public JSHandle evaluateHandle(String expression) {
        return delegate.evaluateHandle(expression);
    }

    // ==================== Frames ====================

    public Frame mainFrame() {
        return delegate.mainFrame();
    }

    public List<Frame> frames() {
        return delegate.frames();
    }

    public Frame frame(String name) {
        return delegate.frame(name);
    }

    public Frame frameLocator(String selector) {
        return delegate.frame(selector);
    }

    // ==================== Dialogs ====================

    public void onDialog(java.util.function.Consumer<Dialog> handler) {
        delegate.onDialog(handler);
    }

    // ==================== Keyboard & Mouse ====================

    public Keyboard keyboard() {
        return delegate.keyboard();
    }

    public Mouse mouse() {
        return delegate.mouse();
    }

    public Touchscreen touchscreen() {
        return delegate.touchscreen();
    }

    // ==================== Page Lifecycle ====================

    public void close() {
        cleanupThreadResources();
        delegate.close();
    }

    public boolean isClosed() {
        return delegate.isClosed();
    }

    public void bringToFront() {
        delegate.bringToFront();
    }

    // ==================== Healing Logic ====================

    /**
     * Attempt to heal a failed locator.
     * Called by HealingLocator when an action fails.
     */
    Locator attemptHeal(String originalSelector, RuntimeException originalException) {
        if (!shouldAttemptHeal(originalException)) {
            return null;
        }

        String beforeScreenshotBase64 = captureScreenshotBase64();

        try {
            LocatorInfo originalLocator = parseSelector(originalSelector);
            UiSnapshot snapshot = getSnapshotBuilder().captureAll();

            String stepText = currentStepText.get();
            IntentContract intent = currentIntent.get();

            SourceLocation sourceLocation = stackTraceAnalyzer
                    .extractSourceLocationWithContext(originalException)
                    .orElse(null);

            String effectiveStepText = stepText != null ? stepText : "locate: " + originalSelector;

            FailureContext failureContext = FailureContext.builder()
                    .exceptionType(originalException.getClass().getSimpleName())
                    .exceptionMessage(originalException.getMessage())
                    .originalLocator(originalLocator)
                    .stepText(effectiveStepText)
                    .sourceLocation(sourceLocation)
                    .build();

            IntentContract intentToUse = intent != null
                    ? intent
                    : IntentContract.defaultContract(effectiveStepText);

            HealResult result = healingEngine.attemptHeal(failureContext, intentToUse);

            if (result != null && result.isSuccess() && result.getHealedLocator().isPresent()) {
                String healedSelector = result.getHealedLocator().get();
                logger.info("Healed locator: {} -> {}", originalSelector, healedSelector);

                String afterScreenshotBase64 = captureScreenshotBase64();

                // Record heal for summary report with full source location
                HealingSummary.getInstance().recordHealWithLocation(
                        effectiveStepText,
                        originalSelector,
                        healedSelector,
                        result.getConfidence(),
                        sourceLocation,
                        beforeScreenshotBase64,
                        afterScreenshotBase64
                );

                return delegate.locator(healedSelector);
            }

        } catch (Exception healException) {
            logger.warn("Healing attempt failed: {}", healException.getMessage());
        }

        return null;
    }

    private boolean shouldAttemptHeal(RuntimeException e) {
        IntentContract intent = currentIntent.get();
        if (intent != null && !intent.isHealingAllowed()) {
            return false;
        }

        String message = e.getMessage();
        return message != null && (
                message.contains("Timeout") ||
                message.contains("waiting for") ||
                message.contains("strict mode violation") ||
                message.contains("no element matching")
        );
    }

    private LocatorInfo parseSelector(String selector) {
        if (selector.startsWith("#")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, selector);
        }
        if (selector.startsWith("//") || selector.startsWith("(//")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.XPATH, selector);
        }
        if (selector.startsWith("text=")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.LINK_TEXT, selector.substring(5));
        }
        if (selector.startsWith("data-testid=")) {
            return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, "[data-testid='" + selector.substring(12) + "']");
        }

        return new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, selector);
    }

    private String captureScreenshotBase64() {
        try {
            byte[] screenshot = delegate.screenshot();
            return Base64.getEncoder().encodeToString(screenshot);
        } catch (Exception e) {
            logger.debug("Failed to capture screenshot: {}", e.getMessage());
            return null;
        }
    }
}
