package io.github.glaciousm.core.engine;

import io.github.glaciousm.core.engine.LocatorRecommender.LocatorAnalysis;
import io.github.glaciousm.core.engine.LocatorRecommender.Recommendation;
import io.github.glaciousm.core.engine.LocatorRecommender.Severity;
import io.github.glaciousm.core.model.LocatorInfo;
import io.github.glaciousm.core.model.LocatorInfo.LocatorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocatorRecommender.
 */
@DisplayName("LocatorRecommender Tests")
class LocatorRecommenderTest {

    private LocatorRecommender recommender;

    @BeforeEach
    void setUp() {
        recommender = new LocatorRecommender();
    }

    @Nested
    @DisplayName("XPath Analysis")
    class XPathAnalysisTests {

        @Test
        @DisplayName("should flag positional indices as critical")
        void flagsPositionalIndices() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "//div[1]/button[2]/span",
                    LocatorStrategy.XPATH
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("Positional index") && r.severity() == Severity.CRITICAL
            ));
        }

        @Test
        @DisplayName("should flag long XPath as warning")
        void flagsLongXPath() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "//html/body/div/main/section/article/div/form/button",
                    LocatorStrategy.XPATH
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("Long XPath") && r.severity() == Severity.WARNING
            ));
        }

        @Test
        @DisplayName("should flag absolute XPath as critical")
        void flagsAbsoluteXPath() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "/html/body/div[1]/main/button",
                    LocatorStrategy.XPATH
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("Absolute XPath") && r.severity() == Severity.CRITICAL
            ));
        }

        @Test
        @DisplayName("should flag text-based selection")
        void flagsTextBasedSelection() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "//button[contains(text(), 'Submit')]",
                    LocatorStrategy.XPATH
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("Text-based")
            ));
        }

        @Test
        @DisplayName("should not flag data-testid XPath")
        void acceptsDataTestIdXPath() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "//*[@data-testid='submit-btn']",
                    LocatorStrategy.XPATH
            );

            // Should have no critical or warning issues (only INFO for best practices)
            assertFalse(recs.stream().anyMatch(r ->
                    r.severity() == Severity.CRITICAL || r.severity() == Severity.WARNING
            ));
        }
    }

    @Nested
    @DisplayName("CSS Analysis")
    class CssAnalysisTests {

        @Test
        @DisplayName("should flag nth-child as warning")
        void flagsNthChild() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    ".container > div:nth-child(3)",
                    LocatorStrategy.CSS
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("Index-based")
            ));
        }

        @Test
        @DisplayName("should flag dynamic class names as critical")
        void flagsDynamicClasses() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    ".Button_primary_a1b2c3",
                    LocatorStrategy.CSS
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("Dynamic") && r.severity() == Severity.CRITICAL
            ));
        }

        @Test
        @DisplayName("should flag complex selector chains")
        void flagsComplexSelectors() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "div.container section.main article.content form.login button.submit",
                    LocatorStrategy.CSS
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("Complex selector")
            ));
        }

        @Test
        @DisplayName("should accept data-testid selectors")
        void acceptsDataTestId() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "[data-testid='login-button']",
                    LocatorStrategy.CSS
            );

            // No critical or warning issues
            assertFalse(recs.stream().anyMatch(r ->
                    r.severity() == Severity.CRITICAL || r.severity() == Severity.WARNING
            ));
        }

        @Test
        @DisplayName("should accept data-cy selectors")
        void acceptsDataCy() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "[data-cy='submit']",
                    LocatorStrategy.CSS
            );

            assertFalse(recs.stream().anyMatch(r ->
                    r.severity() == Severity.CRITICAL || r.severity() == Severity.WARNING
            ));
        }
    }

    @Nested
    @DisplayName("ID Analysis")
    class IdAnalysisTests {

        @Test
        @DisplayName("should flag auto-generated IDs")
        void flagsAutoGeneratedIds() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "react_123456",
                    LocatorStrategy.ID
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("Auto-generated") && r.severity() == Severity.CRITICAL
            ));
        }

        @Test
        @DisplayName("should flag IDs with long numbers")
        void flagsNumericIds() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "element_12345678",
                    LocatorStrategy.ID
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.issue().contains("dynamic")
            ));
        }

        @Test
        @DisplayName("should accept semantic IDs")
        void acceptsSemanticIds() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "login-form",
                    LocatorStrategy.ID
            );

            assertFalse(recs.stream().anyMatch(r ->
                    r.severity() == Severity.CRITICAL
            ));
        }
    }

    @Nested
    @DisplayName("Tag Name Analysis")
    class TagNameAnalysisTests {

        @Test
        @DisplayName("should always flag tag name selection as critical")
        void flagsTagName() {
            List<Recommendation> recs = recommender.analyzeLocator(
                    "button",
                    LocatorStrategy.TAG_NAME
            );

            assertTrue(recs.stream().anyMatch(r ->
                    r.severity() == Severity.CRITICAL
            ));
        }
    }

    @Nested
    @DisplayName("Bulk Analysis")
    class BulkAnalysisTests {

        @Test
        @DisplayName("should calculate stability score correctly")
        void calculatesStabilityScore() {
            List<LocatorInfo> locators = List.of(
                    new LocatorInfo(LocatorStrategy.CSS, "[data-testid='good-1']"),
                    new LocatorInfo(LocatorStrategy.CSS, "[data-testid='good-2']"),
                    new LocatorInfo(LocatorStrategy.XPATH, "//div[1]/button[2]"), // Brittle
                    new LocatorInfo(LocatorStrategy.CSS, ".Button_abc123") // Brittle
            );

            LocatorAnalysis analysis = recommender.analyzeHealedLocators(locators);

            assertEquals(4, analysis.totalLocatorsAnalyzed());
            assertEquals(2, analysis.stableLocators());
            assertEquals(2, analysis.brittleLocators());
            assertEquals(50.0, analysis.getStabilityScore(), 0.1);
            assertEquals("Fair", analysis.getStabilityRating());
        }

        @Test
        @DisplayName("should generate top recommendations")
        void generatesTopRecommendations() {
            List<LocatorInfo> locators = List.of(
                    new LocatorInfo(LocatorStrategy.XPATH, "//div[1]/span"),
                    new LocatorInfo(LocatorStrategy.XPATH, "//table[2]/tr[1]"),
                    new LocatorInfo(LocatorStrategy.CSS, ".dynamic_abc123"),
                    new LocatorInfo(LocatorStrategy.CSS, ".hash_def456")
            );

            LocatorAnalysis analysis = recommender.analyzeHealedLocators(locators);

            assertFalse(analysis.topRecommendations().isEmpty());
            assertTrue(analysis.topRecommendations().stream().anyMatch(r ->
                    r.contains("data-testid") || r.contains("positional")
            ));
        }

        @Test
        @DisplayName("should handle empty list")
        void handlesEmptyList() {
            LocatorAnalysis analysis = recommender.analyzeHealedLocators(Collections.emptyList());

            assertEquals(0, analysis.totalLocatorsAnalyzed());
            assertEquals(100.0, analysis.getStabilityScore(), 0.1);
            assertEquals("Excellent", analysis.getStabilityRating());
        }

        @Test
        @DisplayName("should count issues by type")
        void countsIssuesByType() {
            List<LocatorInfo> locators = List.of(
                    new LocatorInfo(LocatorStrategy.XPATH, "//div[1]/button"),
                    new LocatorInfo(LocatorStrategy.XPATH, "//span[3]/a"),
                    new LocatorInfo(LocatorStrategy.CSS, ".container:nth-child(2)")
            );

            LocatorAnalysis analysis = recommender.analyzeHealedLocators(locators);

            assertFalse(analysis.issuesByType().isEmpty());
            assertTrue(analysis.issuesByType().containsKey("Positional index in XPath") ||
                    analysis.issuesByType().containsKey("Index-based selector"));
        }
    }

    @Nested
    @DisplayName("Locator Suggestions")
    class LocatorSuggestionTests {

        @Test
        @DisplayName("should suggest data-testid when available")
        void suggestsDataTestId() {
            Map<String, String> attrs = Map.of(
                    "data-testid", "submit-button",
                    "class", "btn btn-primary",
                    "id", "react_12345"
            );

            Optional<String> suggestion = recommender.suggestBetterLocator(
                    "//button[@class='btn btn-primary']",
                    attrs
            );

            assertTrue(suggestion.isPresent());
            assertTrue(suggestion.get().contains("data-testid"));
            assertTrue(suggestion.get().contains("submit-button"));
        }

        @Test
        @DisplayName("should suggest aria-label when no testid")
        void suggestsAriaLabel() {
            Map<String, String> attrs = Map.of(
                    "aria-label", "Submit form",
                    "class", "btn_abc123"
            );

            Optional<String> suggestion = recommender.suggestBetterLocator(
                    ".btn_abc123",
                    attrs
            );

            assertTrue(suggestion.isPresent());
            assertTrue(suggestion.get().contains("aria-label"));
        }

        @Test
        @DisplayName("should skip auto-generated IDs")
        void skipsAutoGeneratedIds() {
            Map<String, String> attrs = Map.of(
                    "id", "react_12345678",
                    "name", "username"
            );

            Optional<String> suggestion = recommender.suggestBetterLocator(
                    "#react_12345678",
                    attrs
            );

            assertTrue(suggestion.isPresent());
            assertTrue(suggestion.get().contains("name"));
        }

        @Test
        @DisplayName("should return empty for no good attributes")
        void returnsEmptyWhenNoGoodAttributes() {
            Map<String, String> attrs = Map.of(
                    "class", "dynamic_class_abc123"
            );

            Optional<String> suggestion = recommender.suggestBetterLocator(
                    ".dynamic_class_abc123",
                    attrs
            );

            assertFalse(suggestion.isPresent());
        }
    }

    @Nested
    @DisplayName("Stability Check")
    class StabilityCheckTests {

        @Test
        @DisplayName("should identify stable locators")
        void identifiesStableLocators() {
            assertTrue(recommender.isStableLocator("[data-testid='btn']", LocatorStrategy.CSS));
            assertTrue(recommender.isStableLocator("[data-cy='submit']", LocatorStrategy.CSS));
            assertTrue(recommender.isStableLocator("[aria-label='Close']", LocatorStrategy.CSS));
            assertTrue(recommender.isStableLocator("[name='username']", LocatorStrategy.CSS));
            assertTrue(recommender.isStableLocator("login-form", LocatorStrategy.ID));
        }

        @Test
        @DisplayName("should identify unstable locators")
        void identifiesUnstableLocators() {
            assertFalse(recommender.isStableLocator(".btn:nth-child(2)", LocatorStrategy.CSS));
            assertFalse(recommender.isStableLocator(".Button_abc123", LocatorStrategy.CSS));
            assertFalse(recommender.isStableLocator("react_12345", LocatorStrategy.ID));
        }
    }

    @Nested
    @DisplayName("Text Formatting")
    class TextFormattingTests {

        @Test
        @DisplayName("should format analysis as text")
        void formatsAnalysisAsText() {
            List<LocatorInfo> locators = List.of(
                    new LocatorInfo(LocatorStrategy.CSS, "[data-testid='good']"),
                    new LocatorInfo(LocatorStrategy.XPATH, "//div[1]/button")
            );

            LocatorAnalysis analysis = recommender.analyzeHealedLocators(locators);
            String text = recommender.formatRecommendationsAsText(analysis);

            assertNotNull(text);
            assertTrue(text.contains("Locator Stability Analysis"));
            assertTrue(text.contains("Total Locators Analyzed: 2"));
            assertTrue(text.contains("Stability Rating"));
        }
    }
}
