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
 * Scenario 02: ID removed entirely
 * Tests healing when an element's ID is completely removed.
 */
public class IdRemovedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "02";
    }

    @Override
    public String getName() {
        return "ID Removed";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "Element ID 'submit-btn' was completely removed. " +
               "The button should be found by its text content or form context.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("submit-btn");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("button[type='submit']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Contact Form - Before", """
            <h1>Contact Us</h1>
            <form id="contact-form">
                <div class="form-group">
                    <label for="email">Email</label>
                    <input type="email" id="email" name="email" placeholder="your@email.com">
                </div>
                <div class="form-group">
                    <label for="message">Message</label>
                    <textarea id="message" name="message" rows="4"></textarea>
                </div>
                <button type="submit" id="submit-btn" class="btn-primary">Send Message</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Contact Form - After", """
            <h1>Contact Us</h1>
            <form id="contact-form">
                <div class="form-group">
                    <label for="email">Email</label>
                    <input type="email" id="email" name="email" placeholder="your@email.com">
                </div>
                <div class="form-group">
                    <label for="message">Message</label>
                    <textarea id="message" name="message" rows="4"></textarea>
                </div>
                <button type="submit" class="btn-primary">Send Message</button>
            </form>
            """);
    }
}
