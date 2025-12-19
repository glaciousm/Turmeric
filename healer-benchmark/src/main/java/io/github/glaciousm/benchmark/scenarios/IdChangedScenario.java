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
 * Scenario 01: ID changed (login-btn → signin-button)
 * Tests healing when an element's ID attribute is renamed.
 */
public class IdChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "01";
    }

    @Override
    public String getName() {
        return "ID Changed";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "Element ID changed from 'login-btn' to 'signin-button'. " +
               "The button should be found by its text content or class.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("login-btn");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.id("signin-button");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Login - Before",
            loginFormHtml("login-btn", "btn-primary", "Login"));
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Login - After",
            loginFormHtml("signin-button", "btn-primary", "Login"));
    }
}
