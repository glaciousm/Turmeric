/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package com.intenthealer.benchmark.scenarios;

import com.intenthealer.benchmark.BenchmarkResult.ExpectedOutcome;
import com.intenthealer.core.model.ElementSnapshot;
import org.openqa.selenium.By;

/**
 * Scenario 22: Element completely removed
 * Tests that healing is refused when the target element no longer exists.
 */
public class ElementRemovedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "22";
    }

    @Override
    public String getName() {
        return "Element Removed";
    }

    @Override
    public String getCategory() {
        return "Negative Tests";
    }

    @Override
    public String getDescription() {
        return "The target element was completely removed from the page. " +
               "Healing should be refused as there is no valid replacement.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.REFUSE;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("promo-banner");
    }

    @Override
    public By getExpectedHealedLocator() {
        return null; // No healing expected
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Homepage - Before", """
            <header>
                <div class="logo">MyStore</div>
                <nav>
                    <a href="/">Home</a>
                    <a href="/products">Products</a>
                    <a href="/about">About</a>
                </nav>
            </header>
            <div id="promo-banner" class="banner">
                <h2>Limited Time Offer!</h2>
                <p>Get 50% off on all items.</p>
                <button class="btn-primary">Shop Now</button>
            </div>
            <main>
                <h1>Welcome to MyStore</h1>
                <p>Find the best products at great prices.</p>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Homepage - After (Promo Ended)", """
            <header>
                <div class="logo">MyStore</div>
                <nav>
                    <a href="/">Home</a>
                    <a href="/products">Products</a>
                    <a href="/about">About</a>
                </nav>
            </header>
            <main>
                <h1>Welcome to MyStore</h1>
                <p>Find the best products at great prices.</p>
            </main>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // The original element (promo-banner) was completely removed.
        // No element on the page should be considered a valid replacement.
        // The original was a promotional banner div - nav links are not valid replacements.
        return false;
    }
}
