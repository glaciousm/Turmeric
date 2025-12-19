package io.github.glaciousm.core.engine;

import java.util.ArrayList;
import java.util.List;
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
            healedLocators.add(new HealedLocator(
                stepText, originalLocator, healedLocator, confidence, sourceFile, lineNumber,
                null, null  // No screenshots
            ));
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
            healedLocators.add(new HealedLocator(
                stepText, originalLocator, healedLocator, confidence, sourceFile, lineNumber,
                beforeScreenshotBase64, afterScreenshotBase64
            ));
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
    }

    /**
     * Enable or disable healing summary collection.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Print a summary to console.
     */
    public void printSummary() {
        if (healedLocators.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    INTENT HEALER - HEALING SUMMARY                        ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Total healed locators: %-52d ║%n", healedLocators.size());
        System.out.println("║                                                                            ║");
        System.out.println("║  The following locators were automatically healed during this test run.   ║");
        System.out.println("║  Consider updating your source code with the healed locators below:       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        int index = 1;
        for (HealedLocator heal : healedLocators) {
            System.out.printf("  [%d] %s%n", index++, truncate(heal.stepText(), 70));
            System.out.println("      ┌─────────────────────────────────────────────────────────────────────");
            System.out.printf("      │ ORIGINAL:  %s%n", heal.originalLocator());
            System.out.printf("      │ HEALED TO: %s%n", heal.healedLocator());
            System.out.printf("      │ Confidence: %.0f%%%n", heal.confidence() * 100);
            if (heal.sourceFile() != null && !heal.sourceFile().isEmpty()) {
                System.out.printf("      │ Location: %s:%d%n", heal.sourceFile(), heal.lineNumber());
            }
            System.out.println("      └─────────────────────────────────────────────────────────────────────");
            System.out.println();
        }

        System.out.println("  ════════════════════════════════════════════════════════════════════════");
        System.out.println("  TIP: Update your Page Objects with the healed locators above to prevent");
        System.out.println("       repeated healing and improve test execution speed.");
        System.out.println("  ════════════════════════════════════════════════════════════════════════");
        System.out.println();
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
