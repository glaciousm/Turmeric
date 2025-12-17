package com.intenthealer.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;

/**
 * Generates visual diffs between before and after healing screenshots.
 * Supports highlighting differences and embedding comparisons in reports.
 */
public class VisualDiffGenerator {

    private static final Logger logger = LoggerFactory.getLogger(VisualDiffGenerator.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Threshold for considering pixels different (0-255 range).
     * Lower values are more sensitive to changes.
     */
    private static final int DIFFERENCE_THRESHOLD = 30;

    /**
     * Color to use when highlighting differences.
     */
    private static final Color HIGHLIGHT_COLOR = new Color(255, 0, 0, 128); // Semi-transparent red

    /**
     * Result of a visual diff operation.
     */
    public record DiffResult(
            String beforeImagePath,
            String afterImagePath,
            String diffImagePath,
            String beforeBase64,
            String afterBase64,
            String diffBase64,
            double differencePercentage,
            int differentPixelCount,
            int totalPixels,
            boolean hasSignificantDifference
    ) {
        /**
         * Checks if the before and after images are effectively identical.
         */
        public boolean areImagesIdentical() {
            return differencePercentage < 0.1; // Less than 0.1% different
        }
    }

    /**
     * Captures data for visual comparison.
     */
    public record ScreenshotCapture(
            byte[] imageData,
            String format,
            Instant capturedAt,
            String description
    ) {
        /**
         * Converts the image data to a Base64 string for embedding in HTML.
         */
        public String toBase64() {
            return Base64.getEncoder().encodeToString(imageData);
        }

        /**
         * Returns the data URI for embedding in HTML img tags.
         */
        public String toDataUri() {
            return "data:image/" + format + ";base64," + toBase64();
        }
    }

    private final Path outputDirectory;

    public VisualDiffGenerator() {
        this(Path.of(System.getProperty("user.dir"), "target", "healer-screenshots"));
    }

    public VisualDiffGenerator(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            logger.warn("Failed to create screenshot directory: {}", outputDirectory, e);
        }
    }

    /**
     * Creates a ScreenshotCapture from raw image bytes.
     */
    public ScreenshotCapture createCapture(byte[] imageData, String description) {
        return new ScreenshotCapture(imageData, "png", Instant.now(), description);
    }

