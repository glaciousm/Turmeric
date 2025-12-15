package com.intenthealer.core.engine.healing;

import com.intenthealer.core.model.ElementCandidate;
import com.intenthealer.core.model.HealingRequest;
import com.intenthealer.core.model.UiSnapshot;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Specialized healer for input fields (text inputs, textareas, etc.).
 * Uses semantic matching based on labels, placeholders, and field context.
 */
public class InputFieldHealer {

    private static final Logger logger = LoggerFactory.getLogger(InputFieldHealer.class);

    private static final Set<String> INPUT_TYPES = Set.of(
            "text", "email", "password", "search", "tel", "url", "number"
    );

    private static final Pattern LABEL_FOR_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*$");

    /**
     * Find input field candidates based on semantic matching.
     */
    public List<ElementCandidate> findInputCandidates(
            WebDriver driver,
            HealingRequest request,
            UiSnapshot snapshot) {

        List<ElementCandidate> candidates = new ArrayList<>();
        String intentText = request.getIntentDescription();

        // Extract keywords from intent
        Set<String> keywords = extractKeywords(intentText);

        // Find all input elements
        List<WebElement> inputs = findAllInputElements(driver);

        for (WebElement input : inputs) {
            try {
                double score = calculateInputScore(driver, input, keywords, intentText);
                if (score > 0.3) {
                    candidates.add(createCandidate(input, score));
                }
            } catch (Exception e) {
                logger.debug("Error scoring input element: {}", e.getMessage());
            }
        }

        // Sort by score descending
        candidates.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        return candidates.size() > 5 ? candidates.subList(0, 5) : candidates;
    }

    /**
     * Find all input elements on the page.
     */
    private List<WebElement> findAllInputElements(WebDriver driver) {
        List<WebElement> inputs = new ArrayList<>();

        // Text inputs
        for (WebElement el : driver.findElements(By.cssSelector("input"))) {
            String type = el.getAttribute("type");
            if (type == null || INPUT_TYPES.contains(type.toLowerCase())) {
                inputs.add(el);
            }
        }

        // Textareas
        inputs.addAll(driver.findElements(By.tagName("textarea")));

        // Contenteditable elements
        inputs.addAll(driver.findElements(By.cssSelector("[contenteditable='true']")));

        return inputs;
    }

    /**
     * Calculate a score for how well an input matches the intent.
     */
    private double calculateInputScore(
            WebDriver driver,
            WebElement input,
            Set<String> keywords,
            String intentText) {

        double score = 0.0;
        String intentLower = intentText.toLowerCase();

        // Check placeholder
        String placeholder = input.getAttribute("placeholder");
        if (placeholder != null && !placeholder.isEmpty()) {
            score += calculateTextSimilarity(placeholder.toLowerCase(), keywords, intentLower) * 0.3;
        }

        // Check associated label
        String labelText = findAssociatedLabel(driver, input);
        if (labelText != null && !labelText.isEmpty()) {
            score += calculateTextSimilarity(labelText.toLowerCase(), keywords, intentLower) * 0.4;
        }

        // Check aria-label
        String ariaLabel = input.getAttribute("aria-label");
        if (ariaLabel != null && !ariaLabel.isEmpty()) {
            score += calculateTextSimilarity(ariaLabel.toLowerCase(), keywords, intentLower) * 0.35;
        }

        // Check name attribute
        String name = input.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            score += calculateTextSimilarity(name.toLowerCase(), keywords, intentLower) * 0.2;
        }

