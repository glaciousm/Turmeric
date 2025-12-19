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
 * Scenario 26: Admin-only element (forbidden URL)
 * Tests that healing is refused when on a forbidden URL pattern.
 */
public class ForbiddenUrlScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "26";
    }

    @Override
    public String getName() {
        return "Forbidden URL";
    }

    @Override
    public String getCategory() {
        return "Negative Tests";
    }

    @Override
    public String getDescription() {
        return "Attempting to heal on an admin URL which is in the forbidden list. " +
               "Healing should be refused due to URL guardrails.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.REFUSE;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("user-role-select");
    }

    @Override
    public By getExpectedHealedLocator() {
        return null; // Should not heal on forbidden URLs
    }

    @Override
    public String getPageUrl() {
        // This URL matches the forbidden pattern ".*\/admin\/.*"
        return "https://example.com/admin/users";
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Admin - User Management", """
            <nav class="admin-nav">
                <a href="/admin">Dashboard</a>
                <a href="/admin/users" class="active">Users</a>
                <a href="/admin/settings">Settings</a>
            </nav>
            <main class="admin-content">
                <h1>User Management</h1>
                <form id="edit-user-form">
                    <div class="form-group">
                        <label for="username">Username</label>
                        <input type="text" id="username" value="johndoe">
                    </div>
                    <div class="form-group">
                        <label for="user-role-select">Role</label>
                        <select id="user-role-select" name="role">
                            <option value="user">User</option>
                            <option value="admin">Admin</option>
                            <option value="moderator">Moderator</option>
                        </select>
                    </div>
                    <button type="submit" class="btn-primary">Save Changes</button>
                </form>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Admin - User Management", """
            <nav class="admin-nav">
                <a href="/admin">Dashboard</a>
                <a href="/admin/users" class="active">Users</a>
                <a href="/admin/settings">Settings</a>
            </nav>
            <main class="admin-content">
                <h1>User Management</h1>
                <form id="edit-user-form">
                    <div class="form-group">
                        <label for="username">Username</label>
                        <input type="text" id="username" value="johndoe">
                    </div>
                    <div class="form-group">
                        <label for="role-dropdown">Role</label>
                        <div class="custom-select" id="role-dropdown" role="combobox">
                            <span class="selected">User</span>
                        </div>
                    </div>
                    <button type="submit" class="btn-primary">Save Changes</button>
                </form>
            </main>
            """);
    }
}
