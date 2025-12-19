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
 * Scenario 13: Select → Custom dropdown div
 * Tests healing when a native select is replaced with a custom dropdown component.
 */
public class SelectToCustomDropdownScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "13";
    }

    @Override
    public String getName() {
        return "Select to Custom Dropdown";
    }

    @Override
    public String getCategory() {
        return "Element Type Changes";
    }

    @Override
    public String getDescription() {
        return "A native select element was replaced with a custom div-based dropdown. " +
               "The healer should find the trigger element by its role or label association.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("select#country");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("[role='combobox'][aria-label='Country']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Address Form - Before", """
            <h1>Shipping Address</h1>
            <form id="address-form">
                <div class="form-group">
                    <label for="street">Street Address</label>
                    <input type="text" id="street" name="street">
                </div>
                <div class="form-group">
                    <label for="city">City</label>
                    <input type="text" id="city" name="city">
                </div>
                <div class="form-group">
                    <label for="country">Country</label>
                    <select id="country" name="country">
                        <option value="">Select country...</option>
                        <option value="us">United States</option>
                        <option value="uk">United Kingdom</option>
                        <option value="ca">Canada</option>
                    </select>
                </div>
                <button type="submit" class="btn-primary">Save Address</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Address Form - After", """
            <h1>Shipping Address</h1>
            <form id="address-form">
                <div class="form-group">
                    <label for="street">Street Address</label>
                    <input type="text" id="street" name="street">
                </div>
                <div class="form-group">
                    <label for="city">City</label>
                    <input type="text" id="city" name="city">
                </div>
                <div class="form-group">
                    <label id="country-label">Country</label>
                    <div class="custom-dropdown" role="combobox"
                         aria-label="Country" aria-expanded="false"
                         aria-haspopup="listbox" tabindex="0">
                        <span class="selected-value">Select country...</span>
                        <span class="dropdown-arrow">▼</span>
                    </div>
                    <input type="hidden" name="country" value="">
                    <ul class="dropdown-options hidden" role="listbox">
                        <li role="option" data-value="us">United States</li>
                        <li role="option" data-value="uk">United Kingdom</li>
                        <li role="option" data-value="ca">Canada</li>
                    </ul>
                </div>
                <button type="submit" class="btn-primary">Save Address</button>
            </form>
            """);
    }
}
