/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark.scenarios;

import io.github.glaciousm.benchmark.BenchmarkResult.ExpectedOutcome;
import org.openqa.selenium.By;

/**
 * Scenario 24: Multiple similar elements (ambiguous)
 * Tests that healing returns low confidence or refuses when multiple candidates exist.
 */
public class AmbiguousElementsScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "24";
    }

    @Override
    public String getName() {
        return "Ambiguous Elements";
    }

    @Override
    public String getCategory() {
        return "Negative Tests";
    }

    @Override
    public String getDescription() {
        return "Multiple buttons with identical text 'Delete' exist. " +
               "Healing should refuse or return low confidence due to ambiguity.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.LOW_CONFIDENCE;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("delete-item-1");
    }

    @Override
    public By getExpectedHealedLocator() {
        return null; // Ambiguous - should not heal
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Tasks - Before", """
            <h1>My Tasks</h1>
            <ul class="task-list">
                <li class="task" data-task-id="1">
                    <span>Buy groceries</span>
                    <button id="delete-item-1" class="btn-danger">Delete</button>
                </li>
                <li class="task" data-task-id="2">
                    <span>Call mom</span>
                    <button id="delete-item-2" class="btn-danger">Delete</button>
                </li>
                <li class="task" data-task-id="3">
                    <span>Fix bug</span>
                    <button id="delete-item-3" class="btn-danger">Delete</button>
                </li>
            </ul>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Tasks - After (IDs Removed)", """
            <h1>My Tasks</h1>
            <ul class="task-list">
                <li class="task">
                    <span>Buy groceries</span>
                    <button class="btn-danger">Delete</button>
                </li>
                <li class="task">
                    <span>Call mom</span>
                    <button class="btn-danger">Delete</button>
                </li>
                <li class="task">
                    <span>Fix bug</span>
                    <button class="btn-danger">Delete</button>
                </li>
            </ul>
            """);
    }
}
