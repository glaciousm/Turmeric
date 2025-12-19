/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark.scenarios;

import io.github.glaciousm.benchmark.BenchmarkResult.ExpectedOutcome;
import io.github.glaciousm.core.model.ElementSnapshot;
import org.openqa.selenium.By;

/**
 * Scenario 32: Outcome validation fails post-heal
 * Tests detection when the healed action doesn't produce expected outcome.
 */
public class OutcomeValidationFailsScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "32";
    }

    @Override
    public String getName() {
        return "Outcome Validation Fails";
    }

    @Override
    public String getCategory() {
        return "False Heal Detection";
    }

    @Override
    public String getDescription() {
        return "Healer finds a 'Submit' button but the expected outcome (form submission) " +
               "won't occur because it's actually a disabled or non-functional element.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.DETECT_FALSE_HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("submit-form");
    }

    @Override
    public By getExpectedHealedLocator() {
        // Should find the actual submit button, not the disabled one
        return By.cssSelector("button[type='submit']:not([disabled])");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Order Form - Before", """
            <form id="order-form" action="/submit-order">
                <div class="form-group">
                    <label for="product">Product</label>
                    <select id="product" name="product" required>
                        <option value="">Select product...</option>
                        <option value="laptop">Laptop - $999</option>
                        <option value="phone">Phone - $599</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="quantity">Quantity</label>
                    <input type="number" id="quantity" name="quantity" min="1" value="1">
                </div>
                <button type="submit" id="submit-form" class="btn-primary">
                    Place Order
                </button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Order Form - After (Submit Button Condition Changed)", """
            <form id="order-form" action="/submit-order">
                <div class="form-group">
                    <label for="product">Product</label>
                    <select id="product" name="product" required>
                        <option value="">Select product...</option>
                        <option value="laptop">Laptop - $999</option>
                        <option value="phone">Phone - $599</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="quantity">Quantity</label>
                    <input type="number" id="quantity" name="quantity" min="1" value="1">
                </div>
                <div class="submit-wrapper">
                    <button type="button" class="btn-primary submit-btn"
                            disabled aria-disabled="true"
                            title="Please select a product first">
                        Place Order
                    </button>
                    <span class="validation-msg">Select a product to continue</span>
                </div>
            </form>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // The submit button must be enabled and of type "submit"
        // The "after" HTML has a disabled button, so healed element should NOT match
        return element.isEnabled() &&
               "submit".equals(element.getType()) &&
               "button".equalsIgnoreCase(element.getTagName());
    }
}
