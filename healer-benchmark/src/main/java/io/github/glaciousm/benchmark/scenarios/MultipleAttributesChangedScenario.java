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
 * Scenario 10: Multiple attributes changed simultaneously
 * Tests healing when several attributes change at once.
 */
public class MultipleAttributesChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "10";
    }

    @Override
    public String getName() {
        return "Multiple Attributes Changed";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "Multiple attributes changed: ID, class, and data-testid all modified. " +
               "The healer must rely on semantic understanding of the element's purpose.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("#search-btn.search-button[data-testid='search']");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("#submit-search.btn-search[data-testid='search-submit']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Search - Before", """
            <header>
                <div class="logo">Search Portal</div>
                <form class="search-form" action="/search">
                    <div class="search-wrapper">
                        <input type="text" id="search-input" name="q"
                               placeholder="Search..." aria-label="Search query">
                        <button type="submit" id="search-btn" class="search-button"
                                data-testid="search" aria-label="Submit search">
                            <span class="icon-search">🔍</span>
                            Search
                        </button>
                    </div>
                </form>
            </header>
            <main>
                <h1>Welcome to Search Portal</h1>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Search - After", """
            <header>
                <div class="logo">Search Portal</div>
                <form class="search-form" action="/search">
                    <div class="search-wrapper">
                        <input type="text" id="query-input" name="q"
                               placeholder="Search..." aria-label="Search query">
                        <button type="submit" id="submit-search" class="btn-search"
                                data-testid="search-submit" aria-label="Submit search">
                            <span class="icon-search">🔍</span>
                            Search
                        </button>
                    </div>
                </form>
            </header>
            <main>
                <h1>Welcome to Search Portal</h1>
            </main>
            """);
    }
}
