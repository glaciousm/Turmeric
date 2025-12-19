package io.github.glaciousm.selenium.actions;

import io.github.glaciousm.core.config.GuardrailConfig;
import io.github.glaciousm.core.exception.HealingException;
import io.github.glaciousm.core.model.ActionType;
import io.github.glaciousm.core.model.ElementSnapshot;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.UnexpectedTagNameException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * Executes actions on web elements with retry strategies.
 */
public class ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ActionExecutor.class);

    private final WebDriver driver;
    private final GuardrailConfig guardrailConfig;

    public ActionExecutor(WebDriver driver, GuardrailConfig guardrailConfig) {
        this.driver = Objects.requireNonNull(driver, "driver cannot be null");
        this.guardrailConfig = guardrailConfig != null ? guardrailConfig : new GuardrailConfig();
    }

    public ActionExecutor(WebDriver driver) {
        this(driver, new GuardrailConfig());
    }

    /**
     * Execute an action on the target element.
     */
    public void execute(ActionType action, ElementSnapshot targetSnapshot, Object actionData) {
        WebElement element = refindElement(targetSnapshot);

        switch (action) {
            case CLICK -> executeClick(element);
            case TYPE -> executeType(element, (String) actionData);
            case SELECT -> executeSelect(element, (String) actionData);
            case CLEAR -> executeClear(element);
            case HOVER -> executeHover(element);
            case DOUBLE_CLICK -> executeDoubleClick(element);
            case RIGHT_CLICK -> executeRightClick(element);
            case SUBMIT -> executeSubmit(element);
            default -> throw new HealingException("Unsupported action type: " + action,
                    HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
        }
    }

    private void executeClick(WebElement element) {
        // Try strategies in order until one works
        Exception lastException = null;

        // Strategy 1: Standard click
        try {
            element.click();
            return;
        } catch (ElementClickInterceptedException e) {
            lastException = e;
            logger.debug("Standard click failed, trying scroll: {}", e.getMessage());
        } catch (ElementNotInteractableException e) {
            lastException = e;
            logger.debug("Standard click failed, trying scroll: {}", e.getMessage());
        }

        // Strategy 2: Scroll then click
        try {
            scrollIntoView(element);
            Thread.sleep(100);
            element.click();
            return;
        } catch (Exception e) {
            lastException = e;
            logger.debug("Scroll+click failed, trying wait: {}", e.getMessage());
        }

        // Strategy 3: Wait for clickable then click
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            wait.until(ExpectedConditions.elementToBeClickable(element));
            element.click();
            return;
        } catch (Exception e) {
            lastException = e;
            logger.debug("Wait+click failed, trying Actions: {}", e.getMessage());
        }

        // Strategy 4: Actions click
        try {
            new Actions(driver).moveToElement(element).click().perform();
            return;
        } catch (Exception e) {
            lastException = e;
            logger.debug("Actions click failed: {}", e.getMessage());
        }

        // Strategy 5: JavaScript click (if allowed)
        if (guardrailConfig.isAllowJsClick()) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                return;
            } catch (Exception e) {
                lastException = e;
                logger.debug("JS click failed: {}", e.getMessage());
            }
        }

        throw new HealingException("All click strategies failed: " + lastException.getMessage(),
                lastException, HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
    }

    private void executeType(WebElement element, String text) {
        try {
            element.clear();
            element.sendKeys(text);
        } catch (WebDriverException e) {
            // Try with JS
            try {
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].value = ''; arguments[0].value = arguments[1];",
                        element, text);
            } catch (WebDriverException e2) {
                throw new HealingException("Failed to type text: " + e.getMessage(),
                        e, HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
            }
        }
    }

    private void executeSelect(WebElement element, String value) {
        try {
            Select select = new Select(element);

            // Try by visible text first
            try {
                select.selectByVisibleText(value);
                return;
            } catch (NoSuchElementException e) {
                logger.debug("Select by text failed, trying value");
            }

            // Try by value
            try {
                select.selectByValue(value);
                return;
            } catch (NoSuchElementException e) {
                logger.debug("Select by value failed, trying index");
            }

            // Try by index if numeric
            try {
                int index = Integer.parseInt(value);
                select.selectByIndex(index);
            } catch (NumberFormatException e) {
                throw new HealingException("Could not select option: " + value,
                        HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
            }
        } catch (UnexpectedTagNameException e) {
            throw new HealingException("Element is not a select: " + element.getTagName(),
                    e, HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
        }
    }

    private void executeClear(WebElement element) {
        try {
            element.clear();
        } catch (WebDriverException e) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].value = '';", element);
            } catch (WebDriverException e2) {
                throw new HealingException("Failed to clear element: " + e.getMessage(),
                        e, HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
            }
        }
    }

    private void executeHover(WebElement element) {
        try {
            new Actions(driver).moveToElement(element).perform();
        } catch (WebDriverException e) {
            throw new HealingException("Failed to hover: " + e.getMessage(),
                    e, HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
        }
    }

    private void executeDoubleClick(WebElement element) {
        try {
            new Actions(driver).doubleClick(element).perform();
        } catch (WebDriverException e) {
            throw new HealingException("Failed to double-click: " + e.getMessage(),
                    e, HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
        }
    }

    private void executeRightClick(WebElement element) {
        try {
            new Actions(driver).contextClick(element).perform();
        } catch (WebDriverException e) {
            throw new HealingException("Failed to right-click: " + e.getMessage(),
                    e, HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
        }
    }

    private void executeSubmit(WebElement element) {
        try {
            element.submit();
        } catch (WebDriverException e) {
            // Try clicking if submit fails
            try {
                executeClick(element);
            } catch (HealingException e2) {
                throw new HealingException("Failed to submit: " + e.getMessage(),
                        e, HealingException.HealingFailureReason.ACTION_EXECUTION_FAILED);
            }
        }
    }

    private void scrollIntoView(WebElement element) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});",
                element);
    }

    /**
     * Re-find an element from its snapshot.
     */
    public WebElement refindElement(ElementSnapshot snapshot) {
        // Strategy 1: data-testid (most stable)
        String testId = snapshot.getDataTestId();
        if (testId != null && !testId.isEmpty()) {
            try {
                return driver.findElement(By.cssSelector("[data-testid='" + testId + "']"));
            } catch (NoSuchElementException ignored) {
            }
        }

        // Strategy 2: ID (if present and not dynamic-looking)
        String id = snapshot.getId();
        if (id != null && !id.isEmpty() && !looksGenerated(id)) {
            try {
                return driver.findElement(By.id(id));
            } catch (NoSuchElementException ignored) {
            }
        }

        // Strategy 3: Unique aria-label
        String ariaLabel = snapshot.getAriaLabel();
        if (ariaLabel != null && !ariaLabel.isEmpty()) {
            try {
                return driver.findElement(By.cssSelector("[aria-label='" + escapeForCss(ariaLabel) + "']"));
            } catch (NoSuchElementException ignored) {
            }
        }

        // Strategy 4: Text content + tag
        String text = snapshot.getText();
        if (text != null && !text.isEmpty()) {
            String xpath = String.format("//%s[normalize-space(text())='%s']",
                    snapshot.getTagName(), escapeForXPath(text));
            try {
                return driver.findElement(By.xpath(xpath));
            } catch (NoSuchElementException ignored) {
            }
        }

        // Strategy 5: Name attribute
        String name = snapshot.getName();
        if (name != null && !name.isEmpty()) {
            try {
                return driver.findElement(By.name(name));
            } catch (NoSuchElementException ignored) {
            }
        }

        throw new HealingException("Could not re-find element from snapshot: " + snapshot,
                HealingException.HealingFailureReason.ELEMENT_NOT_REFINDABLE);
    }

    private boolean looksGenerated(String id) {
        // IDs that look auto-generated (UUIDs, random strings, etc.)
        return id.matches(".*[0-9a-f]{8,}.*") ||
               id.matches(".*_\\d+$") ||
               id.matches("^[a-z]{1,3}\\d+$");
    }

    private String escapeForCss(String value) {
        return value.replace("'", "\\'").replace("\"", "\\\"");
    }

    private String escapeForXPath(String value) {
        if (!value.contains("'")) {
            return value;
        }
        if (!value.contains("\"")) {
            return value;
        }
        // Contains both quotes - use concat
        return value.replace("'", "',\"'\",'");
    }
}