        // Check id attribute
        String id = input.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            score += calculateTextSimilarity(id.toLowerCase(), keywords, intentLower) * 0.15;
        }

        // Check type relevance
        String type = input.getAttribute("type");
        if (type != null) {
            score += getTypeRelevanceBonus(type.toLowerCase(), intentLower);
        }

        // Check if visible and enabled
        if (input.isDisplayed() && input.isEnabled()) {
            score += 0.1;
        }

        // Normalize score
        return Math.min(1.0, score);
    }

    /**
     * Find the label associated with an input element.
     */
    private String findAssociatedLabel(WebDriver driver, WebElement input) {
        // Try explicit label via 'for' attribute
        String id = input.getAttribute("id");
        if (id != null && !id.isEmpty() && LABEL_FOR_PATTERN.matcher(id).matches()) {
            List<WebElement> labels = driver.findElements(By.cssSelector("label[for='" + id + "']"));
            if (!labels.isEmpty()) {
                return labels.get(0).getText();
            }
        }

        // Try wrapping label
        try {
            WebElement parent = input.findElement(By.xpath("./ancestor::label"));
            String labelText = parent.getText();
            // Remove the input value from label text if present
            String inputValue = input.getAttribute("value");
            if (inputValue != null && !inputValue.isEmpty()) {
                labelText = labelText.replace(inputValue, "").trim();
            }
            if (!labelText.isEmpty()) {
                return labelText;
            }
        } catch (Exception e) {
            // No wrapping label
        }

        // Try preceding label sibling
        try {
            WebElement precedingLabel = input.findElement(
                    By.xpath("./preceding-sibling::label[1] | ./parent::*/preceding-sibling::label[1]")
            );
            return precedingLabel.getText();
        } catch (Exception e) {
            // No preceding label
        }

        // Try aria-labelledby
        String labelledBy = input.getAttribute("aria-labelledby");
        if (labelledBy != null && !labelledBy.isEmpty()) {
            try {
                WebElement labelElement = driver.findElement(By.id(labelledBy));
                return labelElement.getText();
            } catch (Exception e) {
                // Label element not found
            }
        }

        return null;
    }

    /**
     * Calculate text similarity based on keyword matching.
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

        double keywordScore = keywords.isEmpty() ? 0 : (double) matchCount / keywords.size();

        // Also check for direct substring matches
        double substringScore = 0;
        if (intent.contains(text) || text.contains(intent)) {
            substringScore = 0.5;
        }

        return Math.max(keywordScore, substringScore);
    }

    /**
     * Get bonus score based on input type relevance to intent.
     */
    private double getTypeRelevanceBonus(String type, String intent) {
        Map<String, Set<String>> typeKeywords = Map.of(
                "email", Set.of("email", "mail", "e-mail"),
                "password", Set.of("password", "pwd", "secret"),
                "tel", Set.of("phone", "telephone", "mobile", "cell"),
                "url", Set.of("url", "website", "link", "address"),
                "number", Set.of("number", "amount", "quantity", "count"),
                "search", Set.of("search", "find", "query")
        );

        Set<String> keywords = typeKeywords.get(type);
        if (keywords != null) {
            for (String keyword : keywords) {
                if (intent.contains(keyword)) {
                    return 0.15;
                }
            }
        }

        return 0;
    }

    /**
     * Extract meaningful keywords from intent text.
     */
    private Set<String> extractKeywords(String text) {
        Set<String> stopWords = Set.of(
                "a", "an", "the", "in", "on", "at", "to", "for", "of", "with",
                "enter", "type", "input", "field", "fill", "click", "i", "user"
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
     * Create an ElementCandidate from a WebElement.
     */
    private ElementCandidate createCandidate(WebElement element, double score) {
        String id = element.getAttribute("id");
        String name = element.getAttribute("name");
        String cssSelector = buildCssSelector(element);

        String locator;
        if (id != null && !id.isEmpty()) {
            locator = "#" + id;
        } else if (name != null && !name.isEmpty()) {
            locator = "[name='" + name + "']";
        } else {
            locator = cssSelector;
        }

        return ElementCandidate.builder()
                .locator(locator)
                .confidence(score)
                .explanation("Input field matched by semantic analysis")
                .tagName(element.getTagName())
                .attributes(extractAttributes(element))
                .build();
    }

    /**
     * Build a CSS selector for an element.
     */
    private String buildCssSelector(WebElement element) {
        String tag = element.getTagName();
        String id = element.getAttribute("id");
        String classes = element.getAttribute("class");
        String type = element.getAttribute("type");

        StringBuilder selector = new StringBuilder(tag);

        if (id != null && !id.isEmpty()) {
            selector.append("#").append(id);
        } else {
            if (type != null && !type.isEmpty()) {
                selector.append("[type='").append(type).append("']");
            }
            if (classes != null && !classes.isEmpty()) {
                String firstClass = classes.split("\\s+")[0];
                selector.append(".").append(firstClass);
            }
        }

        return selector.toString();
    }

    /**
     * Extract relevant attributes from an element.
     */
    private Map<String, String> extractAttributes(WebElement element) {
        Map<String, String> attrs = new HashMap<>();

        String[] attrNames = {"id", "name", "type", "placeholder", "aria-label", "class"};
        for (String attr : attrNames) {
            String value = element.getAttribute(attr);
            if (value != null && !value.isEmpty()) {
                attrs.put(attr, value);
            }
        }

        return attrs;
    }
}
