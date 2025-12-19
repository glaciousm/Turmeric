package io.github.glaciousm.cucumber.annotations;

import io.github.glaciousm.core.model.IntentContract.InvariantCheck;

import java.lang.annotation.*;

/**
 * Declares an invariant that must hold during/after a step.
 * If violated, the heal is considered failed even if the action succeeded.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Invariants.class)
public @interface Invariant {

    /**
     * Invariant check class.
     */
    Class<? extends InvariantCheck> value();
}
