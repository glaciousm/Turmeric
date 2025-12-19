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
 * Scenario 19: Label text changed
 * Tests healing when a form label's text is modified.
 */
public class LabelTextChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "19";
    }

    @Override
    public String getName() {
        return "Label Text Changed";
    }

    @Override
    public String getCategory() {
        return "Text/Content Changes";
    }

    @Override
    public String getDescription() {
        return "Label text changed from 'Phone Number' to 'Mobile Number'. " +
               "The healer should find the associated input by its type or name.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.xpath("//label[text()='Phone Number']/following-sibling::input");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.xpath("//label[text()='Mobile Number']/following-sibling::input");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Profile Edit - Before", """
            <h1>Edit Profile</h1>
            <form id="profile-form">
                <div class="form-group">
                    <label for="name">Full Name</label>
                    <input type="text" id="name" name="name" value="John Doe">
                </div>
                <div class="form-group">
                    <label>Phone Number</label>
                    <input type="tel" name="phone" value="+1 555-123-4567">
                </div>
                <div class="form-group">
                    <label for="bio">Bio</label>
                    <textarea id="bio" name="bio">Software developer</textarea>
                </div>
                <button type="submit" class="btn-primary">Save Changes</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Profile Edit - After", """
            <h1>Edit Profile</h1>
            <form id="profile-form">
                <div class="form-group">
                    <label for="name">Full Name</label>
                    <input type="text" id="name" name="name" value="John Doe">
                </div>
                <div class="form-group">
                    <label>Mobile Number</label>
                    <input type="tel" name="phone" value="+1 555-123-4567">
                </div>
                <div class="form-group">
                    <label for="bio">Bio</label>
                    <textarea id="bio" name="bio">Software developer</textarea>
                </div>
                <button type="submit" class="btn-primary">Save Changes</button>
            </form>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // The correct element is the phone input with type="tel" and name="phone"
        String type = element.getType();
        String name = element.getName();
        return "tel".equals(type) ||
               "phone".equals(name) ||
               (name != null && name.toLowerCase().contains("phone"));
    }
}
