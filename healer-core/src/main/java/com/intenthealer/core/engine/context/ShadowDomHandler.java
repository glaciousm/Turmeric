package com.intenthealer.core.engine.context;

import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Handles Shadow DOM traversal for healing operations.
 * Enables finding and healing elements inside shadow roots.
 */
public class ShadowDomHandler {

    private static final Logger logger = LoggerFactory.getLogger(ShadowDomHandler.class);

    private final WebDriver driver;

    public ShadowDomHandler(WebDriver driver) {
        this.driver = driver;
    }

    /**
     * Find element across all shadow roots.
     */
    public Optional<ElementInShadow> findElementAcrossShadowRoots(By locator) {
        // Try in main document first
        try {
            WebElement element = driver.findElement(locator);
            return Optional.of(new ElementInShadow(element, List.of()));
        } catch (NoSuchElementException e) {
            // Not in main document
        }

        // Search in shadow roots
        return searchInShadowRoots(locator, (WebElement) null, new ArrayList<>());
    }

    /**
     * Find element using CSS selector across shadow roots.
     */
    public Optional<WebElement> findByCssAcrossShadowRoots(String cssSelector) {
        return findElementAcrossShadowRoots(By.cssSelector(cssSelector))
                .map(ElementInShadow::element);
    }

    /**
     * Find element using a shadow-piercing path.
     * Path format: "host1 >> host2 >> target"
     */
    public Optional<WebElement> findByShadowPath(String shadowPath) {
        String[] parts = shadowPath.split("\\s*>>\\s*");
        SearchContext currentContext = driver;

        for (int i = 0; i < parts.length; i++) {
            String selector = parts[i].trim();
            boolean isLast = (i == parts.length - 1);

            try {
                WebElement element = currentContext.findElement(By.cssSelector(selector));

                if (isLast) {
                    return Optional.of(element);
                }

                // Get shadow root for next iteration
                SearchContext shadowRoot = getShadowRoot(element);
                if (shadowRoot == null) {
                    logger.debug("No shadow root found for element at path segment: {}", selector);
                    return Optional.empty();
                }
                currentContext = shadowRoot;

            } catch (NoSuchElementException e) {
                logger.debug("Element not found at path segment: {}", selector);
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Get all shadow hosts on the page.
     */
    public List<ShadowHostInfo> getAllShadowHosts() {
        List<ShadowHostInfo> hosts = new ArrayList<>();
        collectShadowHosts(driver, hosts, new ArrayList<>(), 0);
        return hosts;
    }

    /**
     * Execute operation across all shadow roots.
     */
    public <T> Optional<T> executeAcrossShadowRoots(ShadowOperation<T> operation) {
        // Try in main document
        try {
            Optional<T> result = operation.execute(driver);
            if (result.isPresent()) {
                return result;
            }
        } catch (Exception e) {
            logger.debug("Operation failed in main document: {}", e.getMessage());
        }

        // Try in shadow roots
        return executeInShadowRootsRecursively(operation, driver, new ArrayList<>());
    }

    /**
     * Build a shadow-piercing locator for an element.
     */
    public Optional<String> buildShadowPiercingLocator(WebElement targetElement, List<WebElement> shadowPath) {
        if (shadowPath.isEmpty()) {
            return buildSimpleSelector(targetElement);
        }

        StringBuilder path = new StringBuilder();

        for (WebElement host : shadowPath) {
            String hostSelector = buildSimpleSelector(host).orElse(null);
            if (hostSelector == null) {
                return Optional.empty();
            }
            path.append(hostSelector).append(" >> ");
        }

        String targetSelector = buildSimpleSelector(targetElement).orElse(null);
        if (targetSelector == null) {
            return Optional.empty();
        }

        path.append(targetSelector);
        return Optional.of(path.toString());
    }

    /**
     * Check if element is inside a shadow root.
     */
    public boolean isInShadowRoot(WebElement element) {
        try {
            // Try to find the element from document root
            String id = element.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                try {
                    driver.findElement(By.id(id));
                    return false; // Found from root, not in shadow
                } catch (NoSuchElementException e) {
                    return true; // Not found from root, likely in shadow
                }
            }

            // Use JavaScript to check
            if (driver instanceof JavascriptExecutor js) {
                Object result = js.executeScript(
                        "return arguments[0].getRootNode() instanceof ShadowRoot;",
                        element
                );
                return Boolean.TRUE.equals(result);
            }

            return false;
        } catch (Exception e) {
            logger.debug("Error checking shadow root: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods

    private Optional<ElementInShadow> searchInShadowRoots(
            By locator,
            WebElement host,
            List<WebElement> currentPath) {

        SearchContext context = host != null ? getShadowRoot(host) : driver;
        if (context == null) {
            return Optional.empty();
        }

        // Find all elements with shadow roots
        List<WebElement> shadowHosts = findShadowHosts(context);

        for (WebElement shadowHost : shadowHosts) {
            SearchContext shadowRoot = getShadowRoot(shadowHost);
            if (shadowRoot == null) {
                continue;
            }

            List<WebElement> newPath = new ArrayList<>(currentPath);
            newPath.add(shadowHost);

            // Try to find element in this shadow root
            try {
                WebElement element = shadowRoot.findElement(locator);
                return Optional.of(new ElementInShadow(element, newPath));
            } catch (NoSuchElementException e) {
                // Not in this shadow root
            }

            // Search nested shadow roots
            Optional<ElementInShadow> nested = searchInShadowRootsFromContext(
                    locator, shadowRoot, newPath);
            if (nested.isPresent()) {
                return nested;
            }
        }

        return Optional.empty();
    }

    private Optional<ElementInShadow> searchInShadowRootsFromContext(
            By locator,
            SearchContext context,
            List<WebElement> currentPath) {

        List<WebElement> shadowHosts = findShadowHosts(context);

        for (WebElement shadowHost : shadowHosts) {
            SearchContext shadowRoot = getShadowRoot(shadowHost);
            if (shadowRoot == null) {
                continue;
            }

            List<WebElement> newPath = new ArrayList<>(currentPath);
            newPath.add(shadowHost);

            // Try to find in this shadow root
            try {
                WebElement element = shadowRoot.findElement(locator);
                return Optional.of(new ElementInShadow(element, newPath));
            } catch (NoSuchElementException e) {
                // Not here
            }

            // Recurse
            Optional<ElementInShadow> nested = searchInShadowRootsFromContext(
                    locator, shadowRoot, newPath);
            if (nested.isPresent()) {
                return nested;
            }
        }

        return Optional.empty();
    }

    private <T> Optional<T> executeInShadowRootsRecursively(
            ShadowOperation<T> operation,
            SearchContext context,
            List<WebElement> currentPath) {

        List<WebElement> shadowHosts = findShadowHosts(context);

        for (WebElement shadowHost : shadowHosts) {
            SearchContext shadowRoot = getShadowRoot(shadowHost);
            if (shadowRoot == null) {
                continue;
            }

            List<WebElement> newPath = new ArrayList<>(currentPath);
            newPath.add(shadowHost);

            // Try operation in this shadow root
            try {
                Optional<T> result = operation.execute(shadowRoot);
                if (result.isPresent()) {
                    return result;
                }
            } catch (Exception e) {
                logger.debug("Operation failed in shadow root: {}", e.getMessage());
            }

            // Try nested
            Optional<T> nested = executeInShadowRootsRecursively(operation, shadowRoot, newPath);
            if (nested.isPresent()) {
                return nested;
            }
        }

        return Optional.empty();
    }

    private List<WebElement> findShadowHosts(SearchContext context) {
        try {
            // Find elements that commonly have shadow roots
            List<WebElement> candidates = new ArrayList<>();

            // Custom elements (contain hyphens)
            if (context instanceof WebDriver wd) {
                Object result = ((JavascriptExecutor) wd).executeScript(
                        "return Array.from(document.querySelectorAll('*')).filter(e => e.shadowRoot);"
                );
                if (result instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof WebElement we) {
                            candidates.add(we);
                        }
                    }
                }
            } else if (context instanceof WebElement parent && driver instanceof JavascriptExecutor js) {
                Object result = js.executeScript(
                        "return Array.from(arguments[0].querySelectorAll('*')).filter(e => e.shadowRoot);",
                        parent
                );
                if (result instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof WebElement we) {
                            candidates.add(we);
                        }
                    }
                }
            }

            return candidates;
        } catch (Exception e) {
            logger.debug("Error finding shadow hosts: {}", e.getMessage());
            return List.of();
        }
    }

    private SearchContext getShadowRoot(WebElement element) {
        try {
            return element.getShadowRoot();
        } catch (NoSuchShadowRootException e) {
            return null;
        } catch (Exception e) {
            // Try JavaScript fallback
            if (driver instanceof JavascriptExecutor js) {
                try {
                    Object result = js.executeScript("return arguments[0].shadowRoot;", element);
                    if (result instanceof SearchContext sc) {
                        return sc;
                    }
                } catch (Exception ex) {
                    logger.debug("Could not get shadow root: {}", ex.getMessage());
                }
            }
            return null;
        }
    }

    private void collectShadowHosts(
            SearchContext context,
            List<ShadowHostInfo> result,
            List<WebElement> currentPath,
            int depth) {

        if (depth > 10) {
            // Prevent infinite recursion
            return;
        }

        List<WebElement> shadowHosts = findShadowHosts(context);

        for (WebElement host : shadowHosts) {
            List<WebElement> path = new ArrayList<>(currentPath);
            path.add(host);

            String tagName = host.getTagName();
            String id = host.getAttribute("id");
            String className = host.getAttribute("class");

            result.add(new ShadowHostInfo(
                    tagName,
                    id,
                    className,
                    path,
                    depth
            ));

            // Recurse into shadow root
            SearchContext shadowRoot = getShadowRoot(host);
            if (shadowRoot != null) {
                collectShadowHosts(shadowRoot, result, path, depth + 1);
            }
        }
    }

    private Optional<String> buildSimpleSelector(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            return Optional.of("#" + id);
        }

        String className = element.getAttribute("class");
        String tagName = element.getTagName();

        if (className != null && !className.isEmpty()) {
            String firstClass = className.split("\\s+")[0];
            return Optional.of(tagName + "." + firstClass);
        }

        return Optional.of(tagName);
    }

    // Types

    /**
     * Result of finding element in shadow DOM.
     */
    public record ElementInShadow(
            WebElement element,
            List<WebElement> shadowPath
    ) {}

    /**
     * Information about a shadow host.
     */
    public record ShadowHostInfo(
            String tagName,
            String id,
            String className,
            List<WebElement> path,
            int depth
    ) {
        public String getSelector() {
            if (id != null && !id.isEmpty()) {
                return "#" + id;
            }
            if (className != null && !className.isEmpty()) {
                return tagName + "." + className.split("\\s+")[0];
            }
            return tagName;
        }
    }

    /**
     * Operation to execute in shadow context.
     */
    @FunctionalInterface
    public interface ShadowOperation<T> {
        Optional<T> execute(SearchContext context);
    }
}
