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
 * Scenario 07: Name attribute changed
 * Tests healing when a form element's name attribute is modified.
 */
public class NameAttributeChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "07";
    }

    @Override
    public String getName() {
        return "Name Attribute Changed";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "Form input name changed from 'user_email' to 'email_address'. " +
               "The input should be found by its placeholder, label, or type.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.name("user_email");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.name("email_address");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Registration - Before", """
            <h1>Create Account</h1>
            <form id="register-form">
                <div class="form-group">
                    <label for="fullname">Full Name</label>
                    <input type="text" id="fullname" name="full_name" placeholder="John Doe">
                </div>
                <div class="form-group">
                    <label for="email">Email Address</label>
                    <input type="email" id="email" name="user_email" placeholder="john@example.com">
                </div>
                <div class="form-group">
                    <label for="password">Password</label>
                    <input type="password" id="password" name="password" placeholder="••••••••">
                </div>
                <button type="submit" class="btn-primary">Create Account</button>
            </form>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Registration - After", """
            <h1>Create Account</h1>
            <form id="register-form">
                <div class="form-group">
                    <label for="fullname">Full Name</label>
                    <input type="text" id="fullname" name="full_name" placeholder="John Doe">
                </div>
                <div class="form-group">
                    <label for="email">Email Address</label>
                    <input type="email" id="email" name="email_address" placeholder="john@example.com">
                </div>
                <div class="form-group">
                    <label for="password">Password</label>
                    <input type="password" id="password" name="password" placeholder="••••••••">
                </div>
                <button type="submit" class="btn-primary">Create Account</button>
            </form>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // The correct element is the email input with name="email_address"
        // It should have type="email" or the name attribute containing "email"
        String name = element.getName();
        String type = element.getType();
        return "email_address".equals(name) ||
               "email".equals(type) ||
               (name != null && name.toLowerCase().contains("email"));
    }
}
