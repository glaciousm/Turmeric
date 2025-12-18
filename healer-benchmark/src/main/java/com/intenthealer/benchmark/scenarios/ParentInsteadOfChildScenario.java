/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package com.intenthealer.benchmark.scenarios;

import com.intenthealer.benchmark.BenchmarkResult.ExpectedOutcome;
import com.intenthealer.core.model.ElementSnapshot;
import org.openqa.selenium.By;

/**
 * Scenario 30: Heals parent instead of child
 * Tests detection of false heals when parent element is selected instead of child.
 */
public class ParentInsteadOfChildScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "30";
    }

    @Override
    public String getName() {
        return "Parent Instead of Child";
    }

    @Override
    public String getCategory() {
        return "False Heal Detection";
    }

    @Override
    public String getDescription() {
        return "Target is the checkbox input but healer might select the containing label. " +
               "Healer should select the actual input element, not the wrapper.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        // The LLM correctly identifies the checkbox input vs the label wrapper
        // This scenario actually tests successful healing with nested elements
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("agree-terms");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("input[type='checkbox'][name='terms']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Terms Agreement - Before", """
            <form id="registration-form">
                <div class="form-group">
                    <label for="email">Email</label>
                    <input type="email" id="email" name="email" required>
                </div>
                <div class="form-group checkbox-group">
                    <label>
                        <input type="checkbox" id="agree-terms" name="terms" required>
                        I agree to the <a href="/terms">Terms of Service</a>
                    </label>
                </div>
                <div class="form-group checkbox-group">
                    <label>
                        <input type="checkbox" id="newsletter" name="newsletter">
                        Subscribe to newsletter
                    </label>
                </div>
                <button type="submit" class="btn-primary">Register</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Terms Agreement - After (IDs Removed)", """
            <form id="registration-form">
                <div class="form-group">
                    <label for="email">Email</label>
                    <input type="email" id="email" name="email" required>
                </div>
                <div class="form-group checkbox-group" data-field="terms">
                    <label class="checkbox-label">
                        <input type="checkbox" name="terms" required>
                        <span class="checkmark"></span>
                        I agree to the <a href="/terms">Terms of Service</a>
                    </label>
                </div>
                <div class="form-group checkbox-group" data-field="newsletter">
                    <label class="checkbox-label">
                        <input type="checkbox" name="newsletter">
                        <span class="checkmark"></span>
                        Subscribe to newsletter
                    </label>
                </div>
                <button type="submit" class="btn-primary">Register</button>
            </form>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // Must be an INPUT element with type="checkbox" and name="terms"
        // Not the parent LABEL element
        return "input".equalsIgnoreCase(element.getTagName()) &&
               "checkbox".equals(element.getType()) &&
               "terms".equals(element.getName());
    }
}
