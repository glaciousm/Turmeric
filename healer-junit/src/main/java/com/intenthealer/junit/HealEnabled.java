package com.intenthealer.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable and configure healing for a JUnit 5 test method.
 *
 * Usage:
 * <pre>
 * &#64;Test
 * &#64;HealEnabled(intent = "Login with valid credentials")
 * void testLogin() {
 *     // Test code
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface HealEnabled {

    /**
     * Intent description for the test.
     * Used by the LLM to understand what the test is trying to accomplish.
     */
    String intent() default "";

    /**
     * Expected outcome after successful test execution.
     */
    String expectedOutcome() default "";

    /**
     * Invariants that must hold during test execution.
     */
    String[] invariants() default {};

    /**
     * Override healing mode for this test.
     * Values: AUTO_SAFE, MANUAL, AUTO_AGGRESSIVE
     */
    String mode() default "";

    /**
     * Minimum confidence threshold for auto-healing.
     */
    double minConfidence() default 0.8;

    /**
     * Maximum number of healing attempts per element.
     */
    int maxAttempts() default 3;

    /**
     * Tags for categorization and filtering.
     */
    String[] tags() default {};
}