    /**
     * Creates a ScreenshotCapture from a BufferedImage.
     */
    public ScreenshotCapture createCapture(BufferedImage image, String description) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return new ScreenshotCapture(baos.toByteArray(), "png", Instant.now(), description);
    }

    /**
     * Generates a visual diff between two screenshots.
     *
     * @param before The screenshot before healing
     * @param after  The screenshot after healing
     * @param healId Unique identifier for the heal event
     * @return DiffResult containing paths and analysis
     */
    public Optional<DiffResult> generateDiff(ScreenshotCapture before, ScreenshotCapture after, String healId) {
        if (before == null || after == null || before.imageData() == null || after.imageData() == null) {
            logger.warn("Cannot generate diff: one or both screenshots are null");
            return Optional.empty();
        }

        try {
            BufferedImage beforeImage = ImageIO.read(new ByteArrayInputStream(before.imageData()));
            BufferedImage afterImage = ImageIO.read(new ByteArrayInputStream(after.imageData()));

            if (beforeImage == null || afterImage == null) {
                logger.warn("Failed to decode screenshot images");
                return Optional.empty();
            }

            // Generate the diff image
            DiffAnalysis analysis = analyzeDifference(beforeImage, afterImage);

            // Save images to files
            String timestamp = TIMESTAMP_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()));
            String beforePath = saveImage(beforeImage, healId + "_before_" + timestamp + ".png");
            String afterPath = saveImage(afterImage, healId + "_after_" + timestamp + ".png");
            String diffPath = saveImage(analysis.diffImage, healId + "_diff_" + timestamp + ".png");

            // Convert to base64 for embedding
            String beforeBase64 = before.toDataUri();
            String afterBase64 = after.toDataUri();
            String diffBase64 = imageToDataUri(analysis.diffImage);

            return Optional.of(new DiffResult(
                    beforePath,
                    afterPath,
                    diffPath,
                    beforeBase64,
                    afterBase64,
                    diffBase64,
                    analysis.differencePercentage,
                    analysis.differentPixelCount,
                    analysis.totalPixels,
                    analysis.differencePercentage > 1.0 // More than 1% is significant
            ));

        } catch (Exception e) {
            logger.error("Failed to generate visual diff for heal {}: {}", healId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Generates HTML for displaying a visual diff in a report.
     */
    public String generateDiffHtml(DiffResult result, String healDescription) {
        if (result == null) {
            return "";
        }

        return """
                <div class="visual-diff">
                    <h4>Visual Comparison: %s</h4>
                    <div class="diff-stats">
                        <span class="diff-stat">Difference: %.2f%%</span>
                        <span class="diff-stat">Changed Pixels: %,d / %,d</span>
                        <span class="diff-indicator %s">%s</span>
                    </div>
                    <div class="diff-images">
                        <div class="diff-image-container">
                            <div class="diff-label">Before</div>
                            <img src="%s" alt="Before healing" class="diff-image" />
                        </div>
                        <div class="diff-image-container">
                            <div class="diff-label">After</div>
                            <img src="%s" alt="After healing" class="diff-image" />
                        </div>
                        <div class="diff-image-container">
                            <div class="diff-label">Difference</div>
                            <img src="%s" alt="Visual difference" class="diff-image" />
                        </div>
                    </div>
                </div>
                """.formatted(
                escapeHtml(healDescription),
                result.differencePercentage(),
                result.differentPixelCount(),
                result.totalPixels(),
                result.hasSignificantDifference() ? "diff-significant" : "diff-minimal",
                result.hasSignificantDifference() ? "Significant Change" : "Minimal Change",
                result.beforeBase64(),
                result.afterBase64(),
                result.diffBase64()
        );
    }

    /**
     * Returns CSS styles for visual diff display.
     */
    public String getDiffStyles() {
        return """
                <style>
                    .visual-diff {
                        background: white;
                        padding: 15px;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        margin: 15px 0;
                    }
                    .visual-diff h4 {
                        margin: 0 0 10px 0;
                        color: #333;
                    }
                    .diff-stats {
                        display: flex;
                        gap: 15px;
                        margin-bottom: 15px;
                        align-items: center;
                    }
                    .diff-stat {
                        font-size: 0.9em;
                        color: #666;
                    }
                    .diff-indicator {
                        padding: 4px 8px;
                        border-radius: 4px;
                        font-size: 0.85em;
                        font-weight: bold;
                    }
                    .diff-significant {
                        background: #FFEBEE;
                        color: #C62828;
                    }
                    .diff-minimal {
                        background: #E8F5E9;
                        color: #2E7D32;
                    }
                    .diff-images {
                        display: grid;
                        grid-template-columns: repeat(3, 1fr);
                        gap: 15px;
                    }
                    .diff-image-container {
                        text-align: center;
                    }
                    .diff-label {
                        font-weight: bold;
                        margin-bottom: 5px;
                        color: #555;
                    }
                    .diff-image {
                        max-width: 100%;
                        height: auto;
                        border: 1px solid #ddd;
                        border-radius: 4px;
                    }
                    @media (max-width: 768px) {
                        .diff-images {
                            grid-template-columns: 1fr;
                        }
                    }
                </style>
                """;
    }

    /**
     * Internal class to hold diff analysis results.
     */
    private record DiffAnalysis(
            BufferedImage diffImage,
            int differentPixelCount,
            int totalPixels,
            double differencePercentage
    ) {}

    /**
     * Analyzes the difference between two images and generates a diff image.
     */
    private DiffAnalysis analyzeDifference(BufferedImage before, BufferedImage after) {
        int width = Math.max(before.getWidth(), after.getWidth());
        int height = Math.max(before.getHeight(), after.getHeight());
        int totalPixels = width * height;
        int differentPixels = 0;

        BufferedImage diffImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = diffImage.createGraphics();

        // Draw the "after" image as the base
        g.drawImage(after, 0, 0, null);

        // Overlay differences in red
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int beforeRgb = getPixelSafe(before, x, y);
                int afterRgb = getPixelSafe(after, x, y);

                if (arePixelsDifferent(beforeRgb, afterRgb)) {
                    differentPixels++;
                    // Highlight the difference
                    int currentColor = diffImage.getRGB(x, y);
                    int highlightedColor = blendColors(currentColor, HIGHLIGHT_COLOR.getRGB());
                    diffImage.setRGB(x, y, highlightedColor);
                }
            }
        }

        g.dispose();

        double percentage = (differentPixels * 100.0) / totalPixels;

        return new DiffAnalysis(diffImage, differentPixels, totalPixels, percentage);
    }

    /**
     * Gets a pixel value safely, returning black for out-of-bounds.
     */
    private int getPixelSafe(BufferedImage image, int x, int y) {
        if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
            return image.getRGB(x, y);
        }
        return Color.BLACK.getRGB();
    }

    /**
     * Checks if two pixels are considered different.
     */
    private boolean arePixelsDifferent(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;

        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;

        int diff = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
        return diff > DIFFERENCE_THRESHOLD * 3; // Sum of all channels
    }

    /**
     * Blends two colors with alpha compositing.
     */
    private int blendColors(int base, int overlay) {
        int baseR = (base >> 16) & 0xFF;
        int baseG = (base >> 8) & 0xFF;
        int baseB = base & 0xFF;

        int overlayR = (overlay >> 16) & 0xFF;
        int overlayG = (overlay >> 8) & 0xFF;
        int overlayB = overlay & 0xFF;
        int overlayA = (overlay >> 24) & 0xFF;

        float alpha = overlayA / 255.0f;
        int r = (int) (baseR * (1 - alpha) + overlayR * alpha);
        int g = (int) (baseG * (1 - alpha) + overlayG * alpha);
        int b = (int) (baseB * (1 - alpha) + overlayB * alpha);

        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Saves an image to the output directory.
     */
    private String saveImage(BufferedImage image, String filename) throws IOException {
        Path filePath = outputDirectory.resolve(filename);
        ImageIO.write(image, "png", filePath.toFile());
        logger.debug("Saved screenshot: {}", filePath);
        return filePath.toString();
    }

    /**
     * Converts an image to a data URI.
     */
    private String imageToDataUri(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return "data:image/png;base64," + base64;
    }

    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Gets the output directory for screenshots.
     */
    public Path getOutputDirectory() {
        return outputDirectory;
    }
}
