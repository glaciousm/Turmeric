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
 * Scenario 06: CSS selector broken (nested structure change)
 * Tests healing when DOM restructuring breaks CSS selectors.
 */
public class CssSelectorBrokenScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "06";
    }

    @Override
    public String getName() {
        return "CSS Selector Broken";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "The nested structure changed, breaking the CSS selector path. " +
               "Healer should find the element by its unique attributes.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("div.header > div.nav > ul.menu > li.active > a");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("nav.main-nav ul.menu li.current a");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Navigation - Before", """
            <div class="header">
                <div class="logo">MyApp</div>
                <div class="nav">
                    <ul class="menu">
                        <li><a href="/">Home</a></li>
                        <li class="active"><a href="/dashboard">Dashboard</a></li>
                        <li><a href="/settings">Settings</a></li>
                    </ul>
                </div>
            </div>
            <main>
                <h1>Dashboard</h1>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Navigation - After", """
            <header class="site-header">
                <div class="logo">MyApp</div>
                <nav class="main-nav">
                    <ul class="menu">
                        <li><a href="/">Home</a></li>
                        <li class="current"><a href="/dashboard">Dashboard</a></li>
                        <li><a href="/settings">Settings</a></li>
                    </ul>
                </nav>
            </header>
            <main>
                <h1>Dashboard</h1>
            </main>
            """);
    }
}
