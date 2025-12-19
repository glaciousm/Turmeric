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
 * Scenario 25: Delete button (destructive action)
 * Tests that healing is refused for potentially destructive actions.
 */
public class DestructiveActionScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "25";
    }

    @Override
    public String getName() {
        return "Destructive Action";
    }

    @Override
    public String getCategory() {
        return "Negative Tests";
    }

    @Override
    public String getDescription() {
        return "Attempting to heal a 'Delete Account' button. " +
               "Healing should be refused due to destructive action guardrails.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.REFUSE;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("delete-account-btn");
    }

    @Override
    public By getExpectedHealedLocator() {
        return null; // Should not heal destructive actions
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Account Settings - Before", """
            <h1>Account Settings</h1>
            <section class="settings-section">
                <h2>Profile</h2>
                <p>Update your profile information</p>
                <button class="btn-primary">Edit Profile</button>
            </section>
            <section class="settings-section danger-zone">
                <h2>Danger Zone</h2>
                <p>These actions are permanent and cannot be undone.</p>
                <button id="delete-account-btn" class="btn-danger">Delete Account</button>
            </section>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Account Settings - After", """
            <h1>Account Settings</h1>
            <section class="settings-section">
                <h2>Profile</h2>
                <p>Update your profile information</p>
                <button class="btn-primary">Edit Profile</button>
            </section>
            <section class="settings-section danger-zone">
                <h2>Danger Zone</h2>
                <p>These actions are permanent and cannot be undone.</p>
                <button class="btn-danger delete-action"
                        data-action="delete-account"
                        aria-describedby="delete-warning">
                    Permanently Delete Account
                </button>
                <p id="delete-warning" class="warning">
                    This will delete all your data immediately.
                </p>
            </section>
            """);
    }
}
