/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package com.intenthealer.benchmark.scenarios;

import com.intenthealer.benchmark.BenchmarkResult.ExpectedOutcome;
import com.intenthealer.core.model.ElementSnapshot;
import org.openqa.selenium.By;

/**
 * Scenario 23: Page navigated away (wrong page)
 * Tests that healing is refused when the user is on a completely different page.
 */
public class WrongPageScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "23";
    }

    @Override
    public String getName() {
        return "Wrong Page";
    }

    @Override
    public String getCategory() {
        return "Negative Tests";
    }

    @Override
    public String getDescription() {
        return "Test expects login page but landed on 404 error page. " +
               "Healing should be refused as the context is completely wrong.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.REFUSE;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("login-form");
    }

    @Override
    public By getExpectedHealedLocator() {
        return null; // No healing expected
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Login", """
            <main>
                <h1>Sign In</h1>
                <form id="login-form" action="/login" method="post">
                    <div class="form-group">
                        <label for="email">Email</label>
                        <input type="email" id="email" name="email" required>
                    </div>
                    <div class="form-group">
                        <label for="password">Password</label>
                        <input type="password" id="password" name="password" required>
                    </div>
                    <button type="submit" class="btn-primary">Sign In</button>
                </form>
                <p><a href="/forgot-password">Forgot Password?</a></p>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("404 - Page Not Found", """
            <main class="error-page">
                <h1>404</h1>
                <h2>Page Not Found</h2>
                <p>Sorry, the page you're looking for doesn't exist.</p>
                <a href="/" class="btn-primary">Go to Homepage</a>
            </main>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // The original element was a login-form but we're on a 404 page.
        // A link to homepage is NOT a valid replacement for a login form.
        // Only a form element would be a valid match.
        return "form".equalsIgnoreCase(element.getTagName());
    }
}
