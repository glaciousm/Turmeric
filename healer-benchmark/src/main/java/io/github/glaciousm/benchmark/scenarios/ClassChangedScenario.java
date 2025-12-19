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
 * Scenario 03: Class changed (btn-primary → button-main)
 * Tests healing when an element's class name is renamed.
 */
public class ClassChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "03";
    }

    @Override
    public String getName() {
        return "Class Changed";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "CSS class changed from 'btn-primary' to 'button-main'. " +
               "The button should be found by its text or other attributes.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.className("btn-primary");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.className("button-main");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Shopping Cart - Before", """
            <h1>Shopping Cart</h1>
            <div class="cart-items">
                <div class="cart-item">
                    <span>Product A</span>
                    <span>$29.99</span>
                </div>
            </div>
            <div class="cart-actions">
                <button class="btn-secondary">Continue Shopping</button>
                <button class="btn-primary">Checkout</button>
            </div>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Shopping Cart - After", """
            <h1>Shopping Cart</h1>
            <div class="cart-items">
                <div class="cart-item">
                    <span>Product A</span>
                    <span>$29.99</span>
                </div>
            </div>
            <div class="cart-actions">
                <button class="button-secondary">Continue Shopping</button>
                <button class="button-main">Checkout</button>
            </div>
            """);
    }
}
