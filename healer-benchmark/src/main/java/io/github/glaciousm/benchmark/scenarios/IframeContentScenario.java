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
 * Scenario 34: Iframe content
 * Tests healing of elements inside an iframe.
 */
public class IframeContentScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "34";
    }

    @Override
    public String getName() {
        return "Iframe Content";
    }

    @Override
    public String getCategory() {
        return "Complex DOM";
    }

    @Override
    public String getDescription() {
        return "Button is inside an iframe (embedded payment form). " +
               "Healer should switch to iframe context to find the element.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("pay-now-btn");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("button[type='submit'].payment-btn");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Checkout - Before", """
            <main>
                <h1>Checkout</h1>
                <div class="order-summary">
                    <h2>Order Summary</h2>
                    <p>Total: $99.99</p>
                </div>
                <div class="payment-section">
                    <h2>Payment</h2>
                    <iframe id="payment-frame" src="/payment-form" title="Payment Form">
                        <!-- Iframe content (simulated) -->
                        <html>
                        <body>
                            <form id="payment-form">
                                <div class="form-group">
                                    <label for="card">Card Number</label>
                                    <input type="text" id="card" placeholder="1234 5678 9012 3456">
                                </div>
                                <div class="form-group">
                                    <label for="expiry">Expiry</label>
                                    <input type="text" id="expiry" placeholder="MM/YY">
                                </div>
                                <button type="submit" id="pay-now-btn" class="btn-primary">
                                    Pay Now
                                </button>
                            </form>
                        </body>
                        </html>
                    </iframe>
                </div>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        // Simulates what the healer sees AFTER switching to iframe context
        // In real usage, HealingWebDriver switches to iframe and captures its DOM
        // For benchmark purposes, we flatten the iframe content into the main document
        return wrapHtml("Checkout - After (Iframe Content Captured)", """
            <main>
                <h1>Checkout</h1>
                <div class="order-summary">
                    <h2>Order Summary</h2>
                    <p>Total: $99.99</p>
                </div>
                <div class="payment-section">
                    <h2>Payment</h2>
                    <!-- Iframe content flattened for benchmark parsing -->
                    <div id="iframe-content" data-source="secure-payment">
                        <form class="payment-form" data-form="payment">
                            <div class="card-input">
                                <label>Card Number</label>
                                <input type="text" name="card_number" placeholder="Card number">
                            </div>
                            <div class="card-details">
                                <div class="expiry-input">
                                    <label>Expiry</label>
                                    <input type="text" name="expiry">
                                </div>
                                <div class="cvv-input">
                                    <label>CVV</label>
                                    <input type="text" name="cvv">
                                </div>
                            </div>
                            <button type="submit" class="payment-btn btn-success">
                                Complete Payment
                            </button>
                        </form>
                    </div>
                </div>
            </main>
            """);
    }
}
