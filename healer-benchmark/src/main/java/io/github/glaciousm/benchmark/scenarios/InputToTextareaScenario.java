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
 * Scenario 12: Input → Textarea
 * Tests healing when a single-line input is converted to a textarea.
 */
public class InputToTextareaScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "12";
    }

    @Override
    public String getName() {
        return "Input to Textarea";
    }

    @Override
    public String getCategory() {
        return "Element Type Changes";
    }

    @Override
    public String getDescription() {
        return "A text input was converted to a textarea for multi-line input. " +
               "The healer should find the element by its label or name attribute.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("input#description");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("textarea#description");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Create Task - Before", """
            <h1>Create Task</h1>
            <form id="task-form">
                <div class="form-group">
                    <label for="title">Task Title</label>
                    <input type="text" id="title" name="title" placeholder="Enter task title">
                </div>
                <div class="form-group">
                    <label for="description">Description</label>
                    <input type="text" id="description" name="description"
                           placeholder="Enter description">
                </div>
                <div class="form-group">
                    <label for="due-date">Due Date</label>
                    <input type="date" id="due-date" name="due_date">
                </div>
                <button type="submit" class="btn-primary">Create Task</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Create Task - After", """
            <h1>Create Task</h1>
            <form id="task-form">
                <div class="form-group">
                    <label for="title">Task Title</label>
                    <input type="text" id="title" name="title" placeholder="Enter task title">
                </div>
                <div class="form-group">
                    <label for="description">Description</label>
                    <textarea id="description" name="description" rows="4"
                              placeholder="Enter description"></textarea>
                </div>
                <div class="form-group">
                    <label for="due-date">Due Date</label>
                    <input type="date" id="due-date" name="due_date">
                </div>
                <button type="submit" class="btn-primary">Create Task</button>
            </form>
            """);
    }
}
