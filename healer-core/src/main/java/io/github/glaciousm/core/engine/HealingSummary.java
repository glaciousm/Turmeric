package io.github.glaciousm.core.engine;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks healing events during test execution and provides a summary.
 *
 * This class collects all healed locators so users know exactly what
 * to update in their source code after a test run.
 */
public class HealingSummary {

    private static final HealingSummary INSTANCE = new HealingSummary();

    private final List<HealedLocator> healedLocators = new CopyOnWriteArrayList<>();
    private final Set<String> recordedLocators = ConcurrentHashMap.newKeySet(); // For deduplication
    private boolean enabled = true;

    public static HealingSummary getInstance() {
        return INSTANCE;
    }

    /**
     * Record a healed locator.
     */
    public void recordHeal(String stepText, String originalLocator, String healedLocator,
                           double confidence, String sourceFile, int lineNumber) {
        if (enabled) {
            // Deduplicate based on original locator to avoid recording same heal multiple times
            String key = originalLocator;
            if (recordedLocators.add(key)) {
                healedLocators.add(new HealedLocator(
                    stepText, originalLocator, healedLocator, confidence, sourceFile, lineNumber,
                    null, null  // No screenshots
                ));
            }
        }
    }

    /**
     * Record a healed locator (simplified version without source location).
     */
    public void recordHeal(String stepText, String originalLocator, String healedLocator, double confidence) {
        recordHeal(stepText, originalLocator, healedLocator, confidence, null, 0);
    }

    /**
     * Record a healed locator with before/after screenshots for visual evidence.
     */
    public void recordHealWithScreenshots(String stepText, String originalLocator, String healedLocator,
                                          double confidence, String sourceFile, int lineNumber,
                                          String beforeScreenshotBase64, String afterScreenshotBase64) {
        if (enabled) {
            // Deduplicate based on original locator to avoid recording same heal multiple times
            String key = originalLocator;
            if (recordedLocators.add(key)) {
                healedLocators.add(new HealedLocator(
                    stepText, originalLocator, healedLocator, confidence, sourceFile, lineNumber,
                    beforeScreenshotBase64, afterScreenshotBase64
                ));
            }
        }
    }

    /**
     * Get all healed locators.
     */
    public List<HealedLocator> getHealedLocators() {
        return new ArrayList<>(healedLocators);
    }

    /**
     * Check if any healing occurred.
     */
    public boolean hasHeals() {
        return !healedLocators.isEmpty();
    }

    /**
     * Get count of healed locators.
     */
    public int getHealCount() {
        return healedLocators.size();
    }

    /**
     * Clear all recorded heals.
     */
    public void clear() {
        healedLocators.clear();
        recordedLocators.clear();
    }

    /**
     * Enable or disable healing summary collection.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String MAGENTA = "\u001B[35m";

    /**
     * Print a summary to console.
     * Uses ASCII characters, ANSI colors, and single print to avoid Surefire stream fragmentation.
     */
    public void printSummary() {
        if (healedLocators.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(CYAN).append(BOLD);
        sb.append("+----------------------------------------------------------------------------+\n");
        sb.append("|                    INTENT HEALER - HEALING SUMMARY                        |\n");
        sb.append("+----------------------------------------------------------------------------+\n");
        sb.append(RESET);
        sb.append(CYAN);
        sb.append(String.format("|  Total healed locators: %-52d |\n", healedLocators.size()));
        sb.append("|                                                                            |\n");
        sb.append("|  The following locators were automatically healed during this test run.   |\n");
        sb.append("|  Consider updating your source code with the healed locators below:       |\n");
        sb.append("+----------------------------------------------------------------------------+\n");
        sb.append(RESET);
        sb.append("\n");

        int index = 1;
        for (HealedLocator heal : healedLocators) {
            sb.append(String.format("  [%d] %s\n", index++, truncate(heal.stepText(), 70)));
            sb.append("      +-----------------------------------------------------------------------\n");
            sb.append(YELLOW);
            sb.append(String.format("      | ORIGINAL:  %s\n", heal.originalLocator()));
            sb.append(RESET);
            sb.append(GREEN);
            sb.append(String.format("      | HEALED TO: %s\n", heal.healedLocator()));
            sb.append(RESET);
            sb.append(getConfidenceColor(heal.confidence()));
            sb.append(String.format("      | Confidence: %.0f%%\n", heal.confidence() * 100));
            sb.append(RESET);
            if (heal.sourceFile() != null && !heal.sourceFile().isEmpty()) {
                sb.append(String.format("      | Location: %s:%d\n", heal.sourceFile(), heal.lineNumber()));
            }
            sb.append("      +-----------------------------------------------------------------------\n");
            sb.append("\n");
        }

        sb.append(CYAN);
        sb.append("  --------------------------------------------------------------------------\n");
        sb.append("  TIP: Update your Page Objects with the healed locators above to prevent\n");
        sb.append("       repeated healing and improve test execution speed.\n");
        sb.append("  --------------------------------------------------------------------------\n");
        sb.append(RESET);
        sb.append("\n");

        // Print as single block with UTF-8 encoding to support Greek and other characters
        try {
            PrintStream utf8Out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
            utf8Out.print(sb.toString());
            utf8Out.flush();
        } catch (Exception e) {
            // Fallback to default if UTF-8 fails
            System.out.print(sb.toString());
            System.out.flush();
        }
    }

    private String getConfidenceColor(double confidence) {
        if (confidence >= 0.9) return GREEN;
        if (confidence >= 0.75) return YELLOW;
        return MAGENTA;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Record of a healed locator with optional visual evidence.
     */
    public record HealedLocator(
        String stepText,
        String originalLocator,
        String healedLocator,
        double confidence,
        String sourceFile,
        int lineNumber,
        String beforeScreenshotBase64,
        String afterScreenshotBase64
    ) {
        /**
         * Check if this heal has visual evidence (screenshots).
         */
        public boolean hasVisualEvidence() {
            return beforeScreenshotBase64 != null && afterScreenshotBase64 != null;
        }
    }
}
