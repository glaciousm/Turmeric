package com.intenthealer.selenium.snapshot;

import com.intenthealer.core.config.SnapshotConfig;
import com.intenthealer.core.model.*;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Builds UI snapshots from Selenium WebDriver state.
 */
public class SnapshotBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotBuilder.class);

    private final WebDriver driver;
    private final SnapshotConfig config;

    public SnapshotBuilder(WebDriver driver, SnapshotConfig config) {
        this.driver = Objects.requireNonNull(driver, "driver cannot be null");
        this.config = config != null ? config : new SnapshotConfig();
    }

    public SnapshotBuilder(WebDriver driver) {
        this(driver, new SnapshotConfig());
    }

    /**
     * Capture a UI snapshot based on the failure context.
     */
    public UiSnapshot capture(FailureContext failure) {
        UiSnapshot.Builder builder = UiSnapshot.builder()
                .url(driver.getCurrentUrl())
                .title(driver.getTitle())
                .timestamp(Instant.now())
                .detectedLanguage(detectLanguage());

        // Capture elements based on action type
        List<ElementSnapshot> elements = switch (failure.getActionType()) {
            case CLICK -> captureClickableElements();
            case TYPE -> captureInputElements();
            case SELECT -> captureSelectElements();
            default -> captureAllInteractiveElements();
        };

        builder.interactiveElements(elements);

        // Capture artifacts if configured
        if (config.isCaptureScreenshot()) {
            builder.screenshotBase64(captureScreenshot());
        }
        if (config.isCaptureDom()) {
            builder.domSnapshot(captureDom());
        }

        return builder.build();
    }

    /**
     * Capture all interactive elements on the page.
     */
    public UiSnapshot captureAll() {
        return UiSnapshot.builder()
                .url(driver.getCurrentUrl())
                .title(driver.getTitle())
                .timestamp(Instant.now())
                .detectedLanguage(detectLanguage())
                .interactiveElements(captureAllInteractiveElements())
                .build();
    }

    /**
     * Capture a UI snapshot without failure context.
     */
    public UiSnapshot captureSnapshot() {
        return captureAll();
    }

    private List<ElementSnapshot> captureClickableElements() {
        String script = """
            return Array.from(document.querySelectorAll(
                'button, a, [role="button"], [role="link"], input[type="submit"],
                input[type="button"], [onclick], [ng-click], [data-action], [tabindex]'
            )).filter(el => {
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 &&
                       style.visibility !== 'hidden' &&
                       style.display !== 'none';
            }).slice(0, %d);
            """.formatted(config.getMaxElements());

        return captureElements(script);
    }

    private List<ElementSnapshot> captureInputElements() {
        String script = """
            return Array.from(document.querySelectorAll(
                'input:not([type="hidden"]):not([type="submit"]):not([type="button"]),
                textarea, [contenteditable="true"]'
            )).filter(el => {
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 &&
                       style.visibility !== 'hidden' &&
                       style.display !== 'none';
            }).slice(0, %d);
            """.formatted(config.getMaxElements());

        return captureElements(script);
    }

    private List<ElementSnapshot> captureSelectElements() {
        String script = """
            return Array.from(document.querySelectorAll(
                'select, [role="listbox"], [role="combobox"]'
            )).filter(el => {
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 &&
                       style.visibility !== 'hidden' &&
                       style.display !== 'none';
            }).slice(0, %d);
            """.formatted(config.getMaxElements());

        return captureElements(script);
    }

    private List<ElementSnapshot> captureAllInteractiveElements() {
        String script = """
            return Array.from(document.querySelectorAll(
                'button, a, input, select, textarea, [role="button"], [role="link"],
                [role="listbox"], [role="combobox"], [onclick], [tabindex]:not([tabindex="-1"])'
            )).filter(el => {
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                const isVisible = rect.width > 0 && rect.height > 0 &&
                                  style.visibility !== 'hidden' &&
                                  style.display !== 'none';
                const isHidden = el.type === 'hidden';
                return isVisible && !isHidden;
            }).slice(0, %d);
            """.formatted(config.getMaxElements());

        return captureElements(script);
    }

    @SuppressWarnings("unchecked")
    private List<ElementSnapshot> captureElements(String script) {
        List<ElementSnapshot> snapshots = new ArrayList<>();
        try {
            List<WebElement> elements = (List<WebElement>)
                    ((JavascriptExecutor) driver).executeScript(script);

            int index = 0;
            for (WebElement element : elements) {
                try {
                    ElementSnapshot snapshot = captureElement(element, index++);
                    if (snapshot != null) {
                        snapshots.add(snapshot);
                    }
                } catch (StaleElementReferenceException e) {
                    logger.debug("Element became stale during capture");
                }
            }
        } catch (WebDriverException e) {
            logger.warn("Failed to capture elements: {}", e.getMessage());
        }
        return snapshots;
    }

    private ElementSnapshot captureElement(WebElement element, int index) {
        ElementSnapshot.Builder builder = ElementSnapshot.builder()
                .index(index);

        try {
            builder.tagName(element.getTagName())
                    .id(element.getAttribute("id"))
                    .name(element.getAttribute("name"))
                    .type(element.getAttribute("type"))
                    .classes(parseClasses(element.getAttribute("class")))
                    .text(normalizeText(element.getText()))
                    .value(element.getAttribute("value"))
                    .placeholder(element.getAttribute("placeholder"))
                    .ariaLabel(element.getAttribute("aria-label"))
                    .ariaLabelledBy(element.getAttribute("aria-labelledby"))
                    .ariaDescribedBy(element.getAttribute("aria-describedby"))
                    .ariaRole(element.getAttribute("role"))
                    .title(element.getAttribute("title"))
                    .visible(element.isDisplayed())
                    .enabled(element.isEnabled())
                    .selected(element.isSelected());

            // Capture rect
            Rectangle rect = element.getRect();
            builder.rect(new ElementRect(rect.x, rect.y, rect.width, rect.height));

            // Capture container
            builder.container(findContainer(element));

            // Capture nearby labels
            builder.nearbyLabels(findNearbyLabels(element));

            // Capture data attributes
            builder.dataAttributes(captureDataAttributes(element));

        } catch (StaleElementReferenceException e) {
            return null;
        } catch (WebDriverException e) {
            logger.debug("Error capturing element attributes: {}", e.getMessage());
        }

        return builder.build();
    }

    private List<String> parseClasses(String classAttr) {
        if (classAttr == null || classAttr.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(classAttr.split("\\s+"));
    }

    private String normalizeText(String text) {
        if (text == null) return null;
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() > config.getMaxTextLength()) {
            return normalized.substring(0, config.getMaxTextLength() - 3) + "...";
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private String findContainer(WebElement element) {
        String script = """
            let el = arguments[0];
            while (el.parentElement) {
                el = el.parentElement;
                if (el.tagName === 'FORM' || el.tagName === 'DIALOG' ||
                    el.tagName === 'SECTION' || el.tagName === 'NAV' ||
                    el.getAttribute('role') === 'dialog' ||
                    el.getAttribute('role') === 'form') {
                    return el.tagName + (el.id ? '#' + el.id : '') +
                           (el.className ? '.' + el.className.split(' ')[0] : '');
                }
            }
            return 'body';
            """;
        try {
            return (String) ((JavascriptExecutor) driver).executeScript(script, element);
        } catch (WebDriverException e) {
            return "body";
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> findNearbyLabels(WebElement element) {
        String script = """
            const el = arguments[0];
            const labels = [];

            // Check for associated label
            if (el.id) {
                const label = document.querySelector(`label[for="${el.id}"]`);
                if (label) labels.push(label.textContent.trim());
            }

            // Check for wrapping label
            const parentLabel = el.closest('label');
            if (parentLabel) labels.push(parentLabel.textContent.trim());

            // Check aria-labelledby
            const labelledBy = el.getAttribute('aria-labelledby');
            if (labelledBy) {
                labelledBy.split(' ').forEach(id => {
                    const labelEl = document.getElementById(id);
                    if (labelEl) labels.push(labelEl.textContent.trim());
                });
            }

            // Check nearby text in same container
            const container = el.closest('div, fieldset, section') || el.parentElement;
            if (container) {
                const nearbyText = container.querySelector('h1, h2, h3, h4, legend, p');
                if (nearbyText) labels.push(nearbyText.textContent.trim());
            }

            return [...new Set(labels)].slice(0, 5);
            """;
        try {
            List<String> labels = (List<String>) ((JavascriptExecutor) driver).executeScript(script, element);
            return labels != null ? labels : List.of();
        } catch (WebDriverException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> captureDataAttributes(WebElement element) {
        String script = """
            const el = arguments[0];
            const dataAttrs = {};
            for (const attr of el.attributes) {
                if (attr.name.startsWith('data-')) {
                    const key = attr.name.substring(5);
                    dataAttrs[key] = attr.value;
                }
            }
            return dataAttrs;
            """;
        try {
            Map<String, String> attrs = (Map<String, String>)
                    ((JavascriptExecutor) driver).executeScript(script, element);
            return attrs != null ? attrs : Map.of();
        } catch (WebDriverException e) {
            return Map.of();
        }
    }

    private String detectLanguage() {
        try {
            String lang = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.documentElement.lang || document.body.lang || 'en'");
            return lang != null ? lang : "en";
        } catch (WebDriverException e) {
            return "en";
        }
    }

    private String captureScreenshot() {
        try {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
        } catch (WebDriverException e) {
            logger.warn("Failed to capture screenshot: {}", e.getMessage());
            return null;
        }
    }

    private String captureDom() {
        try {
            return (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.documentElement.outerHTML");
        } catch (WebDriverException e) {
            logger.warn("Failed to capture DOM: {}", e.getMessage());
            return null;
        }
    }
}
