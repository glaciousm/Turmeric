package io.github.glaciousm.core.engine.visual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Compares screenshots to detect visual differences.
 *
 * Used for:
 * - Validating heal outcomes visually
 * - Detecting unexpected UI changes
 * - Identifying visual regression after heals
 */
public class ScreenshotComparator {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotComparator.class);

    // Default configuration
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.95;
    private static final int DEFAULT_BLOCK_SIZE = 10;
    private static final Color DIFF_HIGHLIGHT_COLOR = new Color(255, 0, 0, 128);

    private final double similarityThreshold;
    private final int blockSize;
    private final boolean generateDiffImage;

    public ScreenshotComparator() {
        this(DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_BLOCK_SIZE, true);
    }

    public ScreenshotComparator(double similarityThreshold, int blockSize, boolean generateDiffImage) {
        this.similarityThreshold = similarityThreshold;
        this.blockSize = blockSize;
        this.generateDiffImage = generateDiffImage;
    }

    /**
     * Compare two screenshots.
     */
    public ComparisonResult compare(byte[] baseline, byte[] actual) throws IOException {
        BufferedImage baselineImg = ImageIO.read(new ByteArrayInputStream(baseline));
        BufferedImage actualImg = ImageIO.read(new ByteArrayInputStream(actual));

        return compare(baselineImg, actualImg);
    }

    /**
     * Compare two screenshots from files.
     */
    public ComparisonResult compare(Path baselinePath, Path actualPath) throws IOException {
        BufferedImage baselineImg = ImageIO.read(baselinePath.toFile());
        BufferedImage actualImg = ImageIO.read(actualPath.toFile());

        return compare(baselineImg, actualImg);
    }

    /**
     * Compare two BufferedImages.
     */
    public ComparisonResult compare(BufferedImage baseline, BufferedImage actual) {
        if (baseline == null || actual == null) {
            return ComparisonResult.error("One or both images are null");
        }

        // Check dimensions
        boolean sameDimensions = baseline.getWidth() == actual.getWidth() &&
                                 baseline.getHeight() == actual.getHeight();

        if (!sameDimensions) {
            return ComparisonResult.dimensionMismatch(
                    baseline.getWidth(), baseline.getHeight(),
                    actual.getWidth(), actual.getHeight()
            );
        }

        // Perform pixel comparison
        int width = baseline.getWidth();
        int height = baseline.getHeight();
        long totalPixels = (long) width * height;
        long matchingPixels = 0;
        List<DifferenceRegion> diffRegions = new ArrayList<>();

        // Create diff image if requested
        BufferedImage diffImage = generateDiffImage ?
                new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB) : null;

        if (diffImage != null) {
            Graphics2D g = diffImage.createGraphics();
            g.drawImage(actual, 0, 0, null);
            g.dispose();
        }

        // Block-based comparison
        int blocksX = (width + blockSize - 1) / blockSize;
        int blocksY = (height + blockSize - 1) / blockSize;
        boolean[][] diffBlocks = new boolean[blocksX][blocksY];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int baselineRgb = baseline.getRGB(x, y);
                int actualRgb = actual.getRGB(x, y);

                if (pixelsSimilar(baselineRgb, actualRgb)) {
                    matchingPixels++;
                } else {
                    int blockX = x / blockSize;
                    int blockY = y / blockSize;
                    diffBlocks[blockX][blockY] = true;
                }
            }
        }

        // Find diff regions from blocks
        boolean[][] visited = new boolean[blocksX][blocksY];
        for (int bx = 0; bx < blocksX; bx++) {
            for (int by = 0; by < blocksY; by++) {
                if (diffBlocks[bx][by] && !visited[bx][by]) {
                    DifferenceRegion region = floodFillRegion(diffBlocks, visited, bx, by, blocksX, blocksY);
                    diffRegions.add(region);

                    // Highlight in diff image
                    if (diffImage != null) {
                        highlightRegion(diffImage, region);
                    }
                }
            }
        }

        double similarity = (double) matchingPixels / totalPixels;
        boolean passed = similarity >= similarityThreshold && diffRegions.isEmpty();

        byte[] diffImageBytes = null;
        if (diffImage != null && !diffRegions.isEmpty()) {
            diffImageBytes = imageToBytes(diffImage);
        }

        logger.debug("Screenshot comparison: similarity={}, diffRegions={}, passed={}",
                String.format("%.2f%%", similarity * 100), diffRegions.size(), passed);

        return new ComparisonResult(
                passed,
                similarity,
                diffRegions,
                diffImageBytes,
                width,
                height,
                null
        );
    }

    /**
     * Compare with region of interest only.
     */
    public ComparisonResult compareRegion(BufferedImage baseline, BufferedImage actual, Rectangle region) {
        if (baseline == null || actual == null) {
            return ComparisonResult.error("One or both images are null");
        }

        // Validate region bounds
        if (region.x < 0 || region.y < 0 ||
            region.x + region.width > baseline.getWidth() ||
            region.y + region.height > baseline.getHeight() ||
            region.x + region.width > actual.getWidth() ||
            region.y + region.height > actual.getHeight()) {
            return ComparisonResult.error("Region of interest out of bounds");
        }

        // Extract subimages
        BufferedImage baselineRegion = baseline.getSubimage(
                region.x, region.y, region.width, region.height);
        BufferedImage actualRegion = actual.getSubimage(
                region.x, region.y, region.width, region.height);

        return compare(baselineRegion, actualRegion);
    }

    /**
     * Compare ignoring specified regions.
     */
    public ComparisonResult compareIgnoringRegions(
            BufferedImage baseline,
            BufferedImage actual,
            List<Rectangle> ignoreRegions) {

        if (baseline == null || actual == null) {
            return ComparisonResult.error("One or both images are null");
        }

        // Create masks for ignored regions
        BufferedImage baselineMasked = copyImage(baseline);
        BufferedImage actualMasked = copyImage(actual);

        for (Rectangle region : ignoreRegions) {
            maskRegion(baselineMasked, region);
            maskRegion(actualMasked, region);
        }

        return compare(baselineMasked, actualMasked);
    }

    private boolean pixelsSimilar(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;

        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;

        // Allow small differences for anti-aliasing
        int threshold = 10;
        return Math.abs(r1 - r2) <= threshold &&
               Math.abs(g1 - g2) <= threshold &&
               Math.abs(b1 - b2) <= threshold;
    }

    private DifferenceRegion floodFillRegion(boolean[][] blocks, boolean[][] visited,
                                              int startX, int startY, int width, int height) {
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        int blockCount = 0;

        // Simple BFS flood fill
        List<int[]> queue = new ArrayList<>();
        queue.add(new int[]{startX, startY});
        visited[startX][startY] = true;

        while (!queue.isEmpty()) {
            int[] pos = queue.remove(0);
            int x = pos[0];
            int y = pos[1];

            blockCount++;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

            // Check neighbors
            int[][] neighbors = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] n : neighbors) {
                int nx = x + n[0];
                int ny = y + n[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height &&
                    blocks[nx][ny] && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        return new DifferenceRegion(
                minX * blockSize,
                minY * blockSize,
                (maxX - minX + 1) * blockSize,
                (maxY - minY + 1) * blockSize,
                blockCount * blockSize * blockSize
        );
    }

    private void highlightRegion(BufferedImage image, DifferenceRegion region) {
        Graphics2D g = image.createGraphics();
        g.setColor(DIFF_HIGHLIGHT_COLOR);
        g.fillRect(region.x(), region.y(), region.width(), region.height());
        g.setColor(Color.RED);
        g.setStroke(new BasicStroke(2));
        g.drawRect(region.x(), region.y(), region.width(), region.height());
        g.dispose();
    }

    private BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(), source.getHeight(), source.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    private void maskRegion(BufferedImage image, Rectangle region) {
        Graphics2D g = image.createGraphics();
        g.setColor(Color.MAGENTA); // Distinctive mask color
        g.fillRect(region.x, region.y, region.width, region.height);
        g.dispose();
    }

    private byte[] imageToBytes(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            logger.warn("Failed to convert diff image to bytes", e);
            return null;
        }
    }

    /**
     * Result of screenshot comparison.
     */
    public record ComparisonResult(
            boolean passed,
            double similarity,
            List<DifferenceRegion> differenceRegions,
            byte[] diffImage,
            int width,
            int height,
            String errorMessage
    ) {
        public static ComparisonResult error(String message) {
            return new ComparisonResult(false, 0, List.of(), null, 0, 0, message);
        }

        public static ComparisonResult dimensionMismatch(int w1, int h1, int w2, int h2) {
            String msg = String.format("Dimension mismatch: %dx%d vs %dx%d", w1, h1, w2, h2);
            return new ComparisonResult(false, 0, List.of(), null, 0, 0, msg);
        }

        public boolean hasError() {
            return errorMessage != null;
        }

        public String getDiffImageBase64() {
            return diffImage != null ? Base64.getEncoder().encodeToString(diffImage) : null;
        }

        public int getDifferenceCount() {
            return differenceRegions.size();
        }

        public long getTotalDifferencePixels() {
            return differenceRegions.stream()
                    .mapToLong(DifferenceRegion::pixelCount)
                    .sum();
        }
    }

    /**
     * A region of difference between screenshots.
     */
    public record DifferenceRegion(
            int x,
            int y,
            int width,
            int height,
            long pixelCount
    ) {
        public Rectangle toRectangle() {
            return new Rectangle(x, y, width, height);
        }
    }

    /**
     * Builder for customizing comparator settings.
     */
    public static class Builder {
        private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
        private int blockSize = DEFAULT_BLOCK_SIZE;
        private boolean generateDiffImage = true;

        public Builder similarityThreshold(double threshold) {
            this.similarityThreshold = threshold;
            return this;
        }

        public Builder blockSize(int size) {
            this.blockSize = size;
            return this;
        }

        public Builder generateDiffImage(boolean generate) {
            this.generateDiffImage = generate;
            return this;
        }

        public ScreenshotComparator build() {
            return new ScreenshotComparator(similarityThreshold, blockSize, generateDiffImage);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
