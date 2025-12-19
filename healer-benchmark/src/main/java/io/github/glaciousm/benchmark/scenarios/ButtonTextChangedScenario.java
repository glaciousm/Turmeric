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
 * Scenario 17: Button text changed (Submit → Send)
 * Tests healing when a button's visible text is modified.
 */
public class ButtonTextChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "17";
    }

    @Override
    public String getName() {
        return "Button Text Changed";
    }

    @Override
    public String getCategory() {
        return "Text/Content Changes";
    }

    @Override
    public String getDescription() {
        return "Button text changed from 'Submit' to 'Send'. " +
               "The healer should find the button by its form context or type attribute.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.xpath("//button[text()='Submit']");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.xpath("//button[text()='Send']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Contact Form - Before", """
            <h1>Contact Us</h1>
            <form id="contact-form" action="/contact">
                <div class="form-group">
                    <label for="name">Your Name</label>
                    <input type="text" id="name" name="name" required>
                </div>
                <div class="form-group">
                    <label for="email">Your Email</label>
                    <input type="email" id="email" name="email" required>
                </div>
                <div class="form-group">
                    <label for="message">Message</label>
                    <textarea id="message" name="message" rows="5" required></textarea>
                </div>
                <button type="submit" class="btn-primary">Submit</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Contact Form - After", """
            <h1>Contact Us</h1>
            <form id="contact-form" action="/contact">
                <div class="form-group">
                    <label for="name">Your Name</label>
                    <input type="text" id="name" name="name" required>
                </div>
                <div class="form-group">
                    <label for="email">Your Email</label>
                    <input type="email" id="email" name="email" required>
                </div>
                <div class="form-group">
                    <label for="message">Message</label>
                    <textarea id="message" name="message" rows="5" required></textarea>
                </div>
                <button type="submit" class="btn-primary">Send</button>
            </form>
            """);
    }
}
