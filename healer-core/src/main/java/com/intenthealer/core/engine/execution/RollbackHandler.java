package com.intenthealer.core.engine.execution;

import com.intenthealer.core.model.ExecutionContext;
import com.intenthealer.core.model.UiSnapshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Handles rollback of failed heal attempts.
 * Maintains a stack of states that can be rolled back to.
 */
public class RollbackHandler {

    private static final Logger logger = LoggerFactory.getLogger(RollbackHandler.class);
    private static final int MAX_CHECKPOINTS = 10;

    private final Deque<Checkpoint> checkpoints = new ArrayDeque<>();

    /**
     * Create a checkpoint at the current state.
     */
    public void createCheckpoint(String description, String url, UiSnapshot snapshot) {
        Checkpoint checkpoint = new Checkpoint(description, url, snapshot);
        checkpoints.push(checkpoint);

        // Limit checkpoint stack size
        while (checkpoints.size() > MAX_CHECKPOINTS) {
            checkpoints.removeLast();
        }

        logger.debug("Created checkpoint: {} at {}", description, url);
    }

    /**
     * Rollback to the most recent checkpoint using browser navigation.
     */
    public boolean rollback(WebDriver driver) {
        if (checkpoints.isEmpty()) {
            logger.warn("No checkpoints available for rollback");
            return false;
        }

        Checkpoint checkpoint = checkpoints.pop();
        logger.info("Rolling back to checkpoint: {} at {}", checkpoint.description(), checkpoint.url());

        try {
            // Navigate back to checkpoint URL
            String currentUrl = driver.getCurrentUrl();
            if (!currentUrl.equals(checkpoint.url())) {
                driver.navigate().to(checkpoint.url());
                logger.debug("Navigated to: {}", checkpoint.url());
            }

            return true;
        } catch (Exception e) {
            logger.error("Rollback failed", e);
            return false;
        }
    }

    /**
     * Rollback using browser history.
     */
    public boolean rollbackUsingHistory(WebDriver driver) {
        try {
            driver.navigate().back();
            logger.debug("Rolled back using browser history");
            return true;
        } catch (Exception e) {
            logger.error("History rollback failed", e);
            return false;
        }
    }

    /**
     * Attempt smart rollback - tries different strategies.
     */
    public RollbackResult smartRollback(WebDriver driver, ExecutionContext context) {
        // Strategy 1: Try checkpoint rollback
        if (!checkpoints.isEmpty()) {
            if (rollback(driver)) {
                return RollbackResult.success("Rolled back to checkpoint");
            }
        }

        // Strategy 2: Try browser history
        try {
            String beforeUrl = driver.getCurrentUrl();
            driver.navigate().back();

            // Verify we actually went back
            String afterUrl = driver.getCurrentUrl();
            if (!afterUrl.equals(beforeUrl)) {
                return RollbackResult.success("Rolled back using browser history");
            }
        } catch (Exception e) {
            logger.debug("Browser history rollback failed: {}", e.getMessage());
        }

        // Strategy 3: Refresh the page
        try {
            driver.navigate().refresh();
            return RollbackResult.partial("Refreshed page - state may not be fully restored");
        } catch (Exception e) {
            logger.debug("Page refresh failed: {}", e.getMessage());
        }

        return RollbackResult.failed("All rollback strategies failed");
    }

    /**
     * Clear all checkpoints.
     */
    public void clearCheckpoints() {
        checkpoints.clear();
        logger.debug("Cleared all checkpoints");
    }

    /**
     * Get the number of available checkpoints.
     */
    public int getCheckpointCount() {
        return checkpoints.size();
    }

    /**
     * Check if rollback is available.
     */
    public boolean hasCheckpoints() {
        return !checkpoints.isEmpty();
    }

    /**
     * A saved checkpoint state.
     */
    private record Checkpoint(
            String description,
            String url,
            UiSnapshot snapshot
    ) {}

    /**
     * Result of a rollback attempt.
     */
    public record RollbackResult(
            boolean success,
            boolean partial,
            String message
    ) {
        public static RollbackResult success(String message) {
            return new RollbackResult(true, false, message);
        }

        public static RollbackResult partial(String message) {
            return new RollbackResult(true, true, message);
        }

        public static RollbackResult failed(String message) {
            return new RollbackResult(false, false, message);
        }
    }
}
