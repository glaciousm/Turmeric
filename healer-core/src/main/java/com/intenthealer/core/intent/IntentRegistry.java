package com.intenthealer.core.intent;

import com.intenthealer.core.model.HealPolicy;
import com.intenthealer.core.model.IntentContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Registry for managing intent contracts.
 * Maps step patterns to their intent definitions.
 */
public class IntentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(IntentRegistry.class);

    // Singleton instance
    private static volatile IntentRegistry instance;

    // Pattern-based registry
    private final Map<Pattern, IntentContract> patternRegistry = new ConcurrentHashMap<>();

    // Action-based registry
    private final Map<String, IntentContract> actionRegistry = new ConcurrentHashMap<>();

    // Method signature registry
    private final Map<String, IntentContract> methodRegistry = new ConcurrentHashMap<>();

    private IntentRegistry() {
    }

    /**
     * Get the singleton instance.
     */
    public static IntentRegistry getInstance() {
        if (instance == null) {
            synchronized (IntentRegistry.class) {
                if (instance == null) {
                    instance = new IntentRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Register an intent by step pattern.
     */
    public void registerByPattern(String stepPattern, IntentContract intent) {
        Pattern pattern = compileStepPattern(stepPattern);
        patternRegistry.put(pattern, intent);
        logger.debug("Registered intent for pattern: {}", stepPattern);
    }

    /**
     * Register an intent by action name.
     */
    public void registerByAction(String actionName, IntentContract intent) {
        actionRegistry.put(actionName.toLowerCase(), intent);
        logger.debug("Registered intent for action: {}", actionName);
    }

    /**
     * Register an intent by method signature.
     */
    public void registerByMethod(String className, String methodName, IntentContract intent) {
        String key = className + "#" + methodName;
        methodRegistry.put(key, intent);
        logger.debug("Registered intent for method: {}", key);
    }

    /**
     * Look up intent by step text.
     */
    public Optional<IntentContract> lookupByStepText(String stepText) {
        for (Map.Entry<Pattern, IntentContract> entry : patternRegistry.entrySet()) {
            if (entry.getKey().matcher(stepText).matches()) {
                logger.debug("Found intent for step: {}", stepText);
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Look up intent by action name.
     */
    public Optional<IntentContract> lookupByAction(String actionName) {
        return Optional.ofNullable(actionRegistry.get(actionName.toLowerCase()));
    }

    /**
     * Look up intent by method signature.
     */
    public Optional<IntentContract> lookupByMethod(String className, String methodName) {
        String key = className + "#" + methodName;
        return Optional.ofNullable(methodRegistry.get(key));
    }

    /**
     * Get or create a default intent for a step.
     */
    public IntentContract getOrDefault(String stepText, String stepKeyword) {
        return lookupByStepText(stepText)
                .orElseGet(() -> createDefaultIntent(stepText, stepKeyword));
    }

    /**
     * Create a default intent based on step text and keyword.
     */
    private IntentContract createDefaultIntent(String stepText, String stepKeyword) {
        boolean isAssertion = isAssertionStep(stepText, stepKeyword);

        return IntentContract.builder()
                .action(inferActionFromStepText(stepText))
                .description(stepText)
                .policy(isAssertion ? HealPolicy.OFF : HealPolicy.AUTO_SAFE)
                .destructive(isDestructiveStep(stepText))
                .build();
    }

    /**
     * Infer an action name from step text.
     */
    private String inferActionFromStepText(String stepText) {
        if (stepText == null || stepText.isEmpty()) {
            return "unknown";
        }

        String lower = stepText.toLowerCase();

        // Common action patterns
        if (lower.contains("click")) return "click";
        if (lower.contains("enter") || lower.contains("type") || lower.contains("input")) return "input";
        if (lower.contains("select") || lower.contains("choose")) return "select";
        if (lower.contains("navigate") || lower.contains("go to") || lower.contains("open")) return "navigate";
        if (lower.contains("submit")) return "submit";
        if (lower.contains("login") || lower.contains("sign in")) return "authenticate";
        if (lower.contains("logout") || lower.contains("sign out")) return "logout";
        if (lower.contains("search")) return "search";
        if (lower.contains("verify") || lower.contains("check") || lower.contains("should")) return "verify";
        if (lower.contains("wait")) return "wait";
        if (lower.contains("scroll")) return "scroll";
        if (lower.contains("upload")) return "upload";
        if (lower.contains("download")) return "download";
        if (lower.contains("delete") || lower.contains("remove")) return "delete";

        return "action";
    }

    /**
     * Check if a step is an assertion step.
     */
    private boolean isAssertionStep(String stepText, String stepKeyword) {
        if (stepKeyword != null && stepKeyword.toLowerCase().trim().equals("then")) {
            return true;
        }

        if (stepText != null) {
            String lower = stepText.toLowerCase();
            return lower.contains("should") ||
                   lower.contains("must") ||
                   lower.contains("verify") ||
                   lower.contains("assert") ||
                   lower.contains("expect") ||
                   lower.contains("ensure") ||
                   lower.contains("confirm") ||
                   lower.contains("validate") ||
                   lower.contains("check that");
        }

        return false;
    }

    /**
     * Check if a step appears to be destructive.
     */
    private boolean isDestructiveStep(String stepText) {
        if (stepText == null) {
            return false;
        }

        String lower = stepText.toLowerCase();
        return lower.contains("delete") ||
               lower.contains("remove") ||
               lower.contains("cancel") ||
               lower.contains("terminate") ||
               lower.contains("deactivate") ||
               lower.contains("unsubscribe") ||
               lower.contains("close account");
    }

    /**
     * Convert Cucumber step pattern to regex.
     */
    private Pattern compileStepPattern(String stepPattern) {
        // Convert Cucumber expressions to regex
        String regex = stepPattern
                .replace("{string}", "\"([^\"]*)\"")
                .replace("{int}", "(\\d+)")
                .replace("{word}", "(\\w+)")
                .replace("{float}", "([+-]?\\d*\\.?\\d+)")
                .replace("{}", "(.*)");

        // Escape regex special chars except what we just added
        regex = "^" + regex + "$";

        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * Clear all registered intents.
     */
    public void clear() {
        patternRegistry.clear();
        actionRegistry.clear();
        methodRegistry.clear();
        logger.info("Intent registry cleared");
    }

    /**
     * Get the total number of registered intents.
     */
    public int size() {
        return patternRegistry.size() + actionRegistry.size() + methodRegistry.size();
    }
}
