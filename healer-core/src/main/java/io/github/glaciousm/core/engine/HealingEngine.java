package io.github.glaciousm.core.engine;

import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.engine.approval.ApprovalCallback;
import io.github.glaciousm.core.engine.approval.ApprovalDecision;
import io.github.glaciousm.core.engine.approval.ApprovalWorkflow;
import io.github.glaciousm.core.engine.approval.HealProposal;
import io.github.glaciousm.core.engine.guardrails.GuardrailChecker;
import io.github.glaciousm.core.engine.notification.NotificationConfig;
import io.github.glaciousm.core.engine.notification.NotificationService;
import io.github.glaciousm.core.engine.notification.NotificationService.HealNotification;
import io.github.glaciousm.core.engine.sharing.PatternSharingService;
import io.github.glaciousm.core.engine.sharing.PatternSharingService.*;
import io.github.glaciousm.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Main healing engine that orchestrates the healing process.
 * This class is independent of Selenium/LLM implementations to allow different integrations.
 */
public class HealingEngine {

    private static final Logger logger = LoggerFactory.getLogger(HealingEngine.class);

    private final HealerConfig config;
    private final GuardrailChecker guardrails;
    private final NotificationService notificationService;
    private final PatternSharingService patternSharingService;

    // Pluggable components
    private Function<FailureContext, UiSnapshot> snapshotCapture;
    private BiFunction<FailureContext, UiSnapshot, HealDecision> llmEvaluator;
    private TriFunction<ActionType, ElementSnapshot, Object, Void> actionExecutor;
    private Function<ExecutionContext, OutcomeResult> outcomeValidator;

    // Optional approval workflow for CONFIRM mode
    private ApprovalWorkflow approvalWorkflow;

    public HealingEngine(HealerConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.guardrails = new GuardrailChecker(config.getGuardrails());

        // Initialize notification service if configured
        NotificationConfig notificationConfig = config.getNotification();
        if (notificationConfig != null && notificationConfig.isEnabled()) {
            this.notificationService = new NotificationService(notificationConfig);
        } else {
            this.notificationService = null;
        }

        // Initialize pattern sharing service
        SharingConfig sharingConfig = config.getSharing();
        if (sharingConfig != null) {
            this.patternSharingService = new PatternSharingService(sharingConfig);
        } else {
            this.patternSharingService = new PatternSharingService();
        }
    }

    /**
     * Set the snapshot capture function.
     */
    public void setSnapshotCapture(Function<FailureContext, UiSnapshot> snapshotCapture) {
        this.snapshotCapture = snapshotCapture;
    }

    /**
     * Set the LLM evaluator function.
     */
    public void setLlmEvaluator(BiFunction<FailureContext, UiSnapshot, HealDecision> llmEvaluator) {
        this.llmEvaluator = llmEvaluator;
    }

