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
 * Scenario 27: Element hidden (display:none)
 * Tests that healing is refused for hidden elements.
 */
public class HiddenElementScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "27";
    }

    @Override
    public String getName() {
        return "Hidden Element";
    }

    @Override
    public String getCategory() {
        return "Negative Tests";
    }

    @Override
    public String getDescription() {
        return "The target button exists but is hidden with display:none. " +
               "Healing should refuse to select hidden/non-interactable elements.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.REFUSE;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("submit-feedback");
    }

    @Override
    public By getExpectedHealedLocator() {
        return null; // Should not heal to hidden elements
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Feedback Form - Before", """
            <h1>Send Feedback</h1>
            <form id="feedback-form">
                <div class="form-group">
                    <label for="feedback-type">Type</label>
                    <select id="feedback-type" name="type">
                        <option value="">Select...</option>
                        <option value="bug">Bug Report</option>
                        <option value="feature">Feature Request</option>
                        <option value="general">General Feedback</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="feedback-text">Your Feedback</label>
                    <textarea id="feedback-text" name="feedback" rows="5"></textarea>
                </div>
                <button type="submit" id="submit-feedback" class="btn-primary">
                    Submit Feedback
                </button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Feedback Form - After (Button Hidden Until Form Valid)", """
            <h1>Send Feedback</h1>
            <form id="feedback-form">
                <div class="form-group">
                    <label for="feedback-type">Type</label>
                    <select id="feedback-type" name="type">
                        <option value="">Select...</option>
                        <option value="bug">Bug Report</option>
                        <option value="feature">Feature Request</option>
                        <option value="general">General Feedback</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="feedback-text">Your Feedback</label>
                    <textarea id="feedback-text" name="feedback" rows="5"></textarea>
                </div>
                <button type="submit" id="submit-btn" class="btn-primary"
                        style="display: none;" disabled aria-hidden="true">
                    Submit Feedback
                </button>
                <p class="form-hint">Please fill in all fields to enable submission.</p>
            </form>
            """);
    }
}
