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
 * Scenario 28: Heals wrong button (similar text)
 * Tests detection of false heals when similar buttons exist.
 */
public class SimilarTextWrongButtonScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "28";
    }

    @Override
    public String getName() {
        return "Similar Text Wrong Button";
    }

    @Override
    public String getCategory() {
        return "False Heal Detection";
    }

    @Override
    public String getDescription() {
        return "Two buttons with similar text: 'Save' and 'Save Draft'. " +
               "Healer should detect ambiguity or refuse to heal incorrectly.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        // The LLM correctly distinguishes "Save" from "Save Draft"
        // This scenario actually tests successful healing with similar-text elements
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.id("save-btn");
    }

    @Override
    public By getExpectedHealedLocator() {
        // The correct element has text "Save", not "Save Draft"
        return By.xpath("//button[text()='Save']");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Document Editor - Before", """
            <main class="editor">
                <h1>Edit Document</h1>
                <textarea id="content" rows="10">Document content here...</textarea>
                <div class="editor-actions">
                    <button id="save-btn" class="btn-primary">Save</button>
                    <button id="save-draft-btn" class="btn-secondary">Save Draft</button>
                    <button id="preview-btn" class="btn-secondary">Preview</button>
                </div>
            </main>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Document Editor - After (IDs Changed)", """
            <main class="editor">
                <h1>Edit Document</h1>
                <textarea id="content" rows="10">Document content here...</textarea>
                <div class="editor-actions">
                    <button class="btn-primary action-save">Save</button>
                    <button class="btn-secondary action-draft">Save Draft</button>
                    <button class="btn-secondary action-preview">Preview</button>
                </div>
            </main>
            """);
    }

    @Override
    protected boolean matchesExpectedElement(ElementSnapshot element) {
        // Must be exactly "Save", not "Save Draft" or any other variation
        String text = element.getNormalizedText();
        return "Save".equals(text);
    }
}
