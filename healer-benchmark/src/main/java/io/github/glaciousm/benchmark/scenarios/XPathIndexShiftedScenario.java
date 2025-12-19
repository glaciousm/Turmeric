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
 * Scenario 05: XPath index shifted (div[1] → div[2])
 * Tests healing when DOM structure changes cause index shifts.
 */
public class XPathIndexShiftedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "05";
    }

    @Override
    public String getName() {
        return "XPath Index Shifted";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "A new element was inserted before the target, shifting its XPath index. " +
               "The healer should find the element by its content or data attributes.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.xpath("//div[@class='product-list']/div[1]//button");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.xpath("//div[@class='product-list']/div[2]//button");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Products - Before", """
            <h1>Products</h1>
            <div class="product-list">
                <div class="product" data-product-id="p001">
                    <h3>Laptop Pro</h3>
                    <p>$999.99</p>
                    <button class="add-to-cart">Add to Cart</button>
                </div>
                <div class="product" data-product-id="p002">
                    <h3>Wireless Mouse</h3>
                    <p>$29.99</p>
                    <button class="add-to-cart">Add to Cart</button>
                </div>
            </div>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Products - After", """
            <h1>Products</h1>
            <div class="product-list">
                <div class="featured-banner">
                    <p>Special Offer: 20% off all items!</p>
                </div>
                <div class="product" data-product-id="p001">
                    <h3>Laptop Pro</h3>
                    <p>$999.99</p>
                    <button class="add-to-cart">Add to Cart</button>
                </div>
                <div class="product" data-product-id="p002">
                    <h3>Wireless Mouse</h3>
                    <p>$29.99</p>
                    <button class="add-to-cart">Add to Cart</button>
                </div>
            </div>
            """);
    }
}
