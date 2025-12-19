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
 * Scenario 04: Class added (btn → btn btn-lg)
 * Tests healing when additional classes are added to an element.
 */
public class ClassAddedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "04";
    }

    @Override
    public String getName() {
        return "Class Added";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "Additional class added: 'btn' changed to 'btn btn-lg btn-rounded'. " +
               "The CSS selector should still match with partial class.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("button.btn:not(.btn-lg)");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("button.btn.btn-lg");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Subscribe - Before", """
            <h1>Newsletter</h1>
            <form id="newsletter-form">
                <div class="form-group">
                    <input type="email" id="email" placeholder="Enter your email">
                </div>
                <button type="submit" class="btn">Subscribe</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Subscribe - After", """
            <h1>Newsletter</h1>
            <form id="newsletter-form">
                <div class="form-group">
                    <input type="email" id="email" placeholder="Enter your email">
                </div>
                <button type="submit" class="btn btn-lg btn-rounded">Subscribe</button>
            </form>
            """);
    }
}
