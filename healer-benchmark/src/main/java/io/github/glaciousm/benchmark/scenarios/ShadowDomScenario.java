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
 * Scenario 33: Shadow DOM element
 * Tests healing of elements inside a Shadow DOM.
 */
public class ShadowDomScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "33";
    }

    @Override
    public String getName() {
        return "Shadow DOM Element";
    }

    @Override
    public String getCategory() {
        return "Complex DOM";
    }

    @Override
    public String getDescription() {
        return "Button is inside a custom web component's Shadow DOM. " +
               "Healer should be able to pierce the shadow boundary.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        // Original locator tried to access element directly (won't work with shadow DOM)
        return By.cssSelector("custom-button button#action-btn");
    }

    @Override
    public By getExpectedHealedLocator() {
        // Healer should find via shadow DOM piercing
        return By.cssSelector("custom-button");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Custom Components - Before", """
            <main>
                <h1>Dashboard</h1>
                <p>Welcome to your dashboard</p>
                <custom-button>
                    <!-- Shadow DOM content (simulated in HTML) -->
                    <template shadowroot="open">
                        <style>
                            button { padding: 10px 20px; background: #007bff; color: white; border: none; }
                        </style>
                        <button id="action-btn" class="primary-action">
                            <slot>Click Me</slot>
                        </button>
                    </template>
                    View Details
                </custom-button>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Custom Components - After (Structure Changed)", """
            <main>
                <h1>Dashboard</h1>
                <p>Welcome to your dashboard</p>
                <custom-button variant="primary">
                    <!-- Shadow DOM content (simulated in HTML) -->
                    <template shadowroot="open">
                        <style>
                            .btn { padding: 12px 24px; background: #0066cc; color: white; border: none; border-radius: 4px; }
                        </style>
                        <button class="btn primary-btn">
                            <span class="btn-icon">→</span>
                            <span class="btn-text"><slot>Click Me</slot></span>
                        </button>
                    </template>
                    View Details
                </custom-button>
            </main>
            """);
    }
}
