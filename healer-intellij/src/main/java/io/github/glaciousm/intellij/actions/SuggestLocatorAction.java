package io.github.glaciousm.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * Action to suggest a more stable locator for selected text.
 */
public class SuggestLocatorAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (project == null || editor == null) {
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();

        if (selectedText == null || selectedText.isEmpty()) {
            Messages.showInfoMessage(
                    project,
                    "Please select a locator string to get stability suggestions.",
                    "No Selection"
            );
            return;
        }

        // Analyze the locator and suggest alternatives
        String suggestions = analyzeLocator(selectedText);

        Messages.showInfoMessage(project, suggestions, "Locator Suggestions");
    }

    private String analyzeLocator(String locator) {
        StringBuilder suggestions = new StringBuilder();
        suggestions.append("Analysis of: ").append(locator).append("\n\n");

        // Detect locator type
        if (locator.startsWith("//") || locator.startsWith("(//")) {
            suggestions.append("Type: XPath\n");
            suggestions.append("Stability: ");

            if (locator.contains("position()") || locator.contains("[1]") || locator.contains("[last()]")) {
                suggestions.append("LOW - Position-based locators are fragile\n");
            } else if (locator.contains("@id") || locator.contains("@data-testid")) {
                suggestions.append("HIGH - Using stable attributes\n");
            } else if (locator.contains("text()") || locator.contains("contains(")) {
                suggestions.append("MEDIUM - Text-based locators may change\n");
            } else {
                suggestions.append("MEDIUM\n");
            }

            suggestions.append("\nRecommendations:\n");
            suggestions.append("• Prefer @data-testid attributes when available\n");
            suggestions.append("• Avoid position-based selections like [1]\n");
            suggestions.append("• Use relative paths from stable parent elements\n");

        } else if (locator.startsWith("#")) {
            suggestions.append("Type: CSS ID Selector\n");
            suggestions.append("Stability: HIGH - ID selectors are generally stable\n");

        } else if (locator.startsWith(".")) {
            suggestions.append("Type: CSS Class Selector\n");
            suggestions.append("Stability: MEDIUM - Classes may change during styling updates\n");
            suggestions.append("\nRecommendations:\n");
            suggestions.append("• Consider using data-testid instead\n");

        } else if (locator.contains("[")) {
            suggestions.append("Type: CSS Attribute Selector\n");
            suggestions.append("Stability: ");

            if (locator.contains("data-testid") || locator.contains("data-test")) {
                suggestions.append("HIGH - Test attributes are designed to be stable\n");
            } else {
                suggestions.append("MEDIUM\n");
            }

        } else {
            suggestions.append("Type: CSS Tag or Other\n");
            suggestions.append("Stability: Variable\n");
        }

        suggestions.append("\n--- Intent Healer Tip ---\n");
        suggestions.append("Add @Intent annotations to describe the purpose\n");
        suggestions.append("of your step, so healer can find semantically\n");
        suggestions.append("equivalent elements when locators break.");

        return suggestions.toString();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        boolean hasSelection = editor != null &&
                editor.getSelectionModel().hasSelection();

        e.getPresentation().setEnabledAndVisible(project != null && hasSelection);
    }
}
