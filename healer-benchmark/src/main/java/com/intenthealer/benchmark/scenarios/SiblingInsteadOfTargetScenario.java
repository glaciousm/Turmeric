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
 * Scenario 29: Heals sibling instead of target
 * Tests detection of false heals when sibling elements are selected.
 */
public class SiblingInsteadOfTargetScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "29";
    }

    @Override
    public String getName() {
        return "Sibling Instead of Target";
    }

    @Override
    public String getCategory() {
        return "False Heal Detection";
    }

    @Override
    public String getDescription() {
        return "Target is 'Confirm' button but sibling 'Cancel' has similar structure. " +
               "Healer should correctly identify 'Confirm' not 'Cancel'.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        // The LLM correctly identifies "Confirm" vs "Cancel" by text
        // This scenario actually tests successful healing with sibling elements
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("confirm-action");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.xpath("//button[contains(@class, 'primary') and text()='Confirm']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Confirmation Dialog - Before", """
            <div class="modal" role="dialog">
                <div class="modal-header">
                    <h2>Confirm Deletion</h2>
                </div>
                <div class="modal-body">
                    <p>Are you sure you want to delete this item?</p>
                    <p>This action cannot be undone.</p>
                </div>
                <div class="modal-footer">
                    <button id="cancel-action" class="btn-secondary">Cancel</button>
                    <button id="confirm-action" class="btn-primary btn-danger">Confirm</button>
                </div>
            </div>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Confirmation Dialog - After (IDs Removed)", """
            <div class="modal" role="dialog">
                <div class="modal-header">
                    <h2>Confirm Deletion</h2>
                </div>
                <div class="modal-body">
                    <p>Are you sure you want to delete this item?</p>
                    <p>This action cannot be undone.</p>
                </div>
                <div class="modal-footer">
                    <button class="btn-secondary dialog-action" data-action="cancel">Cancel</button>
                    <button class="btn-primary btn-danger dialog-action" data-action="confirm">Confirm</button>
                </div>
            </div>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // Must be "Confirm" button, not "Cancel"
        String text = element.getNormalizedText();
        return "Confirm".equals(text);
    }
}
