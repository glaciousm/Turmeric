package io.github.glaciousm.cucumber.annotations;

import io.github.glaciousm.core.model.IntentContract.OutcomeCheck;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares expected outcome checks for a step.
 * The outcome is validated after the action executes successfully.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Outcome {

    /**
     * Simple text description for LLM-based validation.
     * This is the value attribute for shorthand @Outcome("description") syntax.
     */
    String value() default "";

    /**
     * Outcome check classes to run after action.
     */
    Class<? extends OutcomeCheck>[] checks() default {};

    /**
     * Description for LLM-based validation (alternative to value).
     * If provided, the LLM will be asked to validate this outcome.
     */
    String description() default "";
}
