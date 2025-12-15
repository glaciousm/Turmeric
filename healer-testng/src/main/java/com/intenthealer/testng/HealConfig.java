package com.intenthealer.testng;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to configure healing behavior for TestNG tests.
 * Can be applied at class or method level.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface HealConfig {

    /**
     * Whether healing is enabled for this test.
     */
    boolean enabled() default true;

    /**
     * Healing mode override.
     * Values: AUTO_SAFE, MANUAL, AUTO_AGGRESSIVE, DISABLED
     */
    String mode() default "";

    /**
     * Custom intent description for the test.
     */
    String intent() default "";

    /**
     * Expected outcome description for validation.
     */
    String expectedOutcome() default "";

    /**
     * Invariants that must hold during the test.
     */
    String[] invariants() default {};

    /**
     * Maximum healing attempts per element.
     */
    int maxAttempts() default 3;

    /**
     * Minimum confidence threshold for auto-healing.
     */
    double minConfidence() default 0.8;

    /**
     * Tags for categorizing heals.
     */
    String[] tags() default {};
}
