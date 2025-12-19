package io.github.glaciousm.cucumber.annotations;

import io.github.glaciousm.core.model.HealPolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the semantic intent of a Cucumber step definition.
 * This annotation provides context to the healing system about what the step is meant to accomplish.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Intent {

    /**
     * Semantic action identifier.
     * Examples: "authenticate_user", "add_to_cart", "submit_payment"
     */
    String action();

    /**
     * Human-readable description for LLM context.
     * This should explain what the step is trying to accomplish.
     */
    String description() default "";

    /**
     * Healing policy for this step.
     */
    HealPolicy policy() default HealPolicy.AUTO_SAFE;

    /**
     * Whether this is a destructive action requiring explicit allowlist.
     */
    boolean destructive() default false;
}
