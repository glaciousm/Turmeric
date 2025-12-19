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
 * Scenario 16: Form restructured
 * Tests healing when a form's structure is significantly reorganized.
 */
public class FormRestructuredScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "16";
    }

    @Override
    public String getName() {
        return "Form Restructured";
    }

    @Override
    public String getCategory() {
        return "Element Type Changes";
    }

    @Override
    public String getDescription() {
        return "The form was restructured into wizard steps with different wrapper elements. " +
               "The healer should find the input by its label association or placeholder.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("form#checkout > .form-group > input#card-number");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector(".wizard-step.payment input[name='card_number']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Checkout - Before", """
            <h1>Checkout</h1>
            <form id="checkout">
                <div class="form-group">
                    <label for="email">Email</label>
                    <input type="email" id="email" name="email">
                </div>
                <div class="form-group">
                    <label for="address">Shipping Address</label>
                    <input type="text" id="address" name="address">
                </div>
                <div class="form-group">
                    <label for="card-number">Card Number</label>
                    <input type="text" id="card-number" name="card_number"
                           placeholder="1234 5678 9012 3456">
                </div>
                <div class="form-group">
                    <label for="expiry">Expiry Date</label>
                    <input type="text" id="expiry" name="expiry" placeholder="MM/YY">
                </div>
                <button type="submit" class="btn-primary">Place Order</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Checkout - After", """
            <h1>Checkout</h1>
            <form id="checkout-wizard">
                <div class="wizard-steps">
                    <div class="wizard-step contact completed" data-step="1">
                        <h2>Contact Info</h2>
                        <div class="step-content">
                            <label for="email">Email</label>
                            <input type="email" id="email" name="email">
                        </div>
                    </div>
                    <div class="wizard-step shipping completed" data-step="2">
                        <h2>Shipping</h2>
                        <div class="step-content">
                            <label for="address">Shipping Address</label>
                            <input type="text" id="address" name="address">
                        </div>
                    </div>
                    <div class="wizard-step payment active" data-step="3">
                        <h2>Payment</h2>
                        <div class="step-content">
                            <div class="card-input-wrapper">
                                <label for="card-num">Card Number</label>
                                <input type="text" id="card-num" name="card_number"
                                       placeholder="1234 5678 9012 3456"
                                       aria-describedby="card-hint">
                                <span id="card-hint" class="hint">Enter 16-digit card number</span>
                            </div>
                            <div class="expiry-cvv-row">
                                <div class="field">
                                    <label for="exp">Expiry</label>
                                    <input type="text" id="exp" name="expiry" placeholder="MM/YY">
                                </div>
                                <div class="field">
                                    <label for="cvv">CVV</label>
                                    <input type="text" id="cvv" name="cvv" placeholder="123">
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="wizard-actions">
                    <button type="button" class="btn-secondary">Back</button>
                    <button type="submit" class="btn-primary">Place Order</button>
                </div>
            </form>
            """);
    }
}