    /**
     * Set the action executor function.
     */
    public void setActionExecutor(TriFunction<ActionType, ElementSnapshot, Object, Void> actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    /**
     * Set the outcome validator function.
     */
    public void setOutcomeValidator(Function<ExecutionContext, OutcomeResult> outcomeValidator) {
        this.outcomeValidator = outcomeValidator;
    }

    /**
     * Set the approval workflow for CONFIRM mode.
     * When set, heals with CONFIRM policy will require approval before execution.
     */
    public void setApprovalWorkflow(ApprovalWorkflow approvalWorkflow) {
        this.approvalWorkflow = approvalWorkflow;
    }

    /**
     * Attempt to heal a test failure.
     */
    public HealResult attemptHeal(FailureContext failure, IntentContract intent) {
        Instant startTime = Instant.now();

        try {
            // Check if healing is enabled
            if (!config.isEnabled()) {
                return HealResult.refused("Healing is disabled");
            }

            // 1. Pre-LLM guardrail check
            GuardrailResult preCheck = guardrails.checkPreLlm(failure, intent);
            if (preCheck.isRefused()) {
                logger.info("Pre-LLM guardrail refused: {}", preCheck.getReason());
                return HealResult.refused(preCheck.getReason());
            }

            // 2. Capture UI snapshot
            if (snapshotCapture == null) {
                return HealResult.failed("Snapshot capture not configured");
            }
            UiSnapshot snapshot = snapshotCapture.apply(failure);

            if (snapshot == null || !snapshot.hasElements()) {
                return HealResult.failed("No interactive elements found on page");
            }

            // Check URL against forbidden patterns
            GuardrailResult urlCheck = guardrails.checkUrl(snapshot.getUrl());
            if (urlCheck.isRefused()) {
                return HealResult.refused(urlCheck.getReason());
            }

            // 2.5. Check for matching patterns (skip LLM if high-confidence match found)
            if (failure.getOriginalLocator() != null) {
                List<PatternMatch> patternMatches = patternSharingService.findMatchingPatterns(
                        failure.getOriginalLocator(), snapshot.getUrl());

                if (!patternMatches.isEmpty()) {
                    PatternMatch bestMatch = patternMatches.get(0);
                    // Use pattern if similarity is very high (>= 0.85)
                    if (bestMatch.similarity() >= 0.85 && bestMatch.pattern().successRate() >= 0.8) {
                        logger.info("Using cached pattern with {}% similarity and {}% success rate",
                                Math.round(bestMatch.similarity() * 100),
                                Math.round(bestMatch.pattern().successRate() * 100));

                        // Create heal decision from pattern
                        HealDecision patternDecision = HealDecision.builder()
                                .canHeal(true)
                                .confidence(bestMatch.pattern().avgConfidence())
                                .reasoning("Matched existing pattern: " + bestMatch.pattern().patternId())
                                .selectedElementIndex(-1) // Special marker for pattern-based heal
                                .build();

                        HealResult patternResult = HealResult.builder()
                                .outcome(HealOutcome.SUCCESS)
                                .decision(patternDecision)
                                .healedLocator(bestMatch.pattern().healedSignature())
                                .confidence(bestMatch.pattern().avgConfidence())
                                .reasoning("Pattern match from " + bestMatch.source())
                                .duration(Duration.between(startTime, Instant.now()))
                                .build();

                        sendNotification(failure, patternResult);
                        return patternResult;
                    }
                }
            }

            // 3. Get LLM decision
            if (llmEvaluator == null) {
                return HealResult.failed("LLM evaluator not configured");
            }
            HealDecision decision = llmEvaluator.apply(failure, snapshot);

            // 4. Check if LLM decided not to heal
            if (!decision.canHeal()) {
                return HealResult.builder()
                        .outcome(HealOutcome.REFUSED)
                        .decision(decision)
                        .failureReason(decision.getRefusalReason())
                        .duration(Duration.between(startTime, Instant.now()))
                        .build();
            }

            // 5. Get chosen element
            Optional<ElementSnapshot> chosenOpt = snapshot.getElement(decision.getSelectedElementIndex());
            if (chosenOpt.isEmpty()) {
                return HealResult.failed("Selected element index not found in snapshot");
            }
            ElementSnapshot chosenElement = chosenOpt.get();

            // 6. Post-LLM guardrail check
            GuardrailResult postCheck = guardrails.checkPostLlm(decision, chosenElement, snapshot);
            if (postCheck.isRefused()) {
                logger.info("Post-LLM guardrail refused: {}", postCheck.getReason());
                return HealResult.builder()
                        .outcome(HealOutcome.REFUSED)
                        .decision(decision)
                        .failureReason(postCheck.getReason())
                        .duration(Duration.between(startTime, Instant.now()))
                        .build();
            }

            // 7. Handle SUGGEST mode (don't actually execute)
            if (intent.getPolicy() == HealPolicy.SUGGEST) {
                return HealResult.builder()
                        .outcome(HealOutcome.SUGGESTED)
                        .decision(decision)
                        .healedElementIndex(decision.getSelectedElementIndex())
                        .confidence(decision.getConfidence())
                        .reasoning(decision.getReasoning())
                        .duration(Duration.between(startTime, Instant.now()))
                        .build();
            }

            // 7.5. Handle CONFIRM mode (require approval before executing)
            if (intent.getPolicy() == HealPolicy.CONFIRM && approvalWorkflow != null) {
                // Generate proposed locator for the approval request
                String proposedLocatorStr = generateLocatorFromElement(chosenElement);
                LocatorInfo proposedLocator = parseLocatorString(proposedLocatorStr);

                // Create proposal
                HealProposal proposal = HealProposal.builder()
                        .featureName(failure.getFeatureName())
                        .scenarioName(failure.getScenarioName())
                        .stepText(failure.getStepText())
                        .originalLocator(failure.getOriginalLocator())
                        .proposedLocator(proposedLocator)
                        .actionType(failure.getActionType())
                        .confidence(decision.getConfidence())
                        .reasoning(decision.getReasoning())
                        .pageUrl(snapshot.getUrl())
                        .build();

                logger.info("Submitting heal proposal for approval: {}", proposal.getId());

                // Submit for approval - this may block waiting for human approval
                ApprovalDecision approvalDecision = approvalWorkflow.submitForApproval(proposal, intent.getPolicy());

                if (!approvalDecision.isApproved()) {
                    logger.info("Heal proposal {} was rejected: {}", proposal.getId(), approvalDecision.getReason());
                    return HealResult.builder()
                            .outcome(HealOutcome.REFUSED)
                            .decision(decision)
                            .failureReason("Approval rejected: " + approvalDecision.getReason())
                            .duration(Duration.between(startTime, Instant.now()))
                            .build();
                }

                logger.info("Heal proposal {} was approved", proposal.getId());
            }

            // 8. Execute the healed action
            if (actionExecutor != null) {
                try {
                    actionExecutor.apply(failure.getActionType(), chosenElement, failure.getActionData());
                } catch (Exception e) {
                    logger.error("Action execution failed: {}", e.getMessage());
                    HealResult actionFailedResult = HealResult.builder()
                            .outcome(HealOutcome.FAILED)
                            .decision(decision)
                            .failureReason("Action execution failed: " + e.getMessage())
                            .duration(Duration.between(startTime, Instant.now()))
                            .build();
                    sendNotification(failure, actionFailedResult);
                    return actionFailedResult;
                }
            }

            // 9. Validate outcome (if validator configured)
            if (outcomeValidator != null) {
                ExecutionContext ctx = new ExecutionContext(null, snapshot);
                OutcomeResult outcomeResult = outcomeValidator.apply(ctx);
                if (outcomeResult.isFailed()) {
                    HealResult outcomeFailedResult = HealResult.builder()
                            .outcome(HealOutcome.OUTCOME_FAILED)
                            .decision(decision)
                            .failureReason(outcomeResult.getMessage())
                            .duration(Duration.between(startTime, Instant.now()))
                            .build();
                    sendNotification(failure, outcomeFailedResult);
                    return outcomeFailedResult;
                }
            }

            // 10. Success! Generate healed locator from chosen element
            String healedLocator = generateLocatorFromElement(chosenElement);
            logger.info("Generated healed locator: {}", healedLocator);

            HealResult successResult = HealResult.builder()
                    .outcome(HealOutcome.SUCCESS)
                    .decision(decision)
                    .healedElementIndex(decision.getSelectedElementIndex())
                    .healedLocator(healedLocator)
                    .confidence(decision.getConfidence())
                    .reasoning(decision.getReasoning())
                    .duration(Duration.between(startTime, Instant.now()))
                    .build();

            // Send success notification
            sendNotification(failure, successResult);

            // Store successful heal pattern for future use
            storeHealPattern(failure, healedLocator, decision.getConfidence(), intent);

            return successResult;

        } catch (Exception e) {
            logger.error("Unexpected error during healing: {}", e.getMessage(), e);
            HealResult failedResult = HealResult.builder()
                    .outcome(HealOutcome.FAILED)
                    .failureReason("Unexpected error: " + e.getMessage())
                    .duration(Duration.between(startTime, Instant.now()))
                    .build();

            // Send failure notification
            sendNotification(failure, failedResult);

            return failedResult;
        }
    }

    /**
     * Generate a locator string from an ElementSnapshot.
     * Format: "strategy=value" (e.g., "id=login-btn", "css=button.submit")
     * Prefers id > name > css selector with class.
     */
    private String generateLocatorFromElement(ElementSnapshot element) {
        // Prefer ID if available (but not UUIDs or dynamic IDs)
        String id = element.getId();
        if (id != null && !id.isEmpty() && !looksLikeDynamicId(id)) {
            return "id=" + id;
        }

        // Try name
        if (element.getName() != null && !element.getName().isEmpty()) {
            return "name=" + element.getName();
        }

        // Build CSS selector
        StringBuilder css = new StringBuilder();
        String tagName = element.getTagName() != null ? element.getTagName().toLowerCase() : "div";
        css.append(tagName);

        // Add classes if available (limit to first 2 to avoid overly specific selectors)
        List<String> classes = element.getClasses();
        if (classes != null && !classes.isEmpty()) {
            int classCount = 0;
            for (String className : classes) {
                // Skip dynamic-looking class names
                if (!looksLikeDynamicClass(className) && classCount < 2) {
                    css.append(".").append(className.replace(" ", ""));
                    classCount++;
                }
            }
        }

        // Add type attribute for inputs (only for specific types that are stable)
        String type = element.getType();
        if (type != null && !type.isEmpty() && isStableTypeAttribute(tagName, type)) {
            css.append("[type=\"").append(type).append("\"]");
        }

        // If we only have a tag name with no distinguishing features, try text content
        if (css.toString().equals(tagName) && element.getText() != null && !element.getText().isEmpty()) {
            // For buttons/links, use a more specific selector based on visible text
            String text = element.getText().trim();
            if (text.length() <= 30) {
                // XPath with text content for unique identification
                return "xpath=//" + tagName + "[contains(text(),'" + escapeXpathText(text) + "')]";
            }
        }

        return "css=" + css.toString();
    }

    /**
     * Check if an ID looks like a dynamically generated one.
     */
    private boolean looksLikeDynamicId(String id) {
        // UUID patterns, numbers only, or very long IDs
        return id.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-.*") ||  // UUID pattern
               id.matches("\\d+") ||                          // Numbers only
               id.length() > 50 ||                            // Very long IDs
               id.contains("ember") ||                        // Ember.js generated
               id.contains("react") ||                        // React generated
               id.matches(".*\\d{5,}.*");                     // Contains 5+ digit numbers
    }

    /**
     * Check if a class name looks dynamically generated.
     */
    private boolean looksLikeDynamicClass(String className) {
        return className.matches(".*[0-9a-f]{6,}.*") ||       // Long hex strings
               className.matches(".*\\d{4,}.*") ||            // 4+ digit numbers
               className.contains("_");                        // Often dynamic separators
    }

    /**
     * Check if a type attribute is stable and worth including in selector.
     */
    private boolean isStableTypeAttribute(String tagName, String type) {
        if (!"input".equals(tagName)) {
            return false;
        }
        // Only include type for input elements with distinctive types
        return "checkbox".equals(type) || "radio".equals(type) ||
               "text".equals(type) || "password".equals(type) ||
               "email".equals(type) || "number".equals(type);
    }

    /**
     * Escape text for use in XPath.
     */
    private String escapeXpathText(String text) {
        return text.replace("'", "\\'");
    }

    /**
     * Send a notification about a heal result.
     * This is a fire-and-forget operation that doesn't block the main thread.
     */
    private void sendNotification(FailureContext failure, HealResult result) {
        if (notificationService == null) {
            return;
        }

        try {
            HealDecision decision = result.getDecision().orElse(null);
            String originalLocator = failure.getOriginalLocator() != null
                    ? failure.getOriginalLocator().toString()
                    : "unknown";
            String healedLocator = result.getHealedLocator().orElse("N/A");
            double confidence = decision != null ? decision.getConfidence() : 0.0;
            String reasoning = decision != null ? decision.getReasoning() : result.getFailureReason().orElse("Unknown");

            HealNotification notification = new HealNotification(
                    Instant.now(),
                    failure.getFeatureName() != null ? failure.getFeatureName() : "Unknown Feature",
                    failure.getScenarioName() != null ? failure.getScenarioName() : "Unknown Scenario",
                    failure.getStepText(),
                    originalLocator,
                    healedLocator,
                    confidence,
                    determineTrustLevel(confidence),
                    reasoning,
                    result.getOutcome() == HealOutcome.SUCCESS
            );

            notificationService.notifyHeal(notification);
        } catch (Exception e) {
            logger.warn("Failed to send heal notification: {}", e.getMessage());
        }
    }

    /**
     * Store a successful heal pattern for future reuse.
     */
    private void storeHealPattern(FailureContext failure, String healedLocator, double confidence, IntentContract intent) {
        if (patternSharingService == null || failure.getOriginalLocator() == null) {
            return;
        }

        try {
            // Parse the healed locator back into a LocatorInfo
            LocatorInfo healedLocatorInfo = parseLocatorString(healedLocator);
            if (healedLocatorInfo == null) {
                return;
            }

            // Determine page URL pattern from failure context
            String pageUrlPattern = null;
            if (failure.getAdditionalContext() != null) {
                Object pageUrl = failure.getAdditionalContext().get("pageUrl");
                if (pageUrl != null) {
                    pageUrlPattern = pageUrl.toString();
                }
            }

            HealPatternData patternData = new HealPatternData(
                    failure.getOriginalLocator(),
                    healedLocatorInfo,
                    pageUrlPattern,
                    intent != null ? intent.getDescription() : failure.getStepText(),
                    confidence,
                    true, // success
                    failure.getTags()
            );

            patternSharingService.addPattern(patternData);
            logger.debug("Stored heal pattern for locator: {}", failure.getOriginalLocator());
        } catch (Exception e) {
            logger.warn("Failed to store heal pattern: {}", e.getMessage());
        }
    }

    /**
     * Parse a locator string (e.g., "id=button", "css=.class") into LocatorInfo.
     */
    private LocatorInfo parseLocatorString(String locatorStr) {
        if (locatorStr == null || !locatorStr.contains("=")) {
            return null;
        }

        int eqIndex = locatorStr.indexOf('=');
        String strategy = locatorStr.substring(0, eqIndex).toUpperCase();
        String value = locatorStr.substring(eqIndex + 1);

        try {
            LocatorInfo.LocatorStrategy locatorStrategy = switch (strategy) {
                case "ID" -> LocatorInfo.LocatorStrategy.ID;
                case "NAME" -> LocatorInfo.LocatorStrategy.NAME;
                case "CSS" -> LocatorInfo.LocatorStrategy.CSS;
                case "XPATH" -> LocatorInfo.LocatorStrategy.XPATH;
                case "CLASSNAME", "CLASS" -> LocatorInfo.LocatorStrategy.CLASS_NAME;
                case "TAGNAME", "TAG" -> LocatorInfo.LocatorStrategy.TAG_NAME;
                case "LINKTEXT", "LINK" -> LocatorInfo.LocatorStrategy.LINK_TEXT;
                case "PARTIALLINKTEXT" -> LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT;
                default -> LocatorInfo.LocatorStrategy.CSS;
            };
            return new LocatorInfo(locatorStrategy, value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Determine trust level based on confidence score.
     */
    private String determineTrustLevel(double confidence) {
        if (confidence >= 0.9) {
            return "HIGH";
        } else if (confidence >= 0.7) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * Shutdown the notification service gracefully.
     * Should be called when the engine is no longer needed.
     */
    public void shutdown() {
        if (notificationService != null) {
            notificationService.shutdown();
        }
    }

    /**
     * Functional interface for three-argument functions.
     */
    @FunctionalInterface
    public interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }
}
