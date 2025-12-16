package com.intenthealer.cucumber.intent;

import com.intenthealer.core.model.HealPolicy;
import com.intenthealer.core.model.IntentContract;
import com.intenthealer.cucumber.annotations.Intent;
import com.intenthealer.cucumber.annotations.Invariant;
import com.intenthealer.cucumber.annotations.Invariants;
import com.intenthealer.cucumber.annotations.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts intent contracts from step definition methods annotated with @Intent.
 */
public class IntentExtractor {

    private static final Logger logger = LoggerFactory.getLogger(IntentExtractor.class);

    // Cache of extracted intents by method signature
    private final Map<String, IntentContract> intentCache = new ConcurrentHashMap<>();

    // Step definition class registry
    private final List<Class<?>> stepDefinitionClasses = new ArrayList<>();

    public IntentExtractor() {
    }

    /**
     * Register step definition classes for intent extraction.
     */
    public void registerStepDefinitionClass(Class<?> clazz) {
        if (!stepDefinitionClasses.contains(clazz)) {
            stepDefinitionClasses.add(clazz);
            logger.debug("Registered step definition class: {}", clazz.getName());
        }
    }

    /**
     * Register multiple step definition classes.
     */
    public void registerStepDefinitionClasses(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            registerStepDefinitionClass(clazz);
        }
    }

    /**
     * Extract intent contract from a method.
     */
    public Optional<IntentContract> extractIntent(Method method) {
        String cacheKey = methodSignature(method);

        // Check cache first
        if (intentCache.containsKey(cacheKey)) {
            return Optional.of(intentCache.get(cacheKey));
        }

        // Look for @Intent annotation
        Intent intent = method.getAnnotation(Intent.class);
        if (intent == null) {
            return Optional.empty();
        }

        // Build intent contract
        IntentContract.Builder builder = IntentContract.builder()
                .action(intent.action())
                .description(intent.description())
                .policy(intent.policy())
                .destructive(intent.destructive());

        // Extract outcome check
        Outcome outcome = method.getAnnotation(Outcome.class);
        if (outcome != null) {
            if (outcome.checks().length > 0) {
                builder.outcomeCheck(outcome.checks()[0]);
            }
            // Use value() if provided, otherwise description()
            String outcomeDesc = !outcome.value().isEmpty() ? outcome.value() : outcome.description();
            if (!outcomeDesc.isEmpty()) {
                builder.outcomeDescription(outcomeDesc);
            }
        }

        // Extract invariants
        List<Class<? extends IntentContract.InvariantCheck>> invariantChecks = new ArrayList<>();

        // Check for @Invariants container
        Invariants invariants = method.getAnnotation(Invariants.class);
        if (invariants != null) {
            for (Invariant inv : invariants.value()) {
                invariantChecks.add(inv.value());
            }
        }

        // Check for single @Invariant
        Invariant singleInvariant = method.getAnnotation(Invariant.class);
        if (singleInvariant != null) {
            invariantChecks.add(singleInvariant.value());
        }

        if (!invariantChecks.isEmpty()) {
            builder.invariants(invariantChecks);
        }

        IntentContract contract = builder.build();
        intentCache.put(cacheKey, contract);

        logger.debug("Extracted intent for {}: {}", method.getName(), contract);
        return Optional.of(contract);
    }

    /**
     * Extract intent from step text by searching registered step definitions.
     */
    public Optional<IntentContract> extractIntentByStepText(String stepText) {
        for (Class<?> clazz : stepDefinitionClasses) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (matchesStepText(method, stepText)) {
                    return extractIntent(method);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Check if a method matches a step text based on Cucumber annotations.
     */
    private boolean matchesStepText(Method method, String stepText) {
        // Check common Cucumber step annotations
        String pattern = extractStepPattern(method);
        if (pattern == null) {
            return false;
        }

        // Convert Cucumber pattern to regex
        String regex = cucumberPatternToRegex(pattern);
        return stepText.matches(regex);
    }

    /**
     * Extract step pattern from Cucumber annotations.
     */
    private String extractStepPattern(Method method) {
        // Check for Cucumber annotations
        for (java.lang.annotation.Annotation annotation : method.getAnnotations()) {
            String annotationName = annotation.annotationType().getSimpleName();
            if (annotationName.equals("Given") || annotationName.equals("When") ||
                annotationName.equals("Then") || annotationName.equals("And") ||
                annotationName.equals("But")) {
                try {
                    Method valueMethod = annotation.annotationType().getMethod("value");
                    return (String) valueMethod.invoke(annotation);
                } catch (Exception e) {
                    logger.debug("Could not extract step pattern: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Convert Cucumber expression pattern to regex.
     */
    private String cucumberPatternToRegex(String pattern) {
        // Convert Cucumber expressions to regex
        // {string} -> "([^"]*)"
        // {int} -> (\d+)
        // {word} -> (\w+)
        // {float} -> ([+-]?\d*\.?\d+)
        return pattern
                .replace("{string}", "\"([^\"]*)\"")
                .replace("{int}", "(\\d+)")
                .replace("{word}", "(\\w+)")
                .replace("{float}", "([+-]?\\d*\\.?\\d+)")
                .replace("{}", "(.*)"); // Generic parameter
    }

    /**
     * Create a default intent contract for steps without @Intent annotation.
     */
    public IntentContract createDefaultIntent(String stepText, String stepKeyword) {
        boolean isAssertion = isAssertionStep(stepText, stepKeyword);

        return IntentContract.builder()
                .action(isAssertion ? "assertion" : "action")
                .description(stepText)
                .policy(isAssertion ? HealPolicy.OFF : HealPolicy.AUTO_SAFE)
                .destructive(false)
                .build();
    }

    /**
     * Determine if a step is an assertion based on keyword and text patterns.
     */
    private boolean isAssertionStep(String stepText, String stepKeyword) {
        if (stepKeyword != null) {
            String keyword = stepKeyword.toLowerCase().trim();
            if (keyword.equals("then")) {
                return true;
            }
        }

        if (stepText != null) {
            String lower = stepText.toLowerCase();
            return lower.contains("should") ||
                   lower.contains("must") ||
                   lower.contains("verify") ||
                   lower.contains("assert") ||
                   lower.contains("expect") ||
                   lower.contains("check") ||
                   lower.contains("ensure") ||
                   lower.contains("confirm") ||
                   lower.contains("validate");
        }

        return false;
    }

    /**
     * Clear the intent cache.
     */
    public void clearCache() {
        intentCache.clear();
    }

    /**
     * Get the number of cached intents.
     */
    public int getCacheSize() {
        return intentCache.size();
    }

    private String methodSignature(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }
}
