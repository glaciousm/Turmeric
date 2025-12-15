package com.intenthealer.core.engine.patch;

import com.intenthealer.core.model.ElementCandidate;
import com.intenthealer.core.model.HealDecision;
import com.intenthealer.report.model.HealEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates locator patch suggestions based on successful heals.
 * Provides code snippets and git-style patches for updating test code.
 */
public class LocatorPatchGenerator {

    private static final Logger logger = LoggerFactory.getLogger(LocatorPatchGenerator.class);

    private final List<LocatorPatch> patches = new ArrayList<>();

    /**
     * Generate a patch suggestion from a heal decision.
     */
    public LocatorPatch generatePatch(HealDecision decision, String sourceFile, int lineNumber) {
        if (decision == null || !decision.isShouldHeal()) {
            return null;
        }

        ElementCandidate chosen = decision.getChosenCandidate();
        if (chosen == null) {
            return null;
        }

        LocatorPatch patch = new LocatorPatch();
        patch.setTimestamp(Instant.now());
        patch.setSourceFile(sourceFile);
        patch.setLineNumber(lineNumber);
        patch.setOriginalLocator(decision.getOriginalLocator());
        patch.setNewLocator(chosen.getLocator());
        patch.setConfidence(chosen.getConfidence());
        patch.setReason(decision.getReasoning());
        patch.setLocatorType(inferLocatorType(chosen.getLocator()));

        patches.add(patch);
        return patch;
    }

    /**
     * Generate patch from a heal event.
     */
    public LocatorPatch generatePatch(HealEvent event) {
        if (event == null || event.getHealedLocator() == null) {
            return null;
        }

        LocatorPatch patch = new LocatorPatch();
        patch.setTimestamp(event.getTimestamp());
        patch.setOriginalLocator(event.getOriginalLocator());
        patch.setNewLocator(event.getHealedLocator());
        patch.setConfidence(event.getConfidence());
        patch.setReason(event.getReasoning());
        patch.setLocatorType(inferLocatorType(event.getHealedLocator()));

        patches.add(patch);
        return patch;
    }

    /**
     * Generate a git-style unified diff patch.
     */
    public String generateUnifiedDiff(LocatorPatch patch, String originalCode) {
        if (patch == null || originalCode == null) {
            return null;
        }

        String[] lines = originalCode.split("\n");
        StringBuilder diff = new StringBuilder();

        diff.append("--- a/").append(patch.getSourceFile() != null ? patch.getSourceFile() : "test.java").append("\n");
        diff.append("+++ b/").append(patch.getSourceFile() != null ? patch.getSourceFile() : "test.java").append("\n");

        int startLine = Math.max(0, patch.getLineNumber() - 3);
        int endLine = Math.min(lines.length, patch.getLineNumber() + 3);

        diff.append("@@ -").append(startLine + 1).append(",").append(endLine - startLine)
                .append(" +").append(startLine + 1).append(",").append(endLine - startLine).append(" @@\n");

        for (int i = startLine; i < endLine; i++) {
            String line = i < lines.length ? lines[i] : "";
            if (i == patch.getLineNumber() - 1 && line.contains(patch.getOriginalLocator())) {
                diff.append("-").append(line).append("\n");
                diff.append("+").append(line.replace(patch.getOriginalLocator(), patch.getNewLocator())).append("\n");
            } else {
                diff.append(" ").append(line).append("\n");
            }
        }

        return diff.toString();
    }

    /**
     * Generate code snippet suggestions for different locator strategies.
     */
    public Map<String, String> generateCodeSnippets(LocatorPatch patch) {
        Map<String, String> snippets = new HashMap<>();
        String newLocator = patch.getNewLocator();

        if (newLocator == null) {
            return snippets;
        }

        // CSS Selector version
        if (!newLocator.startsWith("//")) {
            snippets.put("cssSelector", String.format(
                    "driver.findElement(By.cssSelector(\"%s\"))",
                    escapeJavaString(newLocator)));
        }

        // XPath version
        String xpath = convertToXPath(newLocator);
        if (xpath != null) {
            snippets.put("xpath", String.format(
                    "driver.findElement(By.xpath(\"%s\"))",
                    escapeJavaString(xpath)));
        }

        // ID version (if applicable)
        if (newLocator.startsWith("#")) {
            String id = newLocator.substring(1);
            snippets.put("id", String.format(
                    "driver.findElement(By.id(\"%s\"))",
                    escapeJavaString(id)));
        }

        // Name version (if applicable)
        Matcher nameMatcher = Pattern.compile("\\[name=['\"]([^'\"]+)['\"]\\]").matcher(newLocator);
        if (nameMatcher.find()) {
            snippets.put("name", String.format(
                    "driver.findElement(By.name(\"%s\"))",
                    escapeJavaString(nameMatcher.group(1))));
        }

        // Class version (if simple class selector)
        if (newLocator.matches("^[a-zA-Z]*\\.[a-zA-Z0-9_-]+$")) {
            String className = newLocator.contains(".") ?
                    newLocator.substring(newLocator.indexOf('.') + 1) : newLocator;
            snippets.put("className", String.format(
                    "driver.findElement(By.className(\"%s\"))",
                    escapeJavaString(className)));
        }

        // Data-testid version
        Matcher testIdMatcher = Pattern.compile("\\[data-testid=['\"]([^'\"]+)['\"]\\]").matcher(newLocator);
        if (testIdMatcher.find()) {
            snippets.put("dataTestId", String.format(
                    "driver.findElement(By.cssSelector(\"[data-testid='%s']\"))",
                    escapeJavaString(testIdMatcher.group(1))));
        }

        return snippets;
    }

