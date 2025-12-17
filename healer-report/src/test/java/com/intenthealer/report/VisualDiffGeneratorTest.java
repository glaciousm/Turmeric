package com.intenthealer.report;

import com.intenthealer.report.VisualDiffGenerator.DiffResult;
import com.intenthealer.report.VisualDiffGenerator.ScreenshotCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VisualDiffGenerator.
 */
@DisplayName("VisualDiffGenerator Tests")
class VisualDiffGeneratorTest {

    @TempDir
    Path tempDir;

    private VisualDiffGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new VisualDiffGenerator(tempDir);
    }

    @Nested
    @DisplayName("Screenshot Capture Tests")
    class ScreenshotCaptureTests {

        @Test
        @DisplayName("should create capture from byte array")
        void createsFromByteArray() throws IOException {
            BufferedImage image = createSolidColorImage(100, 100, Color.RED);
            byte[] imageBytes = imageToBytes(image);

            ScreenshotCapture capture = generator.createCapture(imageBytes, "Test capture");

            assertNotNull(capture);
            assertEquals("Test capture", capture.description());
            assertEquals("png", capture.format());
            assertNotNull(capture.capturedAt());
        }

        @Test
        @DisplayName("should create capture from BufferedImage")
        void createsFromBufferedImage() throws IOException {
            BufferedImage image = createSolidColorImage(100, 100, Color.BLUE);

            ScreenshotCapture capture = generator.createCapture(image, "Image capture");

            assertNotNull(capture);
            assertNotNull(capture.imageData());
            assertTrue(capture.imageData().length > 0);
        }

        @Test
        @DisplayName("should convert to base64")
        void convertsToBase64() throws IOException {
            BufferedImage image = createSolidColorImage(50, 50, Color.GREEN);
            ScreenshotCapture capture = generator.createCapture(image, "Test");

            String base64 = capture.toBase64();

            assertNotNull(base64);
            assertFalse(base64.isEmpty());
            // Base64 should not contain newlines or spaces
            assertFalse(base64.contains("\n"));
            assertFalse(base64.contains(" "));
        }

        @Test
        @DisplayName("should generate data URI")
        void generatesDataUri() throws IOException {
            BufferedImage image = createSolidColorImage(50, 50, Color.YELLOW);
            ScreenshotCapture capture = generator.createCapture(image, "Test");

            String dataUri = capture.toDataUri();

            assertTrue(dataUri.startsWith("data:image/png;base64,"));
        }
    }

    @Nested
    @DisplayName("Visual Diff Generation Tests")
    class DiffGenerationTests {

        @Test
        @DisplayName("should detect no difference between identical images")
        void detectsNoDifferenceForIdentical() throws IOException {
            BufferedImage image = createSolidColorImage(100, 100, Color.WHITE);
            ScreenshotCapture before = generator.createCapture(image, "Before");
            ScreenshotCapture after = generator.createCapture(image, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "test-001");

            assertTrue(result.isPresent());
            DiffResult diff = result.get();
            assertEquals(0.0, diff.differencePercentage(), 0.01);
            assertEquals(0, diff.differentPixelCount());
            assertTrue(diff.areImagesIdentical());
            assertFalse(diff.hasSignificantDifference());
        }

        @Test
        @DisplayName("should detect significant difference between different images")
        void detectsSignificantDifference() throws IOException {
            BufferedImage beforeImage = createSolidColorImage(100, 100, Color.WHITE);
            BufferedImage afterImage = createSolidColorImage(100, 100, Color.BLACK);

            ScreenshotCapture before = generator.createCapture(beforeImage, "Before");
            ScreenshotCapture after = generator.createCapture(afterImage, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "test-002");

            assertTrue(result.isPresent());
            DiffResult diff = result.get();
            assertTrue(diff.differencePercentage() > 90.0); // Should be very different
            assertTrue(diff.differentPixelCount() > 9000); // Most pixels should be different
            assertFalse(diff.areImagesIdentical());
            assertTrue(diff.hasSignificantDifference());
        }

        @Test
        @DisplayName("should detect partial difference")
        void detectsPartialDifference() throws IOException {
            // Create before image: half white, half red
            BufferedImage beforeImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g1 = beforeImage.createGraphics();
            g1.setColor(Color.WHITE);
            g1.fillRect(0, 0, 100, 100);
            g1.setColor(Color.RED);
            g1.fillRect(0, 0, 50, 100);
            g1.dispose();

            // Create after image: half white, half blue (changed half)
            BufferedImage afterImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = afterImage.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, 100, 100);
            g2.setColor(Color.BLUE);
            g2.fillRect(0, 0, 50, 100);
            g2.dispose();

            ScreenshotCapture before = generator.createCapture(beforeImage, "Before");
            ScreenshotCapture after = generator.createCapture(afterImage, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "test-003");

            assertTrue(result.isPresent());
            DiffResult diff = result.get();
            // Should be approximately 50% different
            assertTrue(diff.differencePercentage() > 40.0);
            assertTrue(diff.differencePercentage() < 60.0);
        }

        @Test
        @DisplayName("should handle different sized images")
        void handlesDifferentSizedImages() throws IOException {
            BufferedImage beforeImage = createSolidColorImage(100, 100, Color.RED);
            BufferedImage afterImage = createSolidColorImage(150, 150, Color.RED);

            ScreenshotCapture before = generator.createCapture(beforeImage, "Before");
            ScreenshotCapture after = generator.createCapture(afterImage, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "test-004");

            // Should still generate a diff without error
            assertTrue(result.isPresent());
            DiffResult diff = result.get();
            // The diff should reflect size difference
            assertEquals(150 * 150, diff.totalPixels());
        }

        @Test
        @DisplayName("should return empty for null before image")
        void returnsEmptyForNullBefore() throws IOException {
            BufferedImage afterImage = createSolidColorImage(100, 100, Color.BLUE);
            ScreenshotCapture after = generator.createCapture(afterImage, "After");

            Optional<DiffResult> result = generator.generateDiff(null, after, "test-005");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for null after image")
        void returnsEmptyForNullAfter() throws IOException {
            BufferedImage beforeImage = createSolidColorImage(100, 100, Color.RED);
            ScreenshotCapture before = generator.createCapture(beforeImage, "Before");

            Optional<DiffResult> result = generator.generateDiff(before, null, "test-006");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should save images to output directory")
        void savesImagesToOutputDirectory() throws IOException {
            BufferedImage beforeImage = createSolidColorImage(50, 50, Color.RED);
            BufferedImage afterImage = createSolidColorImage(50, 50, Color.BLUE);

            ScreenshotCapture before = generator.createCapture(beforeImage, "Before");
            ScreenshotCapture after = generator.createCapture(afterImage, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "save-test");

            assertTrue(result.isPresent());
            DiffResult diff = result.get();

            // Verify paths are set
            assertNotNull(diff.beforeImagePath());
            assertNotNull(diff.afterImagePath());
            assertNotNull(diff.diffImagePath());

            // Verify files exist
            assertTrue(Path.of(diff.beforeImagePath()).toFile().exists());
            assertTrue(Path.of(diff.afterImagePath()).toFile().exists());
            assertTrue(Path.of(diff.diffImagePath()).toFile().exists());
        }
    }

    @Nested
    @DisplayName("HTML Generation Tests")
    class HtmlGenerationTests {

        @Test
        @DisplayName("should generate HTML for diff result")
        void generatesHtmlForDiff() throws IOException {
            BufferedImage beforeImage = createSolidColorImage(100, 100, Color.WHITE);
            BufferedImage afterImage = createSolidColorImage(100, 100, Color.BLACK);

            ScreenshotCapture before = generator.createCapture(beforeImage, "Before");
            ScreenshotCapture after = generator.createCapture(afterImage, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "html-test");
            assertTrue(result.isPresent());

            String html = generator.generateDiffHtml(result.get(), "Login Button Heal");

            assertNotNull(html);
            assertTrue(html.contains("Visual Comparison"));
            assertTrue(html.contains("Login Button Heal"));
            assertTrue(html.contains("Before"));
            assertTrue(html.contains("After"));
            assertTrue(html.contains("Difference"));
            assertTrue(html.contains("data:image/png;base64,"));
        }

        @Test
        @DisplayName("should return empty string for null result")
        void returnsEmptyForNullResult() {
            String html = generator.generateDiffHtml(null, "Test");

            assertEquals("", html);
        }

        @Test
        @DisplayName("should include CSS styles")
        void includesCssStyles() {
            String styles = generator.getDiffStyles();

            assertNotNull(styles);
            assertTrue(styles.contains("<style>"));
            assertTrue(styles.contains(".visual-diff"));
            assertTrue(styles.contains(".diff-images"));
            assertTrue(styles.contains(".diff-significant"));
            assertTrue(styles.contains(".diff-minimal"));
        }

        @Test
        @DisplayName("should show significant change indicator for large differences")
        void showsSignificantChangeIndicator() throws IOException {
            BufferedImage beforeImage = createSolidColorImage(100, 100, Color.WHITE);
            BufferedImage afterImage = createSolidColorImage(100, 100, Color.BLACK);

            ScreenshotCapture before = generator.createCapture(beforeImage, "Before");
            ScreenshotCapture after = generator.createCapture(afterImage, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "indicator-test");
            assertTrue(result.isPresent());

            String html = generator.generateDiffHtml(result.get(), "Test");

            assertTrue(html.contains("diff-significant"));
            assertTrue(html.contains("Significant Change"));
        }

        @Test
        @DisplayName("should show minimal change indicator for small differences")
        void showsMinimalChangeIndicator() throws IOException {
            BufferedImage image = createSolidColorImage(100, 100, Color.WHITE);

            ScreenshotCapture before = generator.createCapture(image, "Before");
            ScreenshotCapture after = generator.createCapture(image, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "minimal-test");
            assertTrue(result.isPresent());

            String html = generator.generateDiffHtml(result.get(), "Test");

            assertTrue(html.contains("diff-minimal"));
            assertTrue(html.contains("Minimal Change"));
        }
    }

    @Nested
    @DisplayName("DiffResult Tests")
    class DiffResultTests {

        @Test
        @DisplayName("should correctly identify identical images")
        void identifiesIdenticalImages() throws IOException {
            BufferedImage image = createSolidColorImage(100, 100, Color.GRAY);
            ScreenshotCapture capture = generator.createCapture(image, "Test");

            Optional<DiffResult> result = generator.generateDiff(capture, capture, "identical-test");

            assertTrue(result.isPresent());
            assertTrue(result.get().areImagesIdentical());
        }

        @Test
        @DisplayName("should correctly identify different images")
        void identifiesDifferentImages() throws IOException {
            BufferedImage image1 = createSolidColorImage(100, 100, Color.RED);
            BufferedImage image2 = createSolidColorImage(100, 100, Color.GREEN);

            ScreenshotCapture before = generator.createCapture(image1, "Before");
            ScreenshotCapture after = generator.createCapture(image2, "After");

            Optional<DiffResult> result = generator.generateDiff(before, after, "different-test");

            assertTrue(result.isPresent());
            assertFalse(result.get().areImagesIdentical());
        }
    }

    // Helper methods

    private BufferedImage createSolidColorImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
