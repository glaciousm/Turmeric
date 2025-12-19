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
 * Scenario 35: Dynamically loaded content (AJAX)
 * Tests healing when content is loaded dynamically via JavaScript.
 */
public class DynamicContentScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "35";
    }

    @Override
    public String getName() {
        return "Dynamic Content";
    }

    @Override
    public String getCategory() {
        return "Complex DOM";
    }

    @Override
    public String getDescription() {
        return "Content is loaded via AJAX with dynamic IDs. " +
               "Healer should handle dynamic attributes and find stable identifiers.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("product-card-12345");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("[data-product-name='Wireless Headphones'] button.add-to-cart");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Products (Dynamic) - Before", """
            <main>
                <h1>Featured Products</h1>
                <div id="product-container" class="product-grid">
                    <div id="product-card-12345" class="product-card" data-product-id="12345">
                        <img src="headphones.jpg" alt="Wireless Headphones">
                        <h3>Wireless Headphones</h3>
                        <p class="price">$149.99</p>
                        <button class="btn-primary add-to-cart">Add to Cart</button>
                    </div>
                    <div id="product-card-12346" class="product-card" data-product-id="12346">
                        <img src="keyboard.jpg" alt="Mechanical Keyboard">
                        <h3>Mechanical Keyboard</h3>
                        <p class="price">$89.99</p>
                        <button class="btn-primary add-to-cart">Add to Cart</button>
                    </div>
                    <div id="product-card-12347" class="product-card" data-product-id="12347">
                        <img src="mouse.jpg" alt="Gaming Mouse">
                        <h3>Gaming Mouse</h3>
                        <p class="price">$49.99</p>
                        <button class="btn-primary add-to-cart">Add to Cart</button>
                    </div>
                </div>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Products (Dynamic) - After (New Session, Different IDs)", """
            <main>
                <h1>Featured Products</h1>
                <div id="products-wrapper" class="product-grid">
                    <article class="product-tile" data-product-name="Wireless Headphones"
                             data-sku="SKU-WH-001">
                        <figure class="product-image">
                            <img src="/images/headphones.webp" alt="Wireless Headphones">
                        </figure>
                        <div class="product-details">
                            <h3 class="product-title">Wireless Headphones</h3>
                            <span class="product-price">$149.99</span>
                        </div>
                        <button class="btn-primary add-to-cart" data-action="add-to-cart">
                            Add to Cart
                        </button>
                    </article>
                    <article class="product-tile" data-product-name="Mechanical Keyboard"
                             data-sku="SKU-MK-002">
                        <figure class="product-image">
                            <img src="/images/keyboard.webp" alt="Mechanical Keyboard">
                        </figure>
                        <div class="product-details">
                            <h3 class="product-title">Mechanical Keyboard</h3>
                            <span class="product-price">$89.99</span>
                        </div>
                        <button class="btn-primary add-to-cart" data-action="add-to-cart">
                            Add to Cart
                        </button>
                    </article>
                    <article class="product-tile" data-product-name="Gaming Mouse"
                             data-sku="SKU-GM-003">
                        <figure class="product-image">
                            <img src="/images/mouse.webp" alt="Gaming Mouse">
                        </figure>
                        <div class="product-details">
                            <h3 class="product-title">Gaming Mouse</h3>
                            <span class="product-price">$49.99</span>
                        </div>
                        <button class="btn-primary add-to-cart" data-action="add-to-cart">
                            Add to Cart
                        </button>
                    </article>
                </div>
            </main>
            """);
    }
}
