package io.github.glaciousm.core.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Utility for normalizing text content from web elements.
 */
public final class TextNormalizer {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NON_PRINTABLE_PATTERN = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");

    private TextNormalizer() {
        // Utility class
    }

    /**
     * Normalizes text by trimming, collapsing whitespace, and removing control characters.
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove control characters except whitespace
        String cleaned = NON_PRINTABLE_PATTERN.matcher(text).replaceAll("");

        // Normalize Unicode
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFC);

        // Collapse whitespace
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ");

        // Trim
        return cleaned.trim();
    }

    /**
     * Normalizes text and truncates to maximum length.
     */
    public static String normalize(String text, int maxLength) {
        String normalized = normalize(text);
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength - 3) + "...";
        }
        return normalized;
    }

    /**
     * Checks if two texts are semantically similar after normalization.
     */
    public static boolean areSimilar(String text1, String text2) {
        if (text1 == null && text2 == null) return true;
        if (text1 == null || text2 == null) return false;

        String norm1 = normalize(text1).toLowerCase();
        String norm2 = normalize(text2).toLowerCase();

        return norm1.equals(norm2);
    }

    /**
     * Extracts keywords from text (simple word tokenization).
     */
    public static String[] extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }

        String normalized = normalize(text).toLowerCase();
        return normalized.split("\\s+");
    }

    /**
     * Checks if text contains a keyword (case-insensitive).
     */
    public static boolean containsKeyword(String text, String keyword) {
        if (text == null || keyword == null) return false;

        String normalizedText = normalize(text).toLowerCase();
        String normalizedKeyword = normalize(keyword).toLowerCase();

        return normalizedText.contains(normalizedKeyword);
    }

    /**
     * Removes HTML entities from text.
     */
    public static String removeHtmlEntities(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    /**
     * Escapes text for use in CSS selectors.
     */
    public static String escapeForCss(String text) {
        if (text == null) return null;

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\'' || c == '\\' || c == '[' || c == ']' ||
                c == '(' || c == ')' || c == '{' || c == '}' || c == ':' ||
                c == '.' || c == '#' || c == '>' || c == '+' || c == '~' ||
                c == '*' || c == ',' || c == '=' || c == '^' || c == '$' ||
                c == '|' || c == '!' || c == '/' || c == '@') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Escapes text for use in XPath expressions.
     */
    public static String escapeForXPath(String text) {
        if (text == null) return null;

        // If text doesn't contain quotes, wrap in quotes
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        if (!text.contains("\"")) {
            return "\"" + text + "\"";
        }

        // Contains both types of quotes, use concat
        StringBuilder sb = new StringBuilder("concat(");
        String[] parts = text.split("'", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", \"'\", ");
            }
            sb.append("'").append(parts[i]).append("'");
        }
        sb.append(")");
        return sb.toString();
    }
}
