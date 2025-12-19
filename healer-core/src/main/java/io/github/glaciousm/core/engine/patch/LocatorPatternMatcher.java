package io.github.glaciousm.core.engine.patch;

import io.github.glaciousm.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches and replaces locator patterns in source code.
 * Supports both Selenium By.* methods and @FindBy annotations.
 */
public class LocatorPatternMatcher {

    private static final Logger logger = LoggerFactory.getLogger(LocatorPatternMatcher.class);

    // Patterns for By.* methods
    private static final Pattern BY_ID_PATTERN = Pattern.compile(
            "(By\\.id\\s*\\(\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern BY_NAME_PATTERN = Pattern.compile(
            "(By\\.name\\s*\\(\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern BY_CLASS_NAME_PATTERN = Pattern.compile(
            "(By\\.className\\s*\\(\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern BY_CSS_SELECTOR_PATTERN = Pattern.compile(
            "(By\\.cssSelector\\s*\\(\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern BY_XPATH_PATTERN = Pattern.compile(
            "(By\\.xpath\\s*\\(\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern BY_TAG_NAME_PATTERN = Pattern.compile(
            "(By\\.tagName\\s*\\(\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern BY_LINK_TEXT_PATTERN = Pattern.compile(
            "(By\\.linkText\\s*\\(\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern BY_PARTIAL_LINK_TEXT_PATTERN = Pattern.compile(
            "(By\\.partialLinkText\\s*\\(\\s*)\"([^\"]+)\"(\\s*\\))");

    // Patterns for @FindBy annotations
    private static final Pattern FINDBY_ID_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*id\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern FINDBY_NAME_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*name\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern FINDBY_CLASS_NAME_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*className\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern FINDBY_CSS_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*css\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern FINDBY_XPATH_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*xpath\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern FINDBY_TAG_NAME_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*tagName\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern FINDBY_LINK_TEXT_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*linkText\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");
    private static final Pattern FINDBY_PARTIAL_LINK_TEXT_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*partialLinkText\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");

    // Combined @FindBy pattern for how attribute detection
    private static final Pattern FINDBY_HOW_PATTERN = Pattern.compile(
            "(@FindBy\\s*\\(\\s*how\\s*=\\s*How\\.)([A-Z_]+)(\\s*,\\s*using\\s*=\\s*)\"([^\"]+)\"(\\s*\\))");

    /**
     * Result of a pattern match operation.
     */
    public static class MatchResult {
        private final boolean found;
        private final String updatedLine;
        private final String oldValue;
        private final String newValue;
        private final LocatorInfo.LocatorStrategy strategy;

        private MatchResult(boolean found, String updatedLine, String oldValue,
                           String newValue, LocatorInfo.LocatorStrategy strategy) {
            this.found = found;
            this.updatedLine = updatedLine;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.strategy = strategy;
        }

        public boolean isFound() {
            return found;
        }

        public String getUpdatedLine() {
            return updatedLine;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public LocatorInfo.LocatorStrategy getStrategy() {
            return strategy;
        }

        public static MatchResult notFound() {
            return new MatchResult(false, null, null, null, null);
        }

        public static MatchResult found(String updatedLine, String oldValue,
                                        String newValue, LocatorInfo.LocatorStrategy strategy) {
            return new MatchResult(true, updatedLine, oldValue, newValue, strategy);
        }
    }

    /**
     * Attempts to find and replace a locator in the given line.
     *
     * @param line          the source code line
     * @param oldValue      the old locator value to find
     * @param newValue      the new locator value to replace with
     * @param strategy      the locator strategy (optional, for targeted replacement)
     * @return MatchResult containing the updated line if found
     */
    public MatchResult replaceLocator(String line, String oldValue, String newValue,
                                       LocatorInfo.LocatorStrategy strategy) {
        if (line == null || oldValue == null || newValue == null) {
            return MatchResult.notFound();
        }

        // If strategy is specified, try that pattern first
        if (strategy != null) {
            MatchResult result = replaceByStrategy(line, oldValue, newValue, strategy);
            if (result.isFound()) {
                return result;
            }
        }

        // Try all By.* patterns
        MatchResult result = tryReplaceByPatterns(line, oldValue, newValue);
        if (result.isFound()) {
            return result;
        }

        // Try all @FindBy patterns
        result = tryReplaceFindByPatterns(line, oldValue, newValue);
        if (result.isFound()) {
            return result;
        }

        // Try @FindBy with How enum
        result = tryReplaceFindByHowPattern(line, oldValue, newValue);
        if (result.isFound()) {
            return result;
        }

        return MatchResult.notFound();
    }

    /**
     * Replaces locator using the specified strategy pattern.
     */
    private MatchResult replaceByStrategy(String line, String oldValue, String newValue,
                                          LocatorInfo.LocatorStrategy strategy) {
        Pattern byPattern = getByPattern(strategy);
        Pattern findByPattern = getFindByPattern(strategy);

        if (byPattern != null) {
            MatchResult result = tryReplace(line, byPattern, oldValue, newValue, strategy);
            if (result.isFound()) {
                return result;
            }
        }

        if (findByPattern != null) {
            return tryReplace(line, findByPattern, oldValue, newValue, strategy);
        }

        return MatchResult.notFound();
    }

