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
 * Scenario 11: Button → Link with same text
 * Tests healing when a button element is converted to a link.
 */
public class ButtonToLinkScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "11";
    }

    @Override
    public String getName() {
        return "Button to Link";
    }

    @Override
    public String getCategory() {
        return "Element Type Changes";
    }

    @Override
    public String getDescription() {
        return "A button element was converted to an anchor link with the same text. " +
               "The healer should find the element by its text content.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("button.learn-more");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.linkText("Learn More");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Feature Page - Before", """
            <section class="feature">
                <h2>Amazing Feature</h2>
                <p>Discover how our feature can help you achieve more.</p>
                <button class="learn-more btn-secondary">Learn More</button>
            </section>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Feature Page - After", """
            <section class="feature">
                <h2>Amazing Feature</h2>
                <p>Discover how our feature can help you achieve more.</p>
                <a href="/features/amazing" class="learn-more-link">Learn More</a>
            </section>
            """);
    }
}
