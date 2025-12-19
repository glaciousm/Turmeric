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
 * Scenario 31: Heals by wrong criteria
 * Tests detection when healer uses wrong matching criteria.
 */
public class WrongCriteriaHealScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "31";
    }

    @Override
    public String getName() {
        return "Wrong Criteria Heal";
    }

    @Override
    public String getCategory() {
        return "False Heal Detection";
    }

    @Override
    public String getDescription() {
        return "Looking for 'Edit' button for user 'Alice', but matching by class might " +
               "return any edit button. Healer should use context (row, user name) correctly.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.DETECT_FALSE_HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("tr[data-user='alice'] button.edit-user");
    }

    @Override
    public By getExpectedHealedLocator() {
        // Should match Edit button in Alice's row specifically
        return By.xpath("//tr[contains(., 'Alice')]//button[text()='Edit']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("User List - Before", """
            <table class="user-table">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    <tr data-user="alice">
                        <td>Alice Smith</td>
                        <td>alice@example.com</td>
                        <td>
                            <button class="edit-user">Edit</button>
                            <button class="delete-user">Delete</button>
                        </td>
                    </tr>
                    <tr data-user="bob">
                        <td>Bob Jones</td>
                        <td>bob@example.com</td>
                        <td>
                            <button class="edit-user">Edit</button>
                            <button class="delete-user">Delete</button>
                        </td>
                    </tr>
                    <tr data-user="charlie">
                        <td>Charlie Brown</td>
                        <td>charlie@example.com</td>
                        <td>
                            <button class="edit-user">Edit</button>
                            <button class="delete-user">Delete</button>
                        </td>
                    </tr>
                </tbody>
            </table>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("User List - After (Data Attributes Removed)", """
            <table class="user-table">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    <tr class="user-row">
                        <td class="user-name">Alice Smith</td>
                        <td class="user-email">alice@example.com</td>
                        <td class="user-actions">
                            <button class="btn-edit">Edit</button>
                            <button class="btn-delete">Delete</button>
                        </td>
                    </tr>
                    <tr class="user-row">
                        <td class="user-name">Bob Jones</td>
                        <td class="user-email">bob@example.com</td>
                        <td class="user-actions">
                            <button class="btn-edit">Edit</button>
                            <button class="btn-delete">Delete</button>
                        </td>
                    </tr>
                    <tr class="user-row">
                        <td class="user-name">Charlie Brown</td>
                        <td class="user-email">charlie@example.com</td>
                        <td class="user-actions">
                            <button class="btn-edit">Edit</button>
                            <button class="btn-delete">Delete</button>
                        </td>
                    </tr>
                </tbody>
            </table>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // Must be the Edit button in Alice's row, not Bob's or Charlie's
        // Check if nearby labels contain "Alice"
        if (element.getNearbyLabels() != null) {
            for (String label : element.getNearbyLabels()) {
                if (label.contains("Alice")) {
                    return true;
                }
            }
        }
        // Also check if the text itself contains reference to Alice
        // (fallback for simpler HTML structures)
        return false;
    }
}
