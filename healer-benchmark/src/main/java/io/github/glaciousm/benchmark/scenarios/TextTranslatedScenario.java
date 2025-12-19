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
 * Scenario 20: Text translated (English → Spanish)
 * Tests healing when button text is translated to a different language.
 */
public class TextTranslatedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "20";
    }

    @Override
    public String getName() {
        return "Text Translated";
    }

    @Override
    public String getCategory() {
        return "Text/Content Changes";
    }

    @Override
    public String getDescription() {
        return "Button text translated from 'Add to Cart' to 'Añadir al Carrito'. " +
               "The healer should find the button by its position, class, or data attributes.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.xpath("//button[contains(text(), 'Add to Cart')]");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.xpath("//button[contains(text(), 'Añadir al Carrito')]");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Product - English", """
            <div class="product-page" lang="en">
                <nav class="breadcrumb">
                    <a href="/">Home</a> > <a href="/products">Products</a> > Laptop
                </nav>
                <div class="product-detail">
                    <img src="laptop.jpg" alt="Gaming Laptop">
                    <div class="product-info">
                        <h1>Gaming Laptop Pro</h1>
                        <p class="price">$1,299.99</p>
                        <div class="actions">
                            <button class="btn-primary add-to-cart"
                                    data-product-id="123"
                                    data-action="add">
                                Add to Cart
                            </button>
                            <button class="btn-secondary wishlist">
                                Add to Wishlist
                            </button>
                        </div>
                    </div>
                </div>
            </div>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Producto - Español", """
            <div class="product-page" lang="es">
                <nav class="breadcrumb">
                    <a href="/">Inicio</a> > <a href="/productos">Productos</a> > Portátil
                </nav>
                <div class="product-detail">
                    <img src="laptop.jpg" alt="Portátil Gaming">
                    <div class="product-info">
                        <h1>Portátil Gaming Pro</h1>
                        <p class="price">$1,299.99</p>
                        <div class="actions">
                            <button class="btn-primary add-to-cart"
                                    data-product-id="123"
                                    data-action="add">
                                Añadir al Carrito
                            </button>
                            <button class="btn-secondary wishlist">
                                Añadir a Lista de Deseos
                            </button>
                        </div>
                    </div>
                </div>
            </div>
            """);
    }
}
