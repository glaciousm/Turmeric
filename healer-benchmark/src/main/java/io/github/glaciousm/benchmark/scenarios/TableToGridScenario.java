/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark.scenarios;

import io.github.glaciousm.benchmark.BenchmarkResult.ExpectedOutcome;
import io.github.glaciousm.core.model.ElementSnapshot;
import org.openqa.selenium.By;

/**
 * Scenario 15: Table → Grid layout
 * Tests healing when a table layout is replaced with a CSS grid.
 */
public class TableToGridScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "15";
    }

    @Override
    public String getName() {
        return "Table to Grid";
    }

    @Override
    public String getCategory() {
        return "Element Type Changes";
    }

    @Override
    public String getDescription() {
        return "A data table was converted to a CSS grid layout. " +
               "The healer should find the action button by its data attribute or text.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("table#users-table tbody tr:first-child td:last-child button.edit");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector(".user-row[data-user-id='1'] .actions button.edit");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Users - Before", """
            <h1>User Management</h1>
            <table id="users-table">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Role</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    <tr data-user-id="1">
                        <td>Alice Smith</td>
                        <td>alice@example.com</td>
                        <td>Admin</td>
                        <td>
                            <button class="edit">Edit</button>
                            <button class="delete">Delete</button>
                        </td>
                    </tr>
                    <tr data-user-id="2">
                        <td>Bob Jones</td>
                        <td>bob@example.com</td>
                        <td>User</td>
                        <td>
                            <button class="edit">Edit</button>
                            <button class="delete">Delete</button>
                        </td>
                    </tr>
                </tbody>
            </table>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Users - After", """
            <h1>User Management</h1>
            <div class="users-grid" role="grid" aria-label="Users list">
                <div class="grid-header" role="row">
                    <div role="columnheader">Name</div>
                    <div role="columnheader">Email</div>
                    <div role="columnheader">Role</div>
                    <div role="columnheader">Actions</div>
                </div>
                <div class="user-row" role="row" data-user-id="1">
                    <div class="cell name">Alice Smith</div>
                    <div class="cell email">alice@example.com</div>
                    <div class="cell role">Admin</div>
                    <div class="cell actions">
                        <button class="edit" aria-label="Edit Alice Smith">Edit</button>
                        <button class="delete" aria-label="Delete Alice Smith">Delete</button>
                    </div>
                </div>
                <div class="user-row" role="row" data-user-id="2">
                    <div class="cell name">Bob Jones</div>
                    <div class="cell email">bob@example.com</div>
                    <div class="cell role">User</div>
                    <div class="cell actions">
                        <button class="edit" aria-label="Edit Bob Jones">Edit</button>
                        <button class="delete" aria-label="Delete Bob Jones">Delete</button>
                    </div>
                </div>
            </div>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // Must be the Edit button, not Delete
        // The original locator targeted button.edit in the first row
        String text = element.getNormalizedText();
        return "Edit".equals(text);
    }
}
