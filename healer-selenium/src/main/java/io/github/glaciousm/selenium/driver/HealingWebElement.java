package io.github.glaciousm.selenium.driver;

import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * WebElement wrapper that provides automatic recovery from stale element exceptions.
 */
public class HealingWebElement implements WebElement {

    private static final Logger logger = LoggerFactory.getLogger(HealingWebElement.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // Volatile ensures visibility across threads when delegate is refreshed
    private volatile WebElement delegate;
    private final By locator;
    private final HealingWebDriver driver;

    public HealingWebElement(WebElement delegate, By locator, HealingWebDriver driver) {
        this.delegate = delegate;
        this.locator = locator;
        this.driver = driver;
    }

    @Override
    public void click() {
        executeWithRetry(() -> delegate.click());
    }

    @Override
    public void submit() {
        executeWithRetry(() -> delegate.submit());
    }

    @Override
    public void sendKeys(CharSequence... keysToSend) {
        executeWithRetry(() -> delegate.sendKeys(keysToSend));
    }

    @Override
    public void clear() {
        executeWithRetry(() -> delegate.clear());
    }

    @Override
    public String getTagName() {
        return executeWithRetry(() -> delegate.getTagName());
    }

    @Override
    public String getAttribute(String name) {
        return executeWithRetry(() -> delegate.getAttribute(name));
    }

    @Override
    public boolean isSelected() {
        return executeWithRetry(() -> delegate.isSelected());
    }

    @Override
    public boolean isEnabled() {
        return executeWithRetry(() -> delegate.isEnabled());
    }

    @Override
    public String getText() {
        return executeWithRetry(() -> delegate.getText());
    }

    @Override
    public List<WebElement> findElements(By by) {
        return executeWithRetry(() -> delegate.findElements(by));
    }

    @Override
    public WebElement findElement(By by) {
        return executeWithRetry(() -> delegate.findElement(by));
    }

    @Override
    public boolean isDisplayed() {
        return executeWithRetry(() -> delegate.isDisplayed());
    }

    @Override
    public Point getLocation() {
        return executeWithRetry(() -> delegate.getLocation());
    }

    @Override
    public Dimension getSize() {
        return executeWithRetry(() -> delegate.getSize());
    }

    @Override
    public Rectangle getRect() {
        return executeWithRetry(() -> delegate.getRect());
    }

    @Override
    public String getCssValue(String propertyName) {
        return executeWithRetry(() -> delegate.getCssValue(propertyName));
    }

    @Override
    public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
        return executeWithRetry(() -> delegate.getScreenshotAs(target));
    }

    // Selenium 4 methods
    @Override
    public String getDomAttribute(String name) {
        return executeWithRetry(() -> delegate.getDomAttribute(name));
    }

    @Override
    public String getDomProperty(String name) {
        return executeWithRetry(() -> delegate.getDomProperty(name));
    }

    @Override
    public SearchContext getShadowRoot() {
        return executeWithRetry(() -> delegate.getShadowRoot());
    }

    @Override
    public String getAriaRole() {
        return executeWithRetry(() -> delegate.getAriaRole());
    }

    @Override
    public String getAccessibleName() {
        return executeWithRetry(() -> delegate.getAccessibleName());
    }

    /**
     * Execute an action with automatic retry on stale element exception.
     */
    private void executeWithRetry(Runnable action) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                action.run();
                return;
            } catch (StaleElementReferenceException e) {
                logger.debug("Stale element on attempt {}, re-finding element", attempt + 1);
                refreshElement();
            }
        }
        // Final attempt without catch
        action.run();
    }

    /**
     * Execute an action with automatic retry on stale element exception.
     */
    private <T> T executeWithRetry(java.util.function.Supplier<T> action) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (StaleElementReferenceException e) {
                logger.debug("Stale element on attempt {}, re-finding element", attempt + 1);
                refreshElement();
            }
        }
        // Final attempt without catch
        return action.get();
    }

    /**
     * Refresh the element reference by re-finding it.
     * Synchronized to prevent race conditions when multiple threads
     * attempt to refresh the same element concurrently.
     */
    private synchronized void refreshElement() {
        if (locator != null) {
            delegate = driver.refindElement(locator);
        }
    }

    /**
     * Get the underlying delegate element.
     */
    public WebElement getDelegate() {
        return delegate;
    }

    /**
     * Get the locator used to find this element.
     */
    public By getLocator() {
        return locator;
    }

    @Override
    public String toString() {
        return "HealingWebElement{locator=" + locator + "}";
    }
}