    /**
     * Tries all By.* patterns.
     */
    private MatchResult tryReplaceByPatterns(String line, String oldValue, String newValue) {
        Pattern[] patterns = {
                BY_ID_PATTERN, BY_NAME_PATTERN, BY_CLASS_NAME_PATTERN,
                BY_CSS_SELECTOR_PATTERN, BY_XPATH_PATTERN, BY_TAG_NAME_PATTERN,
                BY_LINK_TEXT_PATTERN, BY_PARTIAL_LINK_TEXT_PATTERN
        };
        LocatorInfo.LocatorStrategy[] strategies = {
                LocatorInfo.LocatorStrategy.ID, LocatorInfo.LocatorStrategy.NAME,
                LocatorInfo.LocatorStrategy.CLASS_NAME, LocatorInfo.LocatorStrategy.CSS,
                LocatorInfo.LocatorStrategy.XPATH, LocatorInfo.LocatorStrategy.TAG_NAME,
                LocatorInfo.LocatorStrategy.LINK_TEXT, LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT
        };

        for (int i = 0; i < patterns.length; i++) {
            MatchResult result = tryReplace(line, patterns[i], oldValue, newValue, strategies[i]);
            if (result.isFound()) {
                return result;
            }
        }

        return MatchResult.notFound();
    }

    /**
     * Tries all @FindBy patterns.
     */
    private MatchResult tryReplaceFindByPatterns(String line, String oldValue, String newValue) {
        Pattern[] patterns = {
                FINDBY_ID_PATTERN, FINDBY_NAME_PATTERN, FINDBY_CLASS_NAME_PATTERN,
                FINDBY_CSS_PATTERN, FINDBY_XPATH_PATTERN, FINDBY_TAG_NAME_PATTERN,
                FINDBY_LINK_TEXT_PATTERN, FINDBY_PARTIAL_LINK_TEXT_PATTERN
        };
        LocatorInfo.LocatorStrategy[] strategies = {
                LocatorInfo.LocatorStrategy.ID, LocatorInfo.LocatorStrategy.NAME,
                LocatorInfo.LocatorStrategy.CLASS_NAME, LocatorInfo.LocatorStrategy.CSS,
                LocatorInfo.LocatorStrategy.XPATH, LocatorInfo.LocatorStrategy.TAG_NAME,
                LocatorInfo.LocatorStrategy.LINK_TEXT, LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT
        };

        for (int i = 0; i < patterns.length; i++) {
            MatchResult result = tryReplace(line, patterns[i], oldValue, newValue, strategies[i]);
            if (result.isFound()) {
                return result;
            }
        }

        return MatchResult.notFound();
    }

    /**
     * Tries the @FindBy(how=How.*, using="...") pattern.
     */
    private MatchResult tryReplaceFindByHowPattern(String line, String oldValue, String newValue) {
        Matcher matcher = FINDBY_HOW_PATTERN.matcher(line);
        while (matcher.find()) {
            String foundValue = matcher.group(4);
            if (foundValue.equals(oldValue)) {
                String howType = matcher.group(2);
                LocatorInfo.LocatorStrategy strategy = howToStrategy(howType);
                String replacement = matcher.group(1) + howType + matcher.group(3) +
                        "\"" + escapeForReplacement(newValue) + "\"" + matcher.group(5);
                String updatedLine = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
                return MatchResult.found(updatedLine, oldValue, newValue, strategy);
            }
        }
        return MatchResult.notFound();
    }

