package io.github.glaciousm.core.engine.calibration;

import io.github.glaciousm.core.feedback.FeedbackApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calibrates confidence scores based on historical outcomes.
 * Adjusts LLM-reported confidence to better reflect actual accuracy.
 */
public class ConfidenceCalibrator {

    private static final Logger logger = LoggerFactory.getLogger(ConfidenceCalibrator.class);

    // Number of buckets for confidence calibration (0.0-0.1, 0.1-0.2, etc.)
    private static final int NUM_BUCKETS = 10;

    // Minimum samples needed per bucket for reliable calibration
    private static final int MIN_SAMPLES_PER_BUCKET = 10;

    // Smoothing factor for exponential moving average
    private static final double EMA_ALPHA = 0.1;

    private final Map<Integer, CalibrationBucket> buckets = new ConcurrentHashMap<>();
    private final List<CalibrationSample> samples = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Double> modelAdjustments = new ConcurrentHashMap<>();

    public ConfidenceCalibrator() {
        // Initialize buckets
        for (int i = 0; i < NUM_BUCKETS; i++) {
            buckets.put(i, new CalibrationBucket());
        }
    }

    /**
     * Calibrate a raw confidence score based on historical accuracy.
     */
    public double calibrate(double rawConfidence) {
        return calibrate(rawConfidence, null);
    }

    /**
     * Calibrate a raw confidence score with model-specific adjustment.
     */
    public double calibrate(double rawConfidence, String model) {
        // Clamp to valid range
        rawConfidence = Math.max(0.0, Math.min(1.0, rawConfidence));

        // Get bucket for this confidence level
        int bucketIndex = getBucketIndex(rawConfidence);
        CalibrationBucket bucket = buckets.get(bucketIndex);

        double calibrated = rawConfidence;

        // Apply bucket-based calibration if we have enough samples
        if (bucket.getSampleCount() >= MIN_SAMPLES_PER_BUCKET) {
            calibrated = bucket.getCalibratedConfidence();
        }

        // Apply model-specific adjustment if available
        if (model != null && modelAdjustments.containsKey(model)) {
            double adjustment = modelAdjustments.get(model);
            calibrated = calibrated * adjustment;
        }

        // Clamp result
        return Math.max(0.0, Math.min(1.0, calibrated));
    }

    /**
     * Record an outcome for calibration.
     */
    public void recordOutcome(double reportedConfidence, boolean wasCorrect) {
        recordOutcome(reportedConfidence, wasCorrect, null);
    }

    /**
     * Record an outcome with model information.
     */
    public void recordOutcome(double reportedConfidence, boolean wasCorrect, String model) {
        // Clamp confidence
        reportedConfidence = Math.max(0.0, Math.min(1.0, reportedConfidence));

        // Create sample
        CalibrationSample sample = new CalibrationSample(
                reportedConfidence,
                wasCorrect,
                model,
                Instant.now()
        );
        samples.add(sample);

        // Update bucket
        int bucketIndex = getBucketIndex(reportedConfidence);
        buckets.get(bucketIndex).addOutcome(wasCorrect);

        // Update model-specific adjustment
        if (model != null) {
            updateModelAdjustment(model, reportedConfidence, wasCorrect);
        }

        logger.debug("Recorded calibration outcome: confidence={}, correct={}, model={}",
                reportedConfidence, wasCorrect, model);
    }

    /**
     * Get calibration statistics.
     */
    public CalibrationStats getStats() {
        int totalSamples = samples.size();
        int correctPredictions = 0;
        double totalConfidence = 0;

        for (CalibrationSample sample : samples) {
            totalConfidence += sample.reportedConfidence();
            if (sample.wasCorrect()) {
                correctPredictions++;
            }
        }

        double averageConfidence = totalSamples > 0 ? totalConfidence / totalSamples : 0;
        double actualAccuracy = totalSamples > 0 ? (double) correctPredictions / totalSamples : 0;
        double calibrationError = Math.abs(averageConfidence - actualAccuracy);

        // Calculate per-bucket reliability
        Map<String, BucketStats> bucketStats = new LinkedHashMap<>();
        for (int i = 0; i < NUM_BUCKETS; i++) {
            CalibrationBucket bucket = buckets.get(i);
            String range = String.format("%.1f-%.1f", i * 0.1, (i + 1) * 0.1);
            bucketStats.put(range, new BucketStats(
                    bucket.getSampleCount(),
                    bucket.getSuccessRate(),
                    bucket.getCalibratedConfidence()
            ));
        }

        return new CalibrationStats(
                totalSamples,
                averageConfidence,
                actualAccuracy,
                calibrationError,
                isWellCalibrated(),
                bucketStats
        );
    }

