package com.intenthealer.core.engine.healing;

import com.intenthealer.core.model.ElementCandidate;
import com.intenthealer.core.model.HealingRequest;
import com.intenthealer.core.model.UiSnapshot;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Specialized healer for select/dropdown elements.
 * Handles native HTML selects and custom dropdown implementations.
 */
public class SelectFieldHealer {

    private static final Logger logger = LoggerFactory.getLogger(SelectFieldHealer.class);

    /**
     * Find select/dropdown candidates based on semantic matching.
     */
    public List<ElementCandidate> findSelectCandidates(
            WebDriver driver,
            HealingRequest request,
            UiSnapshot snapshot) {

        List<ElementCandidate> candidates = new ArrayList<>();
        String intentText = request.getIntentDescription();
        Set<String> keywords = extractKeywords(intentText);

        // Find native select elements
        List<WebElement> selects = driver.findElements(By.tagName("select"));
        for (WebElement select : selects) {
            try {
                double score = calculateSelectScore(driver, select, keywords, intentText);
                if (score > 0.3) {
                    candidates.add(createSelectCandidate(select, score));
                }
            } catch (Exception e) {
                logger.debug("Error scoring select element: {}", e.getMessage());
            }
        }

        // Find custom dropdown implementations
        candidates.addAll(findCustomDropdowns(driver, keywords, intentText));

        // Sort by score descending
        candidates.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        return candidates.size() > 5 ? candidates.subList(0, 5) : candidates;
    }

    /**
     * Find option within a select that matches intent.
     */
    public Optional<WebElement> findMatchingOption(
            WebDriver driver,
            WebElement selectElement,
            String targetValue) {

        try {
            Select select = new Select(selectElement);
            List<WebElement> options = select.getOptions();

            // Try exact match first
            for (WebElement option : options) {
                String text = option.getText().trim();
                String value = option.getAttribute("value");

                if (text.equalsIgnoreCase(targetValue) ||
                        (value != null && value.equalsIgnoreCase(targetValue))) {
                    return Optional.of(option);
                }
            }

            // Try partial match
            String targetLower = targetValue.toLowerCase();
            for (WebElement option : options) {
                String text = option.getText().toLowerCase().trim();
                if (text.contains(targetLower) || targetLower.contains(text)) {
                    return Optional.of(option);
                }
            }

            // Try fuzzy match
            WebElement bestMatch = null;
            double bestScore = 0;

            for (WebElement option : options) {
                String text = option.getText().trim();
                double score = calculateFuzzyScore(text, targetValue);
                if (score > bestScore && score > 0.5) {
                    bestScore = score;
                    bestMatch = option;
                }
            }

            return Optional.ofNullable(bestMatch);

        } catch (Exception e) {
            logger.debug("Error finding matching option: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Calculate score for a select element.
     */
    private double calculateSelectScore(
            WebDriver driver,
            WebElement select,
            Set<String> keywords,
            String intentText) {

        double score = 0.0;
        String intentLower = intentText.toLowerCase();

        // Check associated label
        String labelText = findAssociatedLabel(driver, select);
        if (labelText != null && !labelText.isEmpty()) {
            score += calculateTextSimilarity(labelText.toLowerCase(), keywords, intentLower) * 0.4;
        }

        // Check aria-label
        String ariaLabel = select.getAttribute("aria-label");
        if (ariaLabel != null && !ariaLabel.isEmpty()) {
            score += calculateTextSimilarity(ariaLabel.toLowerCase(), keywords, intentLower) * 0.35;
        }

        // Check name attribute
        String name = select.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            score += calculateTextSimilarity(name.toLowerCase(), keywords, intentLower) * 0.2;
        }

        // Check id attribute
        String id = select.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            score += calculateTextSimilarity(id.toLowerCase(), keywords, intentLower) * 0.15;
        }

        // Check options for relevance
        try {
            Select selectObj = new Select(select);
            List<WebElement> options = selectObj.getOptions();
            for (WebElement option : options) {
                String optionText = option.getText().toLowerCase();
                if (hasKeywordMatch(optionText, keywords)) {
                    score += 0.15;
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore option parsing errors
        }

        // Visibility bonus
        if (select.isDisplayed() && select.isEnabled()) {
            score += 0.1;
        }

        return Math.min(1.0, score);
    }

    /**
     * Find custom dropdown implementations (div-based, listbox, etc.).
     */
    private List<ElementCandidate> findCustomDropdowns(
            WebDriver driver,
            Set<String> keywords,
            String intentText) {

        List<ElementCandidate> candidates = new ArrayList<>();
        String intentLower = intentText.toLowerCase();

        // Role-based dropdowns
        List<WebElement> listboxes = driver.findElements(By.cssSelector("[role='listbox'], [role='combobox']"));
        for (WebElement element : listboxes) {
            try {
                double score = calculateCustomDropdownScore(driver, element, keywords, intentLower);
                if (score > 0.3) {
                    candidates.add(createCustomDropdownCandidate(element, score));
                }
            } catch (Exception e) {
                logger.debug("Error scoring custom dropdown: {}", e.getMessage());
            }
        }

        // Common dropdown class patterns
        String[] dropdownPatterns = {
                "[class*='dropdown']",
                "[class*='select']",
                "[class*='combo']",
                "[data-testid*='select']",
                "[data-testid*='dropdown']"
        };

        for (String pattern : dropdownPatterns) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(pattern));
                for (WebElement element : elements) {
                    if (!isNativeSelect(element)) {
                        double score = calculateCustomDropdownScore(driver, element, keywords, intentLower);
                        if (score > 0.3) {
                            candidates.add(createCustomDropdownCandidate(element, score));
                        }
                    }
                }
            } catch (Exception e) {
                // Pattern may not match any elements
            }
        }

        return candidates;
    }

