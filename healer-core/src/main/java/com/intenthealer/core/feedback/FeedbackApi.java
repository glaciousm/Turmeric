package com.intenthealer.core.feedback;

import com.intenthealer.core.engine.blacklist.HealBlacklist;
import com.intenthealer.core.engine.trust.TrustLevelManager;
import com.intenthealer.core.engine.blacklist.BlacklistEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API for submitting feedback on heal decisions.
 * Used to correct false heals, improve confidence calibration,
 * and train the system for better future decisions.
 */
public class FeedbackApi {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackApi.class);

    private final HealBlacklist blacklist;
    private final TrustLevelManager trustManager;
    private final List<FeedbackListener> listeners = new ArrayList<>();
    private final Map<String, FeedbackRecord> feedbackHistory = new ConcurrentHashMap<>();

    public FeedbackApi(HealBlacklist blacklist, TrustLevelManager trustManager) {
        this.blacklist = blacklist;
        this.trustManager = trustManager;
    }

    /**
     * Submit feedback that a heal was correct.
     */
    public FeedbackResult submitPositive(String healId, String comment) {
        FeedbackRecord record = new FeedbackRecord();
        record.setHealId(healId);
        record.setFeedbackType(FeedbackType.POSITIVE);
        record.setTimestamp(Instant.now());
        record.setComment(comment);

        feedbackHistory.put(healId, record);

        // Reward trust level
        if (trustManager != null) {
            trustManager.recordSuccess();
        }

        // Notify listeners
        notifyListeners(record);

        logger.info("Positive feedback recorded for heal: {}", healId);
        return FeedbackResult.success("Positive feedback recorded");
    }

    /**
     * Submit feedback that a heal was incorrect (false heal).
     */
    public FeedbackResult submitNegative(
            String healId,
            String reason,
            String correctLocator) {

        FeedbackRecord record = new FeedbackRecord();
        record.setHealId(healId);
        record.setFeedbackType(FeedbackType.NEGATIVE);
        record.setTimestamp(Instant.now());
        record.setComment(reason);
        record.setCorrectLocator(correctLocator);

        feedbackHistory.put(healId, record);

        // Penalize trust level
        if (trustManager != null) {
            trustManager.recordFailure();
        }

        // Notify listeners
        notifyListeners(record);

        logger.warn("Negative feedback recorded for heal: {} - {}", healId, reason);
        return FeedbackResult.success("Negative feedback recorded - trust level adjusted");
    }

    /**
     * Submit a correction with the correct locator.
     */
    public FeedbackResult submitCorrection(HealCorrection correction) {
        if (correction == null) {
            return FeedbackResult.error("Correction cannot be null");
        }

        FeedbackRecord record = new FeedbackRecord();
        record.setHealId(correction.getHealId());
        record.setFeedbackType(FeedbackType.CORRECTION);
        record.setTimestamp(Instant.now());
        record.setOriginalLocator(correction.getOriginalLocator());
        record.setHealedLocator(correction.getHealedLocator());
        record.setCorrectLocator(correction.getCorrectLocator());
        record.setComment(correction.getReason());

        feedbackHistory.put(correction.getHealId(), record);

        // If healed locator was wrong, blacklist it
        if (correction.shouldBlacklist() && blacklist != null) {
            BlacklistEntry entry = BlacklistEntry.builder()
                    .originalLocator("CSS", correction.getOriginalLocator())
                    .healedLocator("CSS", correction.getHealedLocator())
                    .reason(correction.getReason())
                    .build();
            blacklist.add(entry);
        }

        // Adjust trust
        if (trustManager != null) {
            trustManager.recordFailure();
        }

        notifyListeners(record);

        logger.info("Correction submitted for heal: {} -> correct: {}",
                correction.getHealId(), correction.getCorrectLocator());
        return FeedbackResult.success("Correction recorded");
    }

    /**
     * Blacklist a specific locator pair.
     */
    public FeedbackResult blacklistLocator(
            String originalLocator,
            String badLocator,
            String reason) {

        if (blacklist == null) {
            return FeedbackResult.error("Blacklist not configured");
        }

        BlacklistEntry entry = BlacklistEntry.builder()
                .originalLocator("CSS", originalLocator)
                .healedLocator("CSS", badLocator)
                .reason(reason)
                .build();
        blacklist.add(entry);

        logger.info("Locator blacklisted: {} -> {} ({})", originalLocator, badLocator, reason);
        return FeedbackResult.success("Locator blacklisted");
    }

    /**
     * Remove a locator from blacklist.
     */
    public FeedbackResult unblacklistLocator(String locator) {
        if (blacklist == null) {
            return FeedbackResult.error("Blacklist not configured");
        }

        boolean removed = blacklist.remove(locator);
        if (removed) {
            logger.info("Locator removed from blacklist: {}", locator);
            return FeedbackResult.success("Locator removed from blacklist");
        } else {
            return FeedbackResult.error("Locator not found in blacklist");
        }
    }

    /**
     * Report a false negative (heal should have happened but didn't).
     */
    public FeedbackResult reportFalseNegative(
            String stepText,
            String originalLocator,
            String suggestedLocator,
            String comment) {

        FeedbackRecord record = new FeedbackRecord();
        record.setHealId(UUID.randomUUID().toString());
        record.setFeedbackType(FeedbackType.FALSE_NEGATIVE);
        record.setTimestamp(Instant.now());
        record.setOriginalLocator(originalLocator);
        record.setCorrectLocator(suggestedLocator);
        record.setComment(comment);
        record.setStepText(stepText);

        feedbackHistory.put(record.getHealId(), record);
        notifyListeners(record);

        logger.info("False negative reported for step: {}", stepText);
        return FeedbackResult.success("False negative reported");
    }

    /**
     * Batch submit multiple feedback records.
     */
    public List<FeedbackResult> submitBatch(List<FeedbackRecord> records) {
        List<FeedbackResult> results = new ArrayList<>();

        for (FeedbackRecord record : records) {
            record.setTimestamp(Instant.now());
            feedbackHistory.put(record.getHealId(), record);

            switch (record.getFeedbackType()) {
                case POSITIVE -> {
                    if (trustManager != null) trustManager.recordSuccess();
                }
                case NEGATIVE, CORRECTION -> {
                    if (trustManager != null) trustManager.recordFailure();
                }
            }

            notifyListeners(record);
            results.add(FeedbackResult.success("Recorded: " + record.getHealId()));
        }

        logger.info("Batch feedback submitted: {} records", records.size());
        return results;
    }

    /**
     * Get feedback statistics.
     */
    public FeedbackStats getStats() {
        int positive = 0;
        int negative = 0;
        int corrections = 0;
        int falseNegatives = 0;

        for (FeedbackRecord record : feedbackHistory.values()) {
            switch (record.getFeedbackType()) {
                case POSITIVE -> positive++;
                case NEGATIVE -> negative++;
                case CORRECTION -> corrections++;
                case FALSE_NEGATIVE -> falseNegatives++;
            }
        }

        return new FeedbackStats(
                feedbackHistory.size(),
                positive,
                negative,
                corrections,
                falseNegatives
        );
    }

    /**
     * Get recent feedback records.
     */
    public List<FeedbackRecord> getRecentFeedback(int limit) {
        return feedbackHistory.values().stream()
                .sorted(Comparator.comparing(FeedbackRecord::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Get feedback for a specific heal.
     */
    public Optional<FeedbackRecord> getFeedback(String healId) {
        return Optional.ofNullable(feedbackHistory.get(healId));
    }

    /**
     * Register a feedback listener.
     */
    public void addListener(FeedbackListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a feedback listener.
     */
    public void removeListener(FeedbackListener listener) {
        listeners.remove(listener);
    }

    /**
     * Export feedback data for analysis.
     */
    public List<FeedbackRecord> exportFeedback(Instant since) {
        return feedbackHistory.values().stream()
                .filter(r -> r.getTimestamp().isAfter(since))
                .sorted(Comparator.comparing(FeedbackRecord::getTimestamp))
                .toList();
    }

    /**
     * Clear old feedback records.
     */
    public int clearOldFeedback(Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        int removed = 0;

        Iterator<Map.Entry<String, FeedbackRecord>> it = feedbackHistory.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().getTimestamp().isBefore(cutoff)) {
                it.remove();
                removed++;
            }
        }

        logger.info("Cleared {} old feedback records", removed);
        return removed;
    }

    private void notifyListeners(FeedbackRecord record) {
        for (FeedbackListener listener : listeners) {
            try {
                listener.onFeedback(record);
            } catch (Exception e) {
                logger.warn("Feedback listener error: {}", e.getMessage());
            }
        }
    }

    // Inner types

    public enum FeedbackType {
        POSITIVE,
        NEGATIVE,
        CORRECTION,
        FALSE_NEGATIVE
    }

    public static class FeedbackRecord {
        private String healId;
        private FeedbackType feedbackType;
        private Instant timestamp;
        private String originalLocator;
        private String healedLocator;
        private String correctLocator;
        private String comment;
        private String stepText;

        // Getters and Setters
        public String getHealId() { return healId; }
        public void setHealId(String healId) { this.healId = healId; }

        public FeedbackType getFeedbackType() { return feedbackType; }
        public void setFeedbackType(FeedbackType feedbackType) { this.feedbackType = feedbackType; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public String getOriginalLocator() { return originalLocator; }
        public void setOriginalLocator(String originalLocator) { this.originalLocator = originalLocator; }

        public String getHealedLocator() { return healedLocator; }
        public void setHealedLocator(String healedLocator) { this.healedLocator = healedLocator; }

        public String getCorrectLocator() { return correctLocator; }
        public void setCorrectLocator(String correctLocator) { this.correctLocator = correctLocator; }

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }

        public String getStepText() { return stepText; }
        public void setStepText(String stepText) { this.stepText = stepText; }
    }

    public static class HealCorrection {
        private String healId;
        private String originalLocator;
        private String healedLocator;
        private String correctLocator;
        private String reason;
        private boolean blacklist = true;

        // Builder pattern
        public static HealCorrection builder() { return new HealCorrection(); }

        public HealCorrection healId(String healId) { this.healId = healId; return this; }
        public HealCorrection originalLocator(String loc) { this.originalLocator = loc; return this; }
        public HealCorrection healedLocator(String loc) { this.healedLocator = loc; return this; }
        public HealCorrection correctLocator(String loc) { this.correctLocator = loc; return this; }
        public HealCorrection reason(String reason) { this.reason = reason; return this; }
        public HealCorrection shouldBlacklist(boolean blacklist) { this.blacklist = blacklist; return this; }

        // Getters
        public String getHealId() { return healId; }
        public String getOriginalLocator() { return originalLocator; }
        public String getHealedLocator() { return healedLocator; }
        public String getCorrectLocator() { return correctLocator; }
        public String getReason() { return reason; }
        public boolean shouldBlacklist() { return blacklist; }
    }

    public record FeedbackResult(boolean success, String message) {
        public static FeedbackResult success(String message) {
            return new FeedbackResult(true, message);
        }

        public static FeedbackResult error(String message) {
            return new FeedbackResult(false, message);
        }
    }

    public record FeedbackStats(
            int total,
            int positive,
            int negative,
            int corrections,
            int falseNegatives
    ) {
        public double getPositiveRate() {
            return total > 0 ? (double) positive / total : 0;
        }

        public double getAccuracyRate() {
            int evaluated = positive + negative;
            return evaluated > 0 ? (double) positive / evaluated : 0;
        }
    }

    @FunctionalInterface
    public interface FeedbackListener {
        void onFeedback(FeedbackRecord record);
    }
}
