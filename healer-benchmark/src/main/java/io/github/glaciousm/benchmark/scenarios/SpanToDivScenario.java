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
 * Scenario 14: Span → Div (same text content)
 * Tests healing when an inline element is changed to a block element.
 */
public class SpanToDivScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "14";
    }

    @Override
    public String getName() {
        return "Span to Div";
    }

    @Override
    public String getCategory() {
        return "Element Type Changes";
    }

    @Override
    public String getDescription() {
        return "A span element was changed to a div element. " +
               "The healer should find the element by its class and text content.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("span.status-badge.active");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("div.status-badge.active");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("User Profile - Before", """
            <div class="profile-header">
                <img src="avatar.jpg" alt="User Avatar" class="avatar">
                <div class="profile-info">
                    <h1>John Doe</h1>
                    <p>john.doe@example.com</p>
                    <span class="status-badge active">Online</span>
                </div>
            </div>
            <div class="profile-stats">
                <div class="stat">
                    <span class="stat-value">42</span>
                    <span class="stat-label">Projects</span>
                </div>
                <div class="stat">
                    <span class="stat-value">128</span>
                    <span class="stat-label">Followers</span>
                </div>
            </div>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("User Profile - After", """
            <div class="profile-header">
                <img src="avatar.jpg" alt="User Avatar" class="avatar">
                <div class="profile-info">
                    <h1>John Doe</h1>
                    <p>john.doe@example.com</p>
                    <div class="status-badge active">Online</div>
                </div>
            </div>
            <div class="profile-stats">
                <div class="stat">
                    <div class="stat-value">42</div>
                    <div class="stat-label">Projects</div>
                </div>
                <div class="stat">
                    <div class="stat-value">128</div>
                    <div class="stat-label">Followers</div>
                </div>
            </div>
            """);
    }
}