    /**
     * Calculate score for custom dropdown element.
     */
    private double calculateCustomDropdownScore(
            WebDriver driver,
            WebElement element,
            Set<String> keywords,
            String intentLower) {

        double score = 0.0;

        // Check aria attributes
        String ariaLabel = element.getAttribute("aria-label");
        if (ariaLabel != null && !ariaLabel.isEmpty()) {
            score += calculateTextSimilarity(ariaLabel.toLowerCase(), keywords, intentLower) * 0.35;
        }

        // Check text content
        String text = element.getText();
        if (text != null && !text.isEmpty()) {
            score += calculateTextSimilarity(text.toLowerCase(), keywords, intentLower) * 0.25;
        }

        // Check data attributes
        String dataTestId = element.getAttribute("data-testid");
        if (dataTestId != null && !dataTestId.isEmpty()) {
            score += calculateTextSimilarity(dataTestId.toLowerCase(), keywords, intentLower) * 0.2;
        }

        // Check id
        String id = element.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            score += calculateTextSimilarity(id.toLowerCase(), keywords, intentLower) * 0.15;
        }

        // Role bonus
        String role = element.getAttribute("role");
        if ("listbox".equals(role) || "combobox".equals(role)) {
            score += 0.1;
        }

        // Visibility bonus
        if (element.isDisplayed()) {
            score += 0.05;
        }

