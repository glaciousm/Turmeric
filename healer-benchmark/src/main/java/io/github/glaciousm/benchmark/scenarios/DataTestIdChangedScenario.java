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
 * Scenario 08: data-testid changed
 * Tests healing when a data-testid attribute is renamed.
 */
public class DataTestIdChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "08";
    }

    @Override
    public String getName() {
        return "data-testid Changed";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "data-testid changed from 'add-item-btn' to 'add-to-cart-button'. " +
               "The element should be found by its visible text or ARIA attributes.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("[data-testid='add-item-btn']");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("[data-testid='add-to-cart-button']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Product Detail - Before", """
            <div class="product-detail">
                <h1>Wireless Headphones</h1>
                <img src="headphones.jpg" alt="Wireless Headphones">
                <p class="price">$149.99</p>
                <p class="description">Premium noise-canceling wireless headphones.</p>
                <div class="quantity">
                    <label for="qty">Quantity:</label>
                    <input type="number" id="qty" value="1" min="1">
                </div>
                <button data-testid="add-item-btn" class="btn-primary" aria-label="Add to cart">
                    Add to Cart
                </button>
            </div>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Product Detail - After", """
            <div class="product-detail">
                <h1>Wireless Headphones</h1>
                <img src="headphones.jpg" alt="Wireless Headphones">
                <p class="price">$149.99</p>
                <p class="description">Premium noise-canceling wireless headphones.</p>
                <div class="quantity">
                    <label for="qty">Quantity:</label>
                    <input type="number" id="qty" value="1" min="1">
                </div>
                <button data-testid="add-to-cart-button" class="btn-primary" aria-label="Add to cart">
                    Add to Cart
                </button>
            </div>
            """);
    }
}
