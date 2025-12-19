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
 * Scenario 18: Placeholder text changed
 * Tests healing when an input's placeholder text is modified.
 */
public class PlaceholderChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "18";
    }

    @Override
    public String getName() {
        return "Placeholder Changed";
    }

    @Override
    public String getCategory() {
        return "Text/Content Changes";
    }

    @Override
    public String getDescription() {
        return "Input placeholder changed from 'Enter your email' to 'Email address'. " +
               "The healer should find the input by its type, label, or name.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("input[placeholder='Enter your email']");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("input[placeholder='Email address']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Newsletter - Before", """
            <section class="newsletter-section">
                <h2>Subscribe to Our Newsletter</h2>
                <p>Get the latest updates delivered to your inbox.</p>
                <form class="newsletter-form">
                    <input type="email" name="email"
                           placeholder="Enter your email"
                           aria-label="Email for newsletter">
                    <button type="submit" class="btn-primary">Subscribe</button>
                </form>
            </section>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Newsletter - After", """
            <section class="newsletter-section">
                <h2>Subscribe to Our Newsletter</h2>
                <p>Get the latest updates delivered to your inbox.</p>
                <form class="newsletter-form">
                    <input type="email" name="email"
                           placeholder="Email address"
                           aria-label="Email for newsletter">
                    <button type="submit" class="btn-primary">Subscribe</button>
                </form>
            </section>
            """);
    }
}