    /**
     * Generate a Markdown report of all patches.
     */
    public String generateMarkdownReport() {
        if (patches.isEmpty()) {
            return "# Locator Patches\n\nNo patches generated.\n";
        }

        StringBuilder report = new StringBuilder();
        report.append("# Locator Patch Suggestions\n\n");
        report.append("Generated: ").append(Instant.now()).append("\n\n");
        report.append("Total patches: ").append(patches.size()).append("\n\n");

        report.append("| Original | New | Confidence | Type |\n");
        report.append("|----------|-----|------------|------|\n");

        for (LocatorPatch patch : patches) {
            report.append("| `").append(truncate(patch.getOriginalLocator(), 30)).append("` ");
            report.append("| `").append(truncate(patch.getNewLocator(), 30)).append("` ");
            report.append("| ").append(String.format("%.2f", patch.getConfidence())).append(" ");
            report.append("| ").append(patch.getLocatorType()).append(" |\n");
        }

        report.append("\n## Detailed Patches\n\n");

        int index = 1;
        for (LocatorPatch patch : patches) {
            report.append("### Patch ").append(index++).append("\n\n");

            if (patch.getSourceFile() != null) {
                report.append("**File:** `").append(patch.getSourceFile()).append("`");
                if (patch.getLineNumber() > 0) {
                    report.append(" (line ").append(patch.getLineNumber()).append(")");
                }
                report.append("\n\n");
            }

            report.append("**Original:**\n```\n").append(patch.getOriginalLocator()).append("\n```\n\n");
            report.append("**Suggested:**\n```\n").append(patch.getNewLocator()).append("\n```\n\n");

            if (patch.getReason() != null) {
                report.append("**Reason:** ").append(patch.getReason()).append("\n\n");
            }

            Map<String, String> snippets = generateCodeSnippets(patch);
            if (!snippets.isEmpty()) {
                report.append("**Code Alternatives:**\n");
                for (Map.Entry<String, String> entry : snippets.entrySet()) {
                    report.append("- ").append(entry.getKey()).append(": `").append(entry.getValue()).append("`\n");
                }
                report.append("\n");
            }

            report.append("---\n\n");
        }

        return report.toString();
    }

    /**
     * Save patches to a file.
     */
    public void savePatchReport(Path outputPath) throws IOException {
        String report = generateMarkdownReport();
        Files.writeString(outputPath, report);
        logger.info("Patch report saved to: {}", outputPath);
    }

    /**
     * Get all generated patches.
     */
    public List<LocatorPatch> getPatches() {
        return new ArrayList<>(patches);
    }

    /**
     * Clear all patches.
     */
    public void clear() {
        patches.clear();
    }

    /**
     * Infer the type of locator strategy.
     */
    private String inferLocatorType(String locator) {
        if (locator == null) return "unknown";

        if (locator.startsWith("//") || locator.startsWith("(//")) {
            return "xpath";
        } else if (locator.startsWith("#")) {
            return "id";
        } else if (locator.matches("^\\.[a-zA-Z].*")) {
            return "class";
        } else if (locator.contains("[name=")) {
            return "name";
        } else if (locator.contains("[data-testid=")) {
            return "data-testid";
        } else {
            return "css";
        }
    }

    /**
     * Convert CSS selector to XPath.
     */
    private String convertToXPath(String css) {
        if (css == null) return null;
        if (css.startsWith("//")) return css; // Already XPath

        try {
            StringBuilder xpath = new StringBuilder("//");

            // Handle ID selector
            if (css.startsWith("#")) {
                String id = css.substring(1);
                return "//*[@id='" + id + "']";
            }

            // Handle class selector
            if (css.startsWith(".")) {
                String className = css.substring(1);
                return "//*[contains(@class, '" + className + "')]";
            }

            // Handle tag with class
            Matcher tagClass = Pattern.compile("^([a-zA-Z]+)\\.([a-zA-Z0-9_-]+)").matcher(css);
            if (tagClass.find()) {
                return "//" + tagClass.group(1) + "[contains(@class, '" + tagClass.group(2) + "')]";
            }

            // Handle attribute selectors
            Matcher attr = Pattern.compile("\\[([^=]+)=['\"]([^'\"]+)['\"]\\]").matcher(css);
            if (attr.find()) {
                String tag = css.contains("[") ? css.substring(0, css.indexOf('[')) : "*";
                if (tag.isEmpty()) tag = "*";
                return "//" + tag + "[@" + attr.group(1) + "='" + attr.group(2) + "']";
            }

            // Simple tag selector
            if (css.matches("^[a-zA-Z]+$")) {
                return "//" + css;
            }

            return null;
        } catch (Exception e) {
            logger.debug("Could not convert CSS to XPath: {}", css);
            return null;
        }
    }

    /**
     * Escape string for Java code.
     */
    private String escapeJavaString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Truncate string for display.
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    /**
     * Represents a single locator patch suggestion.
     */
    public static class LocatorPatch {
        private Instant timestamp;
        private String sourceFile;
        private int lineNumber;
        private String originalLocator;
        private String newLocator;
        private double confidence;
        private String reason;
        private String locatorType;

        // Getters and Setters
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public String getSourceFile() { return sourceFile; }
        public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

        public int getLineNumber() { return lineNumber; }
        public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

        public String getOriginalLocator() { return originalLocator; }
        public void setOriginalLocator(String originalLocator) { this.originalLocator = originalLocator; }

        public String getNewLocator() { return newLocator; }
        public void setNewLocator(String newLocator) { this.newLocator = newLocator; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getLocatorType() { return locatorType; }
        public void setLocatorType(String locatorType) { this.locatorType = locatorType; }
    }
}