        return Math.min(1.0, score);
    }

    /**
     * Find the label associated with an element.
     */
    private String findAssociatedLabel(WebDriver driver, WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            try {
                List<WebElement> labels = driver.findElements(By.cssSelector("label[for='" + id + "']"));
                if (!labels.isEmpty()) {
                    return labels.get(0).getText();
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        // Try wrapping label
        try {
            WebElement parent = element.findElement(By.xpath("./ancestor::label"));
            return parent.getText();
        } catch (Exception e) {
            // No wrapping label
        }

        // Try aria-labelledby
        String labelledBy = element.getAttribute("aria-labelledby");
        if (labelledBy != null && !labelledBy.isEmpty()) {
            try {
                WebElement labelElement = driver.findElement(By.id(labelledBy));
                return labelElement.getText();
            } catch (Exception e) {
                // Label not found
            }
        }

        return null;
    }

    /**
     * Check if element is a native select.
     */
    private boolean isNativeSelect(WebElement element) {
        return "select".equalsIgnoreCase(element.getTagName());
    }

    /**
     * Calculate text similarity.
     */
    private double calculateTextSimilarity(String text, Set<String> keywords, String intent) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        int matchCount = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                matchCount++;
            }
        }

        return keywords.isEmpty() ? 0 : (double) matchCount / keywords.size();
    }

    /**
     * Check if text contains any keyword.
     */
    private boolean hasKeywordMatch(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate fuzzy match score between two strings.
     */
    private double calculateFuzzyScore(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        s1 = s1.toLowerCase().trim();
        s2 = s2.toLowerCase().trim();

        if (s1.equals(s2)) {
            return 1.0;
        }

        // Levenshtein distance based score
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Calculate Levenshtein distance.
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Extract keywords from text.
     */
    private Set<String> extractKeywords(String text) {
        Set<String> stopWords = Set.of(
                "a", "an", "the", "in", "on", "at", "to", "for", "of", "with",
                "select", "choose", "pick", "dropdown", "from", "i", "user"
        );

        Set<String> keywords = new HashSet<>();
        String[] words = text.toLowerCase().split("\\W+");

        for (String word : words) {
            if (word.length() > 2 && !stopWords.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * Create candidate for native select.
     */
    private ElementCandidate createSelectCandidate(WebElement element, double score) {
        String id = element.getAttribute("id");
        String name = element.getAttribute("name");

        String locator;
        if (id != null && !id.isEmpty()) {
            locator = "#" + id;
        } else if (name != null && !name.isEmpty()) {
            locator = "select[name='" + name + "']";
        } else {
            locator = buildCssSelector(element);
        }

        return ElementCandidate.builder()
                .locator(locator)
                .confidence(score)
                .explanation("Select element matched by semantic analysis")
                .tagName("select")
                .attributes(extractAttributes(element))
                .build();
    }

    /**
     * Create candidate for custom dropdown.
     */
    private ElementCandidate createCustomDropdownCandidate(WebElement element, double score) {
        String id = element.getAttribute("id");
        String dataTestId = element.getAttribute("data-testid");

        String locator;
        if (id != null && !id.isEmpty()) {
            locator = "#" + id;
        } else if (dataTestId != null && !dataTestId.isEmpty()) {
            locator = "[data-testid='" + dataTestId + "']";
        } else {
            locator = buildCssSelector(element);
        }

        return ElementCandidate.builder()
                .locator(locator)
                .confidence(score)
                .explanation("Custom dropdown matched by semantic analysis")
                .tagName(element.getTagName())
                .attributes(extractAttributes(element))
                .build();
    }

    /**
     * Build CSS selector for element.
     */
    private String buildCssSelector(WebElement element) {
        String tag = element.getTagName();
        String id = element.getAttribute("id");
        String classes = element.getAttribute("class");

        StringBuilder selector = new StringBuilder(tag);

        if (id != null && !id.isEmpty()) {
            selector.append("#").append(id);
        } else if (classes != null && !classes.isEmpty()) {
            String firstClass = classes.split("\\s+")[0];
            selector.append(".").append(firstClass);
        }

        return selector.toString();
    }

    /**
     * Extract attributes from element.
     */
    private Map<String, String> extractAttributes(WebElement element) {
        Map<String, String> attrs = new HashMap<>();

        String[] attrNames = {"id", "name", "class", "aria-label", "role", "data-testid"};
        for (String attr : attrNames) {
            String value = element.getAttribute(attr);
            if (value != null && !value.isEmpty()) {
                attrs.put(attr, value);
            }
        }

        return attrs;
    }
}
