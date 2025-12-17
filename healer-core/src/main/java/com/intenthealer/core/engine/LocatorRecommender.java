package com.intenthealer.core.engine;

import com.intenthealer.core.model.LocatorInfo;
import com.intenthealer.core.model.LocatorInfo.LocatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes locators and provides recommendations for more stable alternatives.
 * Helps identify brittle locators that are prone to breaking and suggests
 * best practices for element selection.
 */
public class LocatorRecommender {

    private static final Logger logger = LoggerFactory.getLogger(LocatorRecommender.class);

    // Patterns for detecting brittle locators
    private static final Pattern POSITIONAL_XPATH = Pattern.compile("\\[\\d+\\]");
    private static final Pattern LONG_XPATH = Pattern.compile("(/[^/]+){5,}");
    private static final Pattern INDEX_BASED_CSS = Pattern.compile(":nth-child\\(\\d+\\)|:nth-of-type\\(\\d+\\)");
    private static final Pattern DYNAMIC_CLASS = Pattern.compile("\\.[a-zA-Z]+[_-][a-zA-Z]*[_-]?[0-9a-f]{4,}|\\.[a-zA-Z]*[_-]?[0-9a-f]{6,}");
    private static final Pattern AUTO_GENERATED_ID = Pattern.compile("\\b(react|angular|vue|ember|svelte)[_-]?\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_BASED = Pattern.compile("contains\\(text\\(\\)|normalize-space\\(");

    // Patterns for detecting stable locators
    private static final Pattern DATA_TESTID = Pattern.compile("\\[data-testid[=~|^$*]?=['\"]?[^'\"\\]]+['\"]?\\]|@data-testid");
    private static final Pattern DATA_CY = Pattern.compile("\\[data-cy[=~|^$*]?=['\"]?[^'\"\\]]+['\"]?\\]");
    private static final Pattern ARIA_LABEL = Pattern.compile("\\[aria-label[=~|^$*]?=['\"]?[^'\"\\]]+['\"]?\\]|@aria-label");
    private static final Pattern ROLE_SELECTOR = Pattern.compile("\\[role=['\"]?[^'\"\\]]+['\"]?\\]|@role");
    private static final Pattern NAME_SELECTOR = Pattern.compile("\\[name=['\"]?[^'\"\\]]+['\"]?\\]|@name");

    /**
     * Recommendation severity level.
     */
    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    /**
     * A recommendation for improving a locator.
     */
    public record Recommendation(
            String locator,
            LocatorStrategy strategy,
            Severity severity,
            String issue,
            String suggestion,
            String exampleFix
    ) {
        public boolean isCritical() {
            return severity == Severity.CRITICAL;
        }

        public boolean isWarning() {
            return severity == Severity.WARNING;
        }
    }

    /**
     * Summary of locator analysis for a set of heals.
     */
    public record LocatorAnalysis(
            int totalLocatorsAnalyzed,
            int stableLocators,
            int brittleLocators,
            int criticalIssues,
            Map<String, Integer> issuesByType,
            List<Recommendation> recommendations,
            List<String> topRecommendations
    ) {
        public double getStabilityScore() {
            if (totalLocatorsAnalyzed == 0) return 100.0;
            return (stableLocators * 100.0) / totalLocatorsAnalyzed;
        }

        public String getStabilityRating() {
            double score = getStabilityScore();
            if (score >= 90) return "Excellent";
            if (score >= 75) return "Good";
            if (score >= 50) return "Fair";
            return "Poor";
        }
    }

    /**
     * Analyzes a single locator and returns recommendations.
     */
    public List<Recommendation> analyzeLocator(String locator, LocatorStrategy strategy) {
        if (locator == null || locator.isEmpty()) {
            return Collections.emptyList();
        }

        List<Recommendation> recommendations = new ArrayList<>();

        switch (strategy) {
            case XPATH -> analyzeXPath(locator, recommendations);
            case CSS -> analyzeCss(locator, recommendations);
            case ID -> analyzeId(locator, recommendations);
            case CLASS_NAME -> analyzeClassName(locator, recommendations);
            case NAME -> analyzeName(locator, recommendations);
            case TAG_NAME -> analyzeTagName(locator, recommendations);
            case LINK_TEXT, PARTIAL_LINK_TEXT -> analyzeLinkText(locator, strategy, recommendations);
            default -> logger.debug("Unknown strategy: {}", strategy);
        }

        return recommendations;
    }

    /**
     * Analyzes a collection of healed locators and returns a summary.
     */
    public LocatorAnalysis analyzeHealedLocators(List<LocatorInfo> locators) {
        if (locators == null || locators.isEmpty()) {
            return new LocatorAnalysis(0, 0, 0, 0,
                    Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());
        }

        List<Recommendation> allRecommendations = new ArrayList<>();
        int stableCount = 0;
        int brittleCount = 0;
        Map<String, Integer> issuesByType = new HashMap<>();

        for (LocatorInfo locator : locators) {
            List<Recommendation> recs = analyzeLocator(locator.getValue(), locator.getStrategy());
            allRecommendations.addAll(recs);

            if (recs.isEmpty() || recs.stream().noneMatch(r -> r.severity() != Severity.INFO)) {
                stableCount++;
            } else {
                brittleCount++;
                for (Recommendation rec : recs) {
                    issuesByType.merge(rec.issue(), 1, Integer::sum);
                }
            }
        }

        int criticalIssues = (int) allRecommendations.stream()
                .filter(Recommendation::isCritical)
                .count();

        // Generate top recommendations
        List<String> topRecommendations = generateTopRecommendations(issuesByType, locators.size());

        return new LocatorAnalysis(
                locators.size(),
                stableCount,
                brittleCount,
                criticalIssues,
                issuesByType,
                allRecommendations,
                topRecommendations
        );
    }

    /**
     * Suggests a better locator based on element attributes.
     */
    public Optional<String> suggestBetterLocator(String currentLocator, Map<String, String> elementAttributes) {
        if (elementAttributes == null || elementAttributes.isEmpty()) {
            return Optional.empty();
        }

        // Priority order: data-testid > data-cy > aria-label > name > id > role
        String[] priorityAttributes = {"data-testid", "data-cy", "data-test", "aria-label", "name", "id", "role"};

        for (String attr : priorityAttributes) {
            String value = elementAttributes.get(attr);
            if (value != null && !value.isEmpty() && !isAutoGeneratedValue(value)) {
                if (attr.equals("id") && !isAutoGeneratedValue(value)) {
                    return Optional.of("[id='" + escapeForCss(value) + "']");
                } else if (attr.equals("role")) {
                    String name = elementAttributes.get("aria-label");
                    if (name != null && !name.isEmpty()) {
                        return Optional.of("[role='" + value + "'][aria-label='" + escapeForCss(name) + "']");
                    }
                    return Optional.of("[role='" + value + "']");
                } else {
                    return Optional.of("[" + attr + "='" + escapeForCss(value) + "']");
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if a locator is considered stable.
     */
    public boolean isStableLocator(String locator, LocatorStrategy strategy) {
        if (locator == null || locator.isEmpty()) {
            return false;
        }

        // Check for stable patterns
        if (DATA_TESTID.matcher(locator).find() ||
                DATA_CY.matcher(locator).find() ||
                ARIA_LABEL.matcher(locator).find() ||
                NAME_SELECTOR.matcher(locator).find()) {
            return true;
        }

        // Simple ID selectors are usually stable (unless auto-generated)
        if (strategy == LocatorStrategy.ID && !isAutoGeneratedValue(locator)) {
            return true;
        }

        // Short, simple CSS selectors are often stable
        if (strategy == LocatorStrategy.CSS) {
            return !INDEX_BASED_CSS.matcher(locator).find() &&
                    !DYNAMIC_CLASS.matcher(locator).find() &&
                    locator.split("\\s+").length <= 2;
        }

        return false;
    }

    private void analyzeXPath(String xpath, List<Recommendation> recommendations) {
        // Check for positional predicates
        if (POSITIONAL_XPATH.matcher(xpath).find()) {
            recommendations.add(new Recommendation(
                    xpath,
                    LocatorStrategy.XPATH,
                    Severity.CRITICAL,
                    "Positional index in XPath",
                    "Positional indices like [1], [2] are fragile. Use attributes or data-testid instead.",
                    "//button[@data-testid='submit-btn']"
            ));
        }

        // Check for long XPath
        if (LONG_XPATH.matcher(xpath).find()) {
            recommendations.add(new Recommendation(
                    xpath,
                    LocatorStrategy.XPATH,
                    Severity.WARNING,
                    "Long XPath path",
                    "Deep XPath expressions break easily when DOM structure changes. Consider using a shorter, more direct selector.",
                    "//div[@data-testid='container']//button"
            ));
        }

        // Check for text-based selection
        if (TEXT_BASED.matcher(xpath).find()) {
            recommendations.add(new Recommendation(
                    xpath,
                    LocatorStrategy.XPATH,
                    Severity.WARNING,
                    "Text-based selection",
                    "Text content can change with internationalization or updates. Prefer data-testid attributes.",
                    "//button[@data-testid='login-btn']"
            ));
        }

        // Check for absolute XPath
        if (xpath.startsWith("/html")) {
            recommendations.add(new Recommendation(
                    xpath,
                    LocatorStrategy.XPATH,
                    Severity.CRITICAL,
                    "Absolute XPath",
                    "Absolute XPath from /html is extremely brittle. Use relative XPath with stable attributes.",
                    "//form[@id='login']//input[@name='username']"
            ));
        }

        // Recommend stable patterns if not present
        if (!DATA_TESTID.matcher(xpath).find() && !ARIA_LABEL.matcher(xpath).find()) {
            recommendations.add(new Recommendation(
                    xpath,
                    LocatorStrategy.XPATH,
                    Severity.INFO,
                    "No test ID attribute",
                    "Consider adding data-testid attributes to elements for more reliable selection.",
                    "//*[@data-testid='element-name']"
            ));
        }
    }

    private void analyzeCss(String css, List<Recommendation> recommendations) {
        // Check for index-based selectors
        if (INDEX_BASED_CSS.matcher(css).find()) {
            recommendations.add(new Recommendation(
                    css,
                    LocatorStrategy.CSS,
                    Severity.WARNING,
                    "Index-based selector",
                    ":nth-child and :nth-of-type selectors break when DOM order changes. Use unique attributes.",
                    "[data-testid='item-1']"
            ));
        }

        // Check for dynamic classes
        if (DYNAMIC_CLASS.matcher(css).find()) {
            recommendations.add(new Recommendation(
                    css,
                    LocatorStrategy.CSS,
                    Severity.CRITICAL,
                    "Dynamic/hashed class name",
                    "Classes with hashes (e.g., _abc123) are generated by build tools and change between builds.",
                    "[data-testid='component-name']"
            ));
        }

        // Check for overly complex selectors
        String[] parts = css.split("\\s+");
        if (parts.length > 4) {
            recommendations.add(new Recommendation(
                    css,
                    LocatorStrategy.CSS,
                    Severity.WARNING,
                    "Complex selector chain",
                    "Long selector chains are fragile. Simplify by targeting the element directly.",
                    ".container [data-testid='target']"
            ));
        }

        // Recommend stable patterns
        if (!DATA_TESTID.matcher(css).find() && !DATA_CY.matcher(css).find()) {
            recommendations.add(new Recommendation(
                    css,
                    LocatorStrategy.CSS,
                    Severity.INFO,
                    "No test ID attribute",
                    "Consider using [data-testid] or [data-cy] attributes for stable element selection.",
                    "[data-testid='element-name']"
            ));
        }
    }

    private void analyzeId(String id, List<Recommendation> recommendations) {
        if (isAutoGeneratedValue(id)) {
            recommendations.add(new Recommendation(
                    id,
                    LocatorStrategy.ID,
                    Severity.CRITICAL,
                    "Auto-generated ID",
                    "IDs containing framework prefixes or UUIDs are unstable. Use data-testid instead.",
                    "[data-testid='element-name']"
            ));
        }

        // Check for IDs that look like UUIDs or hashes
        if (id.matches(".*[0-9a-f]{8,}.*") || id.matches(".*\\d{5,}.*")) {
            recommendations.add(new Recommendation(
                    id,
                    LocatorStrategy.ID,
                    Severity.WARNING,
                    "ID may be dynamic",
                    "IDs with long numbers or hex strings may be dynamically generated.",
                    "[data-testid='stable-name']"
            ));
        }
    }

    private void analyzeClassName(String className, List<Recommendation> recommendations) {
        if (DYNAMIC_CLASS.matcher("." + className).find()) {
            recommendations.add(new Recommendation(
                    className,
                    LocatorStrategy.CLASS_NAME,
                    Severity.CRITICAL,
                    "Dynamic class name",
                    "Class names with hashes or numbers are often generated by CSS-in-JS libraries.",
                    "[data-testid='element-name']"
            ));
        }

        recommendations.add(new Recommendation(
                className,
                LocatorStrategy.CLASS_NAME,
                Severity.INFO,
                "Class-based selection",
                "Class names may change during refactoring. Consider using data-testid for test-specific selection.",
                "[data-testid='element-name']"
        ));
    }

    private void analyzeName(String name, List<Recommendation> recommendations) {
        // Name attributes are generally stable but can change
        if (name.contains("_") && name.matches(".*\\d+.*")) {
            recommendations.add(new Recommendation(
                    name,
                    LocatorStrategy.NAME,
                    Severity.INFO,
                    "Name may be auto-generated",
                    "Names with numbers and underscores might be generated. Verify they are intentional.",
                    "[data-testid='form-field']"
            ));
        }
    }

    private void analyzeTagName(String tagName, List<Recommendation> recommendations) {
        recommendations.add(new Recommendation(
                tagName,
                LocatorStrategy.TAG_NAME,
                Severity.CRITICAL,
                "Tag name selection",
                "Selecting by tag name alone is extremely brittle - multiple elements will match.",
                "[data-testid='specific-element']"
        ));
    }

    private void analyzeLinkText(String text, LocatorStrategy strategy, List<Recommendation> recommendations) {
        recommendations.add(new Recommendation(
                text,
                strategy,
                Severity.WARNING,
                "Text-based link selection",
                "Link text can change with internationalization or content updates. Use data-testid if possible.",
                "a[data-testid='nav-link']"
        ));
    }

    private boolean isAutoGeneratedValue(String value) {
        if (value == null) return false;
        return AUTO_GENERATED_ID.matcher(value).find() ||
                value.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*") || // UUID
                value.matches(".*:[r]\\d+:.*"); // React key pattern
    }

    private String escapeForCss(String value) {
        return value.replace("'", "\\'").replace("\"", "\\\"");
    }

    private List<String> generateTopRecommendations(Map<String, Integer> issuesByType, int totalLocators) {
        List<String> recommendations = new ArrayList<>();

        if (issuesByType.containsKey("Positional index in XPath") ||
                issuesByType.containsKey("Index-based selector")) {
            recommendations.add("Replace positional selectors ([1], :nth-child) with data-testid attributes");
        }

        if (issuesByType.containsKey("Dynamic/hashed class name") ||
                issuesByType.containsKey("Auto-generated ID")) {
            recommendations.add("Add data-testid attributes to elements with generated IDs/classes");
        }

        if (issuesByType.containsKey("Long XPath path") ||
                issuesByType.containsKey("Complex selector chain")) {
            recommendations.add("Simplify deep selectors by adding unique attributes closer to target elements");
        }

        if (issuesByType.containsKey("Text-based selection") ||
                issuesByType.containsKey("Text-based link selection")) {
            recommendations.add("Replace text-based selectors with attribute selectors for i18n compatibility");
        }

        if (issuesByType.containsKey("Absolute XPath")) {
            recommendations.add("Convert absolute XPaths to relative XPaths using unique element attributes");
        }

        int noTestIdCount = issuesByType.getOrDefault("No test ID attribute", 0);
        if (noTestIdCount > totalLocators / 2) {
            recommendations.add("Consider implementing a data-testid strategy across your application");
        }

        return recommendations;
    }

    /**
     * Formats recommendations as a human-readable string.
     */
    public String formatRecommendationsAsText(LocatorAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Locator Stability Analysis ===\n\n");
        sb.append(String.format("Total Locators Analyzed: %d%n", analysis.totalLocatorsAnalyzed()));
        sb.append(String.format("Stable Locators: %d (%.1f%%)%n",
                analysis.stableLocators(), analysis.getStabilityScore()));
        sb.append(String.format("Brittle Locators: %d%n", analysis.brittleLocators()));
        sb.append(String.format("Critical Issues: %d%n", analysis.criticalIssues()));
        sb.append(String.format("Stability Rating: %s%n%n", analysis.getStabilityRating()));

        if (!analysis.issuesByType().isEmpty()) {
            sb.append("Issues by Type:\n");
            analysis.issuesByType().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("  - %s: %d%n", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        if (!analysis.topRecommendations().isEmpty()) {
            sb.append("Top Recommendations:\n");
            for (int i = 0; i < analysis.topRecommendations().size(); i++) {
                sb.append(String.format("  %d. %s%n", i + 1, analysis.topRecommendations().get(i)));
            }
        }

        return sb.toString();
    }
}
