package com.intenthealer.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to disable healing for a specific test method or test class.
 *
 * Usage:
 * <pre>
 * &#64;Test
 * &#64;HealDisabled(reason = "This test validates specific error handling")
 * void testErrorHandling() {
 *     // Test code - healing will not apply
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface HealDisabled {

    /**
     * Optional reason why healing is disabled.
     */
    String reason() default "";
}