    /**
     * Attempts to replace using a specific pattern.
     */
    private MatchResult tryReplace(String line, Pattern pattern, String oldValue,
                                   String newValue, LocatorInfo.LocatorStrategy strategy) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String foundValue = matcher.group(2);
            if (foundValue.equals(oldValue)) {
                String replacement = matcher.group(1) + "\"" + escapeForReplacement(newValue) + "\"" + matcher.group(3);
                String updatedLine = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
                return MatchResult.found(updatedLine, oldValue, newValue, strategy);
            }
        }
        return MatchResult.notFound();
    }

    /**
     * Gets the By.* pattern for a strategy.
     */
    private Pattern getByPattern(LocatorInfo.LocatorStrategy strategy) {
        return switch (strategy) {
            case ID -> BY_ID_PATTERN;
            case NAME -> BY_NAME_PATTERN;
            case CLASS_NAME -> BY_CLASS_NAME_PATTERN;
            case CSS -> BY_CSS_SELECTOR_PATTERN;
            case XPATH -> BY_XPATH_PATTERN;
            case TAG_NAME -> BY_TAG_NAME_PATTERN;
            case LINK_TEXT -> BY_LINK_TEXT_PATTERN;
            case PARTIAL_LINK_TEXT -> BY_PARTIAL_LINK_TEXT_PATTERN;
        };
    }

    /**
     * Gets the @FindBy pattern for a strategy.
     */
    private Pattern getFindByPattern(LocatorInfo.LocatorStrategy strategy) {
        return switch (strategy) {
            case ID -> FINDBY_ID_PATTERN;
            case NAME -> FINDBY_NAME_PATTERN;
            case CLASS_NAME -> FINDBY_CLASS_NAME_PATTERN;
            case CSS -> FINDBY_CSS_PATTERN;
            case XPATH -> FINDBY_XPATH_PATTERN;
            case TAG_NAME -> FINDBY_TAG_NAME_PATTERN;
            case LINK_TEXT -> FINDBY_LINK_TEXT_PATTERN;
            case PARTIAL_LINK_TEXT -> FINDBY_PARTIAL_LINK_TEXT_PATTERN;
        };
    }

    /**
     * Converts How enum name to LocatorStrategy.
     */
    private LocatorInfo.LocatorStrategy howToStrategy(String howType) {
        return switch (howType) {
            case "ID" -> LocatorInfo.LocatorStrategy.ID;
            case "NAME" -> LocatorInfo.LocatorStrategy.NAME;
            case "CLASS_NAME" -> LocatorInfo.LocatorStrategy.CLASS_NAME;
            case "CSS" -> LocatorInfo.LocatorStrategy.CSS;
            case "XPATH" -> LocatorInfo.LocatorStrategy.XPATH;
            case "TAG_NAME" -> LocatorInfo.LocatorStrategy.TAG_NAME;
            case "LINK_TEXT" -> LocatorInfo.LocatorStrategy.LINK_TEXT;
            case "PARTIAL_LINK_TEXT" -> LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT;
            default -> LocatorInfo.LocatorStrategy.CSS;
        };
    }

    /**
     * Escapes special characters for use in replacement string.
     */
    private String escapeForReplacement(String value) {
        // Escape backslashes and quotes
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /**
     * Detects the locator strategy used in a line of code.
     */
    public Optional<LocatorInfo.LocatorStrategy> detectStrategy(String line) {
        if (line == null) {
            return Optional.empty();
        }

        if (line.contains("By.id") || line.contains("id =") || line.contains("id=")) {
            return Optional.of(LocatorInfo.LocatorStrategy.ID);
        }
        if (line.contains("By.name") || line.contains("name =") || line.contains("name=")) {
            return Optional.of(LocatorInfo.LocatorStrategy.NAME);
        }
        if (line.contains("By.className") || line.contains("className =") || line.contains("className=")) {
            return Optional.of(LocatorInfo.LocatorStrategy.CLASS_NAME);
        }
        if (line.contains("By.cssSelector") || line.contains("css =") || line.contains("css=")) {
            return Optional.of(LocatorInfo.LocatorStrategy.CSS);
        }
        if (line.contains("By.xpath") || line.contains("xpath =") || line.contains("xpath=")) {
            return Optional.of(LocatorInfo.LocatorStrategy.XPATH);
        }
        if (line.contains("By.tagName") || line.contains("tagName =") || line.contains("tagName=")) {
            return Optional.of(LocatorInfo.LocatorStrategy.TAG_NAME);
        }
        if (line.contains("By.linkText") || line.contains("linkText =") || line.contains("linkText=")) {
            return Optional.of(LocatorInfo.LocatorStrategy.LINK_TEXT);
        }
        if (line.contains("By.partialLinkText") || line.contains("partialLinkText =") || line.contains("partialLinkText=")) {
            return Optional.of(LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT);
        }

        return Optional.empty();
    }

    /**
     * Extracts the locator value from a line of code.
     */
    public Optional<String> extractLocatorValue(String line) {
        if (line == null) {
            return Optional.empty();
        }

        // Try all patterns
        Pattern[] allPatterns = {
                BY_ID_PATTERN, BY_NAME_PATTERN, BY_CLASS_NAME_PATTERN,
                BY_CSS_SELECTOR_PATTERN, BY_XPATH_PATTERN, BY_TAG_NAME_PATTERN,
                BY_LINK_TEXT_PATTERN, BY_PARTIAL_LINK_TEXT_PATTERN,
                FINDBY_ID_PATTERN, FINDBY_NAME_PATTERN, FINDBY_CLASS_NAME_PATTERN,
                FINDBY_CSS_PATTERN, FINDBY_XPATH_PATTERN, FINDBY_TAG_NAME_PATTERN,
                FINDBY_LINK_TEXT_PATTERN, FINDBY_PARTIAL_LINK_TEXT_PATTERN
        };

        for (Pattern pattern : allPatterns) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Optional.of(matcher.group(2));
            }
        }

        // Try @FindBy with How
        Matcher howMatcher = FINDBY_HOW_PATTERN.matcher(line);
        if (howMatcher.find()) {
            return Optional.of(howMatcher.group(4));
        }

        return Optional.empty();
    }
}