    /**
     * Check if the system is well-calibrated.
     */
    public boolean isWellCalibrated() {
        // A system is well-calibrated if predicted probabilities match actual frequencies
        double totalError = 0;
        int evaluatedBuckets = 0;

        for (int i = 0; i < NUM_BUCKETS; i++) {
            CalibrationBucket bucket = buckets.get(i);
            if (bucket.getSampleCount() >= MIN_SAMPLES_PER_BUCKET) {
                double midpoint = (i * 0.1) + 0.05;
                double actualRate = bucket.getSuccessRate();
                totalError += Math.abs(midpoint - actualRate);
                evaluatedBuckets++;
            }
        }

        if (evaluatedBuckets == 0) {
            return false; // Not enough data
        }

        double averageError = totalError / evaluatedBuckets;
        return averageError < 0.1; // Well-calibrated if avg error < 10%
    }

    /**
     * Get the recommended minimum confidence threshold based on calibration.
     */
    public double getRecommendedThreshold(double targetAccuracy) {
        // Find the bucket where accuracy meets target
        for (int i = NUM_BUCKETS - 1; i >= 0; i--) {
            CalibrationBucket bucket = buckets.get(i);
            if (bucket.getSampleCount() >= MIN_SAMPLES_PER_BUCKET) {
                if (bucket.getSuccessRate() >= targetAccuracy) {
                    return i * 0.1; // Return lower bound of bucket
                }
            }
        }
        return 0.9; // Default to high threshold if no data
    }

    /**
     * Get calibration data for visualization.
     */
    public List<CalibrationPoint> getCalibrationCurve() {
        List<CalibrationPoint> points = new ArrayList<>();

        for (int i = 0; i < NUM_BUCKETS; i++) {
            CalibrationBucket bucket = buckets.get(i);
            if (bucket.getSampleCount() > 0) {
                double midpoint = (i * 0.1) + 0.05;
                points.add(new CalibrationPoint(
                        midpoint,
                        bucket.getSuccessRate(),
                        bucket.getSampleCount()
                ));
            }
        }

        return points;
    }

    /**
     * Clear old samples to prevent memory growth.
     */
    public int clearOldSamples(Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        int removed = 0;

        Iterator<CalibrationSample> it = samples.iterator();
        while (it.hasNext()) {
            if (it.next().timestamp().isBefore(cutoff)) {
                it.remove();
                removed++;
            }
        }

        // Recalibrate buckets
        recalibrateBuckets();

        logger.info("Cleared {} old calibration samples", removed);
        return removed;
    }

    /**
     * Reset all calibration data.
     */
    public void reset() {
        samples.clear();
        modelAdjustments.clear();
        for (CalibrationBucket bucket : buckets.values()) {
            bucket.reset();
        }
        logger.info("Calibration data reset");
    }

    // Private helpers

    private int getBucketIndex(double confidence) {
        int index = (int) (confidence * NUM_BUCKETS);
        return Math.min(index, NUM_BUCKETS - 1);
    }

    private void updateModelAdjustment(String model, double confidence, boolean wasCorrect) {
        double current = modelAdjustments.getOrDefault(model, 1.0);

        // If prediction was correct at this confidence, slight increase
        // If incorrect, slight decrease
        double adjustment = wasCorrect ? 1.01 : 0.99;

        // Apply exponential moving average
        double newValue = (EMA_ALPHA * adjustment) + ((1 - EMA_ALPHA) * current);

        // Clamp to reasonable range
        newValue = Math.max(0.8, Math.min(1.2, newValue));

        modelAdjustments.put(model, newValue);
    }

    private void recalibrateBuckets() {
        // Reset buckets
        for (CalibrationBucket bucket : buckets.values()) {
            bucket.reset();
        }

        // Rebuild from samples
        for (CalibrationSample sample : samples) {
            int bucketIndex = getBucketIndex(sample.reportedConfidence());
            buckets.get(bucketIndex).addOutcome(sample.wasCorrect());
        }
    }

    // Inner types

    private static class CalibrationBucket {
        private int successes = 0;
        private int failures = 0;

        synchronized void addOutcome(boolean success) {
            if (success) {
                successes++;
            } else {
                failures++;
            }
        }

        synchronized int getSampleCount() {
            return successes + failures;
        }

        synchronized double getSuccessRate() {
            int total = successes + failures;
            return total > 0 ? (double) successes / total : 0;
        }

        synchronized double getCalibratedConfidence() {
            return getSuccessRate();
        }

        synchronized void reset() {
            successes = 0;
            failures = 0;
        }
    }

    public record CalibrationSample(
            double reportedConfidence,
            boolean wasCorrect,
            String model,
            Instant timestamp
    ) {}

    public record CalibrationStats(
            int totalSamples,
            double averageConfidence,
            double actualAccuracy,
            double calibrationError,
            boolean wellCalibrated,
            Map<String, BucketStats> bucketStats
    ) {}

    public record BucketStats(
            int sampleCount,
            double actualAccuracy,
            double calibratedConfidence
    ) {}

    public record CalibrationPoint(
            double predictedProbability,
            double actualFrequency,
            int sampleCount
    ) {}
}
