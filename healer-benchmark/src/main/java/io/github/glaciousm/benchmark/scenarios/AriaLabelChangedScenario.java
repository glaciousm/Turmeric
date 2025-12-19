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
 * Scenario 09: aria-label changed
 * Tests healing when an element's aria-label is modified.
 */
public class AriaLabelChangedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "09";
    }

    @Override
    public String getName() {
        return "aria-label Changed";
    }

    @Override
    public String getCategory() {
        return "Locator Changes";
    }

    @Override
    public String getDescription() {
        return "aria-label changed from 'Close dialog' to 'Close modal'. " +
               "The button should be found by its icon class or position.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.cssSelector("button[aria-label='Close dialog']");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.cssSelector("button[aria-label='Close modal']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Modal - Before", """
            <div class="modal-backdrop">
                <div class="modal" role="dialog" aria-modal="true">
                    <div class="modal-header">
                        <h2>Confirm Action</h2>
                        <button class="close-btn" aria-label="Close dialog">×</button>
                    </div>
                    <div class="modal-body">
                        <p>Are you sure you want to proceed?</p>
                    </div>
                    <div class="modal-footer">
                        <button class="btn-secondary">Cancel</button>
                        <button class="btn-primary">Confirm</button>
                    </div>
                </div>
            </div>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Modal - After", """
            <div class="modal-backdrop">
                <div class="modal" role="dialog" aria-modal="true">
                    <div class="modal-header">
                        <h2>Confirm Action</h2>
                        <button class="close-btn" aria-label="Close modal">×</button>
                    </div>
                    <div class="modal-body">
                        <p>Are you sure you want to proceed?</p>
                    </div>
                    <div class="modal-footer">
                        <button class="btn-secondary">Cancel</button>
                        <button class="btn-primary">Confirm</button>
                    </div>
                </div>
            </div>
            """);
    }
}
