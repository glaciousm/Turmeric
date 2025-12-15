package com.intenthealer.core.engine.context;

import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Handles iframe detection and context switching for healing operations.
 * Enables healing of elements inside iframes.
 */
public class IframeHandler {

    private static final Logger logger = LoggerFactory.getLogger(IframeHandler.class);

    private final WebDriver driver;
    private final Deque<FrameContext> frameStack = new ArrayDeque<>();

    public IframeHandler(WebDriver driver) {
        this.driver = driver;
    }

    /**
     * Find element across all iframes.
     * Searches main document first, then recursively searches all iframes.
     */
    public Optional<ElementInFrame> findElementAcrossFrames(By locator) {
        // Save current frame context
        FrameContext originalContext = getCurrentFrameContext();

        try {
            // Switch to default content first
            driver.switchTo().defaultContent();

            // Try main document
            try {
                WebElement element = driver.findElement(locator);
                return Optional.of(new ElementInFrame(element, null, List.of()));
            } catch (NoSuchElementException e) {
                // Not in main document
            }

            // Search recursively in iframes
            return searchInIframes(locator, new ArrayList<>());

        } finally {
            // Restore original context
            restoreFrameContext(originalContext);
        }
    }

    /**
     * Execute healing operation in all frame contexts until element is found.
     */
    public <T> Optional<T> executeAcrossFrames(FrameOperation<T> operation) {
        FrameContext originalContext = getCurrentFrameContext();

        try {
            driver.switchTo().defaultContent();

            // Try main document
            try {
                Optional<T> result = operation.execute();
                if (result.isPresent()) {
                    return result;
                }
            } catch (Exception e) {
                logger.debug("Operation failed in main document: {}", e.getMessage());
            }

            // Try all iframes
            return executeInIframesRecursively(operation, new ArrayList<>());

        } finally {
            restoreFrameContext(originalContext);
        }
    }

    /**
     * Switch to frame containing the target element and return the element.
     */
    public Optional<WebElement> switchToFrameAndFind(By locator) {
        Optional<ElementInFrame> found = findElementAcrossFrames(locator);
        if (found.isPresent()) {
            ElementInFrame eif = found.get();

            // Switch to correct frame context
            driver.switchTo().defaultContent();
            for (Integer frameIndex : eif.framePath()) {
                driver.switchTo().frame(frameIndex);
            }

            return Optional.of(eif.element());
        }
        return Optional.empty();
    }

    /**
     * Get all iframes on the current page.
     */
    public List<IframeInfo> getAllIframes() {
        List<IframeInfo> iframes = new ArrayList<>();
        FrameContext originalContext = getCurrentFrameContext();

        try {
            driver.switchTo().defaultContent();
            collectIframes(iframes, new ArrayList<>());
        } finally {
            restoreFrameContext(originalContext);
        }

        return iframes;
    }

