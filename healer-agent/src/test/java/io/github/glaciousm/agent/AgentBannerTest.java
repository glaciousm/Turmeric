package io.github.glaciousm.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentBanner.
 * Tests banner output for different configuration states.
 */
class AgentBannerTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void setUp() {
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Nested
    @DisplayName("Banner output")
    class BannerOutputTests {

        @Test
        @DisplayName("should print ACTIVE banner when provider is available")
        void printsActiveBanner() {
            AgentBanner.print(true);

            String output = capturedOut.toString();
            assertThat(output).contains("INTENT HEALER AGENT");
            assertThat(output).contains("ACTIVE");
            assertThat(output).contains("Mode:");
            assertThat(output).contains("Provider:");
            assertThat(output).contains("Model:");
            assertThat(output).contains("Healing:");
            assertThat(output).contains("Self-healing is active");
        }

        @Test
        @DisplayName("should print INACTIVE banner when provider is not available")
        void printsInactiveBanner() {
            AgentBanner.print(false);

            String output = capturedOut.toString();
            assertThat(output).contains("INTENT HEALER AGENT");
            assertThat(output).contains("INACTIVE");
            assertThat(output).contains("DISABLED");
            assertThat(output).contains("WARNING");
            assertThat(output).contains("API key is not configured");
        }

        @Test
        @DisplayName("should include configuration info in banner")
        void includesConfigInfo() {
            AgentBanner.print(true);

            String output = capturedOut.toString();
            // Default values when config is not loaded
            assertThat(output).containsAnyOf("AUTO_SAFE", "mock", "heuristic");
        }
    }

    @Nested
    @DisplayName("Minimal banner")
    class MinimalBannerTests {

        @Test
        @DisplayName("should print one-line minimal banner")
        void printsMinimalBanner() {
            AgentBanner.printMinimal();

            String output = capturedOut.toString();
            assertThat(output).contains("[Intent Healer] Agent active");
            assertThat(output).contains("provider:");
            // Should be a single line (or close to it)
            assertThat(output.split("\n").length).isLessThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Banner format")
    class BannerFormatTests {

        @Test
        @DisplayName("should have proper banner borders")
        void hasProperBorders() {
            AgentBanner.print(true);

            String output = capturedOut.toString();
            assertThat(output).contains("+===============================================================+");
            assertThat(output).contains("+---------------------------------------------------------------+");
        }

        @Test
        @DisplayName("should have aligned content")
        void hasAlignedContent() {
            AgentBanner.print(true);

            String output = capturedOut.toString();
            // Check that lines with | start and end properly (accounting for ANSI codes)
            for (String line : output.split("\n")) {
                // Strip ANSI escape codes for content validation
                String strippedLine = line.replaceAll("\u001B\\[[0-9;]*m", "").trim();
                if (strippedLine.contains("Mode:") || strippedLine.contains("Provider:") || strippedLine.contains("Model:") || strippedLine.contains("Healing:")) {
                    assertThat(strippedLine).startsWith("|");
                    assertThat(strippedLine).endsWith("|");
                }
            }
        }
    }
}