    /**
     * Switch to iframe by various identifiers.
     */
    public boolean switchToIframe(String identifier) {
        try {
            // Try by id
            try {
                WebElement iframe = driver.findElement(By.id(identifier));
                driver.switchTo().frame(iframe);
                return true;
            } catch (NoSuchElementException e) {
                // Not found by id
            }

            // Try by name
            try {
                WebElement iframe = driver.findElement(By.name(identifier));
                driver.switchTo().frame(iframe);
                return true;
            } catch (NoSuchElementException e) {
                // Not found by name
            }

            // Try by index
            try {
                int index = Integer.parseInt(identifier);
                driver.switchTo().frame(index);
                return true;
            } catch (NumberFormatException e) {
                // Not an index
            }

            // Try by CSS selector
            try {
                WebElement iframe = driver.findElement(By.cssSelector(identifier));
                if ("iframe".equalsIgnoreCase(iframe.getTagName())) {
                    driver.switchTo().frame(iframe);
                    return true;
                }
            } catch (NoSuchElementException e) {
                // Not found by selector
            }

            return false;
        } catch (Exception e) {
            logger.error("Error switching to iframe: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Switch back to default content.
     */
    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
        frameStack.clear();
    }

    /**
     * Switch to parent frame.
     */
    public void switchToParentFrame() {
        driver.switchTo().parentFrame();
        if (!frameStack.isEmpty()) {
            frameStack.pop();
        }
    }

    /**
     * Get current frame path for debugging.
     */
    public List<String> getCurrentFramePath() {
        return frameStack.stream()
                .map(FrameContext::identifier)
                .toList();
    }

    // Private helper methods

    private Optional<ElementInFrame> searchInIframes(By locator, List<Integer> currentPath) {
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
        iframes.addAll(driver.findElements(By.tagName("frame")));

        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().frame(i);
                List<Integer> newPath = new ArrayList<>(currentPath);
                newPath.add(i);

                // Try to find element in this frame
                try {
                    WebElement element = driver.findElement(locator);
                    return Optional.of(new ElementInFrame(element, iframes.get(i), newPath));
                } catch (NoSuchElementException e) {
                    // Not in this frame, search nested iframes
                    Optional<ElementInFrame> nested = searchInIframes(locator, newPath);
                    if (nested.isPresent()) {
                        return nested;
                    }
                }

                // Go back to parent
                driver.switchTo().parentFrame();

            } catch (Exception e) {
                logger.debug("Error searching iframe {}: {}", i, e.getMessage());
                try {
                    driver.switchTo().parentFrame();
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }

        return Optional.empty();
    }

    private <T> Optional<T> executeInIframesRecursively(FrameOperation<T> operation, List<Integer> currentPath) {
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
        iframes.addAll(driver.findElements(By.tagName("frame")));

        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().frame(i);
                List<Integer> newPath = new ArrayList<>(currentPath);
                newPath.add(i);

                // Try operation in this frame
                try {
                    Optional<T> result = operation.execute();
                    if (result.isPresent()) {
                        return result;
                    }
                } catch (Exception e) {
                    logger.debug("Operation failed in iframe {}: {}", newPath, e.getMessage());
                }

                // Try nested iframes
                Optional<T> nested = executeInIframesRecursively(operation, newPath);
                if (nested.isPresent()) {
                    return nested;
                }

                driver.switchTo().parentFrame();

            } catch (Exception e) {
                logger.debug("Error executing in iframe {}: {}", i, e.getMessage());
                try {
                    driver.switchTo().parentFrame();
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }

        return Optional.empty();
    }

    private void collectIframes(List<IframeInfo> result, List<Integer> currentPath) {
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
        iframes.addAll(driver.findElements(By.tagName("frame")));

        for (int i = 0; i < iframes.size(); i++) {
            WebElement iframe = iframes.get(i);
            List<Integer> path = new ArrayList<>(currentPath);
            path.add(i);

            String id = iframe.getAttribute("id");
            String name = iframe.getAttribute("name");
            String src = iframe.getAttribute("src");

            result.add(new IframeInfo(
                    id,
                    name,
                    src,
                    path,
                    iframe.isDisplayed()
            ));

            // Recurse into iframe
            try {
                driver.switchTo().frame(i);
                collectIframes(result, path);
                driver.switchTo().parentFrame();
            } catch (Exception e) {
                logger.debug("Could not enter iframe {}: {}", path, e.getMessage());
            }
        }
    }

    private FrameContext getCurrentFrameContext() {
        return new FrameContext(
                frameStack.isEmpty() ? "default" : frameStack.peek().identifier(),
                new ArrayList<>(frameStack)
        );
    }

    private void restoreFrameContext(FrameContext context) {
        try {
            driver.switchTo().defaultContent();
            frameStack.clear();

            for (FrameContext frame : context.stack()) {
                if (!"default".equals(frame.identifier())) {
                    switchToIframe(frame.identifier());
                    frameStack.push(frame);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not restore frame context: {}", e.getMessage());
        }
    }

    // Types

    /**
     * Result of finding an element in a frame.
     */
    public record ElementInFrame(
            WebElement element,
            WebElement iframe,
            List<Integer> framePath
    ) {}

    /**
     * Information about an iframe.
     */
    public record IframeInfo(
            String id,
            String name,
            String src,
            List<Integer> path,
            boolean visible
    ) {
        public String getIdentifier() {
            if (id != null && !id.isEmpty()) return id;
            if (name != null && !name.isEmpty()) return name;
            return "frame[" + String.join(",", path.stream().map(String::valueOf).toList()) + "]";
        }
    }

    /**
     * Frame context for tracking current position.
     */
    private record FrameContext(
            String identifier,
            List<FrameContext> stack
    ) {}

    /**
     * Operation to execute in frame context.
     */
    @FunctionalInterface
    public interface FrameOperation<T> {
        Optional<T> execute();
    }
}
